package com.brewingbytes.gemmory.storage

import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.CanvasRecordCodec
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridBinding
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.save.PlayFileCodec
import com.brewingbytes.gemmory.save.PlayTimes
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.SaveCodec
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.save.ViewStateCodec
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.brewingbytes.gemmory.telemetry.reportLabel

/** Which file of a canvas a [LoadProblem] is about. */
enum class CanvasFile { DIR, GRID, SAVE, LAST_GOOD }

sealed interface LoadProblem {
    val file: CanvasFile

    data class Missing(override val file: CanvasFile) : LoadProblem

    data class Unreadable(override val file: CanvasFile, val error: FileStoreException) : LoadProblem

    data class Refused(override val file: CanvasFile, val refusal: Refusal) : LoadProblem
}

sealed interface LoadResult {
    /** [log] is the ordered list of placed cell indices; empty for a canvas that was never saved. */
    class Loaded(val grid: GridFile, val log: IntArray) : LoadResult

    /** The progress file was refused or missing; [log] comes from the last good file, a few seconds older. */
    class RecoveredFromLastGood(val grid: GridFile, val log: IntArray, val problem: LoadProblem) : LoadResult

    /** Nothing usable: the gallery shows "Can't open" (design D9). Files are left as they are. */
    data class Damaged(val problem: LoadProblem) : LoadResult
}

class CanvasStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Canvas records in app-private storage, one directory each: `files/canvases/<photo|bundled|remote>/<id>/` holding
 * `grid` (GridCodec), `save` (SaveCodec), `save.last-good`, `view` (ViewStateCodec), `record` (CanvasRecordCodec:
 * title, creation time, play time), `play` (PlayFileCodec, T35: last gem and first finished day), `thumb.png` (the last gallery thumbnail, kept for the "Can't open" tile) and,
 * for photo canvases, `source.jpg` (the cropped photo, at most 1024 px). Photo canvases live under the directory the
 * backup rules exclude, so none of it leaves the phone. Every write is atomic ([FileStore.writeAtomic]: temp + fsync +
 * rename). Common since iOS Stage 1c (T64): the same paths and steps over [FileStore] as Android's `File` code was. Before `save` is replaced, a copy of it that still decodes becomes `save.last-good`,
 * so a refused `save` falls back to progress at most one save interval older. It's a copy, not a hard link: SELinux
 * denies `link` on app data files (CanvasRepositoryDeviceTest).
 *
 * Blocking IO: call from a background dispatcher, with one writer per canvas at a time.
 */
class CanvasRepository(
    private val files: FileStore,
    private val reporter: NonFatalReporter = NonFatalReporter.Logcat,
    private val codec: SaveCodec = SaveCodec(),
) {
    /** [ref]'s folder, relative to the files folder: `canvases/<kind>/<id>`. */
    fun dirOf(ref: CanvasRef): String = "${ref.kind.dir}/${ref.id}"

    /** Whether [ref]'s canvas is on the phone (its folder exists). */
    fun exists(ref: CanvasRef): Boolean = files.isDirectory(dirOf(ref))

    /** Ids of the canvases of [kind], sorted. Half-created or half-deleted canvases are not listed. */
    fun list(kind: CanvasKind): List<String> =
        files.list(kind.dir).filter { CanvasRef.ID.matches(it) && files.isDirectory("${kind.dir}/$it") }

    /**
     * Starts a canvas from an encoded grid: a photo grid just made by the converter, or a bundled picture's frozen
     * grid asset copied as is (never re-converted). [record] and, for a photo canvas, its cropped [source] photo are
     * written in the same step. The canvas appears only once all of its files are complete.
     */
    fun create(ref: CanvasRef, gridBytes: ByteArray, record: CanvasRecord? = null, source: ByteArray? = null): WriteResult {
        require(GridCodec.decode(gridBytes) is Decoded.Ok) { "not a valid grid file" }
        require(source == null || ref.kind == CanvasKind.PHOTO) { "only photo canvases keep a source photo" }
        val dir = dirOf(ref)
        require(!files.exists(dir)) { "canvas ${ref.id} already exists" }
        val staging = "${ref.kind.dir}/.${ref.id}.new"
        return try {
            files.deleteRecursive(staging)
            if (!files.mkdirs(staging)) throw FileStoreException("can't create .${ref.id}.new", diskFull = false)
            files.writeAtomic("$staging/$GRID", gridBytes)
            if (record != null) files.writeAtomic("$staging/$RECORD", CanvasRecordCodec.encode(record))
            if (source != null) files.writeAtomic("$staging/$SOURCE", source)
            files.moveDir(staging, dir)
            WriteResult.Written
        } catch (e: FileStoreException) {
            files.deleteRecursive(staging)
            writeFailed(ref, "create", e)
        }
    }

    fun load(ref: CanvasRef): LoadResult {
        val dir = dirOf(ref)
        if (!files.isDirectory(dir)) return damaged(ref, LoadProblem.Missing(CanvasFile.DIR))
        val grid = when (val g = read("$dir/$GRID", CanvasFile.GRID) { GridCodec.decode(it) }) {
            is Attempt.Ok -> g.value
            is Attempt.Failed -> return damaged(ref, g.problem)
        }
        val binding = grid.binding
        val primary = read("$dir/$SAVE", CanvasFile.SAVE) { codec.decode(it, binding) }
        if (primary is Attempt.Ok) return LoadResult.Loaded(grid, primary.value)
        primary as Attempt.Failed
        val problem = primary.problem
        val lastGood = read("$dir/$LAST_GOOD", CanvasFile.LAST_GOOD) { codec.decode(it, binding) }
        // Never saved yet: a fresh canvas.
        if (problem is LoadProblem.Missing && lastGood is Attempt.Failed && lastGood.problem is LoadProblem.Missing) {
            return LoadResult.Loaded(grid, IntArray(0))
        }
        // Newer progress than this app can read: falling back would let the next write overwrite it.
        val newer = problem is LoadProblem.Refused && problem.refusal is Refusal.NewerVersion
        if (!newer && lastGood is Attempt.Ok) {
            reporter.report(CanvasStorageException("${ref.reportLabel}: recovered from last good save, $problem"))
            return LoadResult.RecoveredFromLastGood(grid, lastGood.value, problem)
        }
        return damaged(ref, problem)
    }

    /** Replaces the canvas progress with [log], keeping the current file as the last good one if it decodes. */
    fun writeSave(ref: CanvasRef, grid: GridBinding, log: IntArray): WriteResult {
        val dir = dirOf(ref)
        // Deleted while a write was pending: don't bring it back.
        if (!files.isDirectory(dir)) return WriteResult.WriteFailed(diskFull = false)
        val bytes = try {
            codec.encode(grid, log)
        } catch (e: IllegalArgumentException) {
            reporter.report(CanvasStorageException("${ref.reportLabel}: refused to encode save", e))
            return WriteResult.WriteFailed(diskFull = false)
        }
        val save = "$dir/$SAVE"
        return try {
            // An unreadable save is treated like a refused one: no last-good copy, and the rename still replaces it
            // (that needs the directory's permissions, not the file's).
            val currentBytes = try {
                files.read(save)
            } catch (_: FileStoreException) {
                null
            }
            val current = currentBytes?.let { codec.decode(it, grid) }
            if (current is Decoded.Ok) {
                files.writeAtomic("$dir/$LAST_GOOD", currentBytes)
            } else if ((current as? Decoded.Refused)?.reason is Refusal.NewerVersion) {
                reporter.report(CanvasStorageException("${ref.reportLabel}: won't overwrite a newer save"))
                return WriteResult.WriteFailed(diskFull = false)
            }
            files.writeAtomic(save, bytes)
            WriteResult.Written
        } catch (e: FileStoreException) {
            writeFailed(ref, "save", e)
        }
    }

    /** The saved view, or null (use defaults) if it's missing, unreadable or out of range for [paletteSize]. */
    fun readView(ref: CanvasRef, paletteSize: Int): ViewState? {
        val bytes = try {
            files.read("${dirOf(ref)}/$VIEW")
        } catch (_: FileStoreException) {
            null
        }
        return ViewStateCodec.decode(bytes, paletteSize)
    }

    /** Written on Activity onStop only (no per-pan writes). Not reported: losing the view costs one pinch. */
    fun writeView(ref: CanvasRef, state: ViewState): WriteResult {
        val dir = dirOf(ref)
        if (!files.isDirectory(dir)) return WriteResult.WriteFailed(diskFull = false)
        return try {
            files.writeAtomic("$dir/$VIEW", ViewStateCodec.encode(state))
            WriteResult.Written
        } catch (e: FileStoreException) {
            WriteResult.WriteFailed(e.diskFull)
        }
    }

    /** The record, or null if it's missing or refused (the caller falls back to a default title). */
    fun readRecord(ref: CanvasRef): CanvasRecord? {
        val bytes = try {
            files.read("${dirOf(ref)}/$RECORD") ?: return null
        } catch (_: FileStoreException) {
            return null
        }
        return (CanvasRecordCodec.decode(bytes) as? Decoded.Ok)?.value
    }

    /** Rename and play time. A failure is not reported: the old title or a few minutes of play time are kept. */
    fun writeRecord(ref: CanvasRef, record: CanvasRecord): WriteResult {
        val dir = dirOf(ref)
        if (!files.isDirectory(dir)) return WriteResult.WriteFailed(diskFull = false)
        return try {
            files.writeAtomic("$dir/$RECORD", CanvasRecordCodec.encode(record))
            WriteResult.Written
        } catch (e: FileStoreException) {
            WriteResult.WriteFailed(e.diskFull)
        }
    }

    /** When the canvas was last played and first finished (T35), or null if unknown: missing, unreadable or refused. */
    fun readPlay(ref: CanvasRef): PlayTimes? {
        val bytes = try {
            files.read("${dirOf(ref)}/$PLAY") ?: return null
        } catch (_: FileStoreException) {
            return null
        }
        return (PlayFileCodec.decode(bytes) as? Decoded.Ok)?.value
    }

    /** Written with each save (T35). Not reported: losing it costs one evening's reminder precision. */
    fun writePlay(ref: CanvasRef, times: PlayTimes): WriteResult {
        val dir = dirOf(ref)
        if (!files.isDirectory(dir)) return WriteResult.WriteFailed(diskFull = false)
        return try {
            files.writeAtomic("$dir/$PLAY", PlayFileCodec.encode(times))
            WriteResult.Written
        } catch (e: FileStoreException) {
            WriteResult.WriteFailed(e.diskFull)
        }
    }

    /** A photo canvas's cropped photo, relative to the files folder (the encoder reads it as a file on Android). */
    fun sourcePath(ref: CanvasRef): String = "${dirOf(ref)}/$SOURCE"

    /** Whether [ref] keeps a cropped photo (photo canvases only). */
    fun hasSource(ref: CanvasRef): Boolean = ref.kind == CanvasKind.PHOTO && files.exists(sourcePath(ref)) && !files.isDirectory(sourcePath(ref))

    /** The last gallery thumbnail's PNG, or null when none was written or it can't be read. */
    fun readThumb(ref: CanvasRef): ByteArray? = try {
        files.read("${dirOf(ref)}/$THUMB")
    } catch (_: FileStoreException) {
        null
    }

    fun writeThumb(ref: CanvasRef, png: ByteArray) {
        val dir = dirOf(ref)
        if (!files.isDirectory(dir)) return
        try {
            files.writeAtomic("$dir/$THUMB", png)
        } catch (_: FileStoreException) {
        }
    }

    /**
     * Start over / Play again: removes the progress (last good first, so an interrupted clear never brings back
     * older progress), the view state and the thumbnail, and resets the play time and the last gem (T35). The grid,
     * title, source and the first finished day stay.
     * Callers close the canvas's session and cancel its encode first.
     */
    fun clearProgress(ref: CanvasRef): Boolean {
        val dir = dirOf(ref)
        if (!files.isDirectory(dir)) return false
        for (name in listOf(LAST_GOOD, SAVE, VIEW, THUMB)) {
            val f = "$dir/$name"
            if (files.exists(f) && !files.delete(f)) {
                reporter.report(CanvasStorageException("${ref.reportLabel}: clearing $name failed"))
                return false
            }
        }
        files.syncDir(dir)
        readRecord(ref)?.let { writeRecord(ref, it.copy(playMillis = 0)) }
        readPlay(ref)?.let { writePlay(ref, it.copy(lastGemMillis = null)) }
        return true
    }

    /**
     * Deletes the canvas with everything in it (grid, progress, view state, record, source photo). The directory is first renamed out
     * of the listing, so an interrupted delete never leaves a "Can't open" tile. Callers cancel the canvas's
     * encode first (eng E4d).
     */
    fun delete(ref: CanvasRef): Boolean {
        val dir = dirOf(ref)
        if (!files.exists(dir)) return true
        val doomed = "${ref.kind.dir}/.${ref.id}.deleted"
        return try {
            files.deleteRecursive(doomed)
            files.moveDir(dir, doomed)
            files.deleteRecursive(doomed)
            true
        } catch (e: FileStoreException) {
            reporter.report(CanvasStorageException("${ref.reportLabel}: delete failed", e))
            false
        }
    }

    /** Clears leftovers of interrupted creates, deletes and writes. Call once at startup, before opening canvases. */
    fun sweep() {
        for (kind in CanvasKind.entries) {
            for (name in files.list(kind.dir)) {
                val f = "${kind.dir}/$name"
                when {
                    name.startsWith(".") -> files.deleteRecursive(f)
                    files.isDirectory(f) -> files.list(f).filter { it.endsWith(FileStore.TEMP_SUFFIX) }.forEach { files.delete("$f/$it") }
                }
            }
        }
    }

    private sealed interface Attempt<out T> {
        class Ok<T>(val value: T) : Attempt<T>

        class Failed(val problem: LoadProblem) : Attempt<Nothing>
    }

    private fun <T> read(path: String, which: CanvasFile, decode: (ByteArray) -> Decoded<T>): Attempt<T> {
        val bytes = try {
            files.read(path) ?: return Attempt.Failed(LoadProblem.Missing(which))
        } catch (e: FileStoreException) {
            return Attempt.Failed(LoadProblem.Unreadable(which, e))
        }
        return when (val d = decode(bytes)) {
            is Decoded.Ok -> Attempt.Ok(d.value)
            is Decoded.Refused -> Attempt.Failed(LoadProblem.Refused(which, d.reason))
        }
    }

    private fun damaged(ref: CanvasRef, problem: LoadProblem): LoadResult.Damaged {
        val cause = (problem as? LoadProblem.Unreadable)?.error
        reporter.report(CanvasStorageException("${ref.reportLabel}: damaged, $problem", cause))
        return LoadResult.Damaged(problem)
    }

    /** A full disk is the player's to fix and is shown on the canvas, so only other failures are reported. */
    private fun writeFailed(ref: CanvasRef, what: String, e: FileStoreException): WriteResult.WriteFailed {
        val diskFull = e.diskFull
        if (!diskFull) reporter.report(CanvasStorageException("${ref.reportLabel}: $what write failed", e))
        return WriteResult.WriteFailed(diskFull)
    }

    companion object {
        const val GRID = "grid"
        const val SAVE = "save"
        const val LAST_GOOD = "save.last-good"
        const val VIEW = "view"
        const val RECORD = "record"
        const val SOURCE = "source.jpg"
        const val THUMB = "thumb.png"
        const val PLAY = "play"
    }
}
