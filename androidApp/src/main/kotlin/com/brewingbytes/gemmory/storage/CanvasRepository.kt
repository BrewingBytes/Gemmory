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
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class CanvasKind(internal val dir: String) {
    PHOTO(StorageLayout.PHOTO_CANVASES),
    BUNDLED(StorageLayout.BUNDLED_CANVASES),

    /** A remote picture (#43): played exactly like a bundled one, its grid copied in from the download cache. */
    REMOTE(StorageLayout.REMOTE_CANVASES),
}

data class CanvasRef(val kind: CanvasKind, val id: String) {
    init {
        require(ID.matches(id)) { "canvas id must match ${ID.pattern}" }
    }

    companion object {
        /** Lowercase, no dots or slashes, so an id is always one plain directory name. */
        val ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
    }
}

/** Which file of a canvas a [LoadProblem] is about. */
enum class CanvasFile { DIR, GRID, SAVE, LAST_GOOD }

sealed interface LoadProblem {
    val file: CanvasFile

    data class Missing(override val file: CanvasFile) : LoadProblem

    data class Unreadable(override val file: CanvasFile, val error: IOException) : LoadProblem

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

sealed interface WriteResult {
    data object Written : WriteResult

    /** [diskFull] drives "Progress isn't saving: phone storage is full." on the canvas (design D9). */
    data class WriteFailed(val diskFull: Boolean) : WriteResult
}

class CanvasStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Canvas records in app-private storage, one directory each: `files/canvases/<photo|bundled|remote>/<id>/` holding
 * `grid` (GridCodec), `save` (SaveCodec), `save.last-good`, `view` (ViewStateCodec), `record` (CanvasRecordCodec:
 * title, creation time, play time), `play` (PlayFileCodec, T35: last gem and first finished day), `thumb.png` (the last gallery thumbnail, kept for the "Can't open" tile) and,
 * for photo canvases, `source.jpg` (the cropped photo, at most 1024 px). Photo canvases live under the directory the
 * backup rules exclude, so none of it leaves the phone. Every write is atomic
 * (temp + fsync + rename). Before `save` is replaced, a copy of it that still decodes becomes `save.last-good`,
 * so a refused `save` falls back to progress at most one save interval older. It's a copy, not a hard link: SELinux
 * denies `link` on app data files (CanvasRepositoryDeviceTest).
 *
 * Blocking IO: call from a background dispatcher, with one writer per canvas at a time.
 */
class CanvasRepository internal constructor(
    filesDir: File,
    private val reporter: NonFatalReporter,
    private val codec: SaveCodec,
    private val files: AtomicFiles,
) {
    constructor(filesDir: File, reporter: NonFatalReporter = NonFatalReporter.Logcat) :
        this(filesDir, reporter, SaveCodec(), AtomicFiles())

    private val root = filesDir

    fun dirOf(ref: CanvasRef): File = File(File(root, ref.kind.dir), ref.id)

    /** Ids of the canvases of [kind], sorted. Half-created or half-deleted canvases are not listed. */
    fun list(kind: CanvasKind): List<String> =
        File(root, kind.dir).listFiles { f -> f.isDirectory && CanvasRef.ID.matches(f.name) }
            ?.map { it.name }?.sorted().orEmpty()

    /**
     * Starts a canvas from an encoded grid: a photo grid just made by the converter, or a bundled picture's frozen
     * grid asset copied as is (never re-converted). [record] and, for a photo canvas, its cropped [source] photo are
     * written in the same step. The canvas appears only once all of its files are complete.
     */
    fun create(ref: CanvasRef, gridBytes: ByteArray, record: CanvasRecord? = null, source: ByteArray? = null): WriteResult {
        require(GridCodec.decode(gridBytes) is Decoded.Ok) { "not a valid grid file" }
        require(source == null || ref.kind == CanvasKind.PHOTO) { "only photo canvases keep a source photo" }
        val dir = dirOf(ref)
        require(!dir.exists()) { "canvas ${ref.id} already exists" }
        val staging = File(dir.parentFile, ".${ref.id}.new")
        return try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw IOException("can't create ${staging.name}")
            files.write(File(staging, GRID), gridBytes)
            if (record != null) files.write(File(staging, RECORD), CanvasRecordCodec.encode(record))
            if (source != null) files.write(File(staging, SOURCE), source)
            Files.move(staging.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            AtomicFiles.syncDir(dir.parentFile)
            WriteResult.Written
        } catch (e: IOException) {
            staging.deleteRecursively()
            writeFailed(ref, "create", e)
        }
    }

    fun load(ref: CanvasRef): LoadResult {
        val dir = dirOf(ref)
        if (!dir.isDirectory) return damaged(ref, LoadProblem.Missing(CanvasFile.DIR))
        val grid = when (val g = read(File(dir, GRID), CanvasFile.GRID) { GridCodec.decode(it) }) {
            is Attempt.Ok -> g.value
            is Attempt.Failed -> return damaged(ref, g.problem)
        }
        val binding = grid.binding
        val primary = read(File(dir, SAVE), CanvasFile.SAVE) { codec.decode(it, binding) }
        if (primary is Attempt.Ok) return LoadResult.Loaded(grid, primary.value)
        primary as Attempt.Failed
        val problem = primary.problem
        val lastGood = read(File(dir, LAST_GOOD), CanvasFile.LAST_GOOD) { codec.decode(it, binding) }
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
        if (!dir.isDirectory) return WriteResult.WriteFailed(diskFull = false)
        val bytes = try {
            codec.encode(grid, log)
        } catch (e: IllegalArgumentException) {
            reporter.report(CanvasStorageException("${ref.reportLabel}: refused to encode save", e))
            return WriteResult.WriteFailed(diskFull = false)
        }
        val save = File(dir, SAVE)
        return try {
            if (save.isFile) {
                // An unreadable save is treated like a refused one: no last-good copy, and the rename still replaces
                // it (that needs the directory's permissions, not the file's).
                val currentBytes = try {
                    save.readBytes()
                } catch (_: IOException) {
                    null
                }
                val current = currentBytes?.let { codec.decode(it, grid) }
                if (current is Decoded.Ok) {
                    files.write(File(dir, LAST_GOOD), currentBytes)
                } else if ((current as? Decoded.Refused)?.reason is Refusal.NewerVersion) {
                    reporter.report(CanvasStorageException("${ref.reportLabel}: won't overwrite a newer save"))
                    return WriteResult.WriteFailed(diskFull = false)
                }
            }
            files.write(save, bytes)
            WriteResult.Written
        } catch (e: IOException) {
            writeFailed(ref, "save", e)
        }
    }

    /** The saved view, or null (use defaults) if it's missing, unreadable or out of range for [paletteSize]. */
    fun readView(ref: CanvasRef, paletteSize: Int): ViewState? {
        val file = File(dirOf(ref), VIEW)
        val bytes = try {
            if (file.isFile) file.readBytes() else null
        } catch (_: IOException) {
            null
        }
        return ViewStateCodec.decode(bytes, paletteSize)
    }

    /** Written on Activity onStop only (no per-pan writes). Not reported: losing the view costs one pinch. */
    fun writeView(ref: CanvasRef, state: ViewState): WriteResult {
        val dir = dirOf(ref)
        if (!dir.isDirectory) return WriteResult.WriteFailed(diskFull = false)
        return try {
            files.write(File(dir, VIEW), ViewStateCodec.encode(state))
            WriteResult.Written
        } catch (e: IOException) {
            WriteResult.WriteFailed(AtomicFiles.isDiskFull(e))
        }
    }

    /** The record, or null if it's missing or refused (the caller falls back to a default title). */
    fun readRecord(ref: CanvasRef): CanvasRecord? {
        val file = File(dirOf(ref), RECORD)
        val bytes = try {
            if (file.isFile) file.readBytes() else return null
        } catch (_: IOException) {
            return null
        }
        return (CanvasRecordCodec.decode(bytes) as? Decoded.Ok)?.value
    }

    /** Rename and play time. A failure is not reported: the old title or a few minutes of play time are kept. */
    fun writeRecord(ref: CanvasRef, record: CanvasRecord): WriteResult {
        val dir = dirOf(ref)
        if (!dir.isDirectory) return WriteResult.WriteFailed(diskFull = false)
        return try {
            files.write(File(dir, RECORD), CanvasRecordCodec.encode(record))
            WriteResult.Written
        } catch (e: IOException) {
            WriteResult.WriteFailed(AtomicFiles.isDiskFull(e))
        }
    }

    /** When the canvas was last played and first finished (T35), or null if unknown: missing, unreadable or refused. */
    fun readPlay(ref: CanvasRef): PlayTimes? {
        val file = File(dirOf(ref), PLAY)
        val bytes = try {
            if (file.isFile) file.readBytes() else return null
        } catch (_: IOException) {
            return null
        }
        return (PlayFileCodec.decode(bytes) as? Decoded.Ok)?.value
    }

    /** Written with each save (T35). Not reported: losing it costs one evening's reminder precision. */
    fun writePlay(ref: CanvasRef, times: PlayTimes): WriteResult {
        val dir = dirOf(ref)
        if (!dir.isDirectory) return WriteResult.WriteFailed(diskFull = false)
        return try {
            files.write(File(dir, PLAY), PlayFileCodec.encode(times))
            WriteResult.Written
        } catch (e: IOException) {
            WriteResult.WriteFailed(AtomicFiles.isDiskFull(e))
        }
    }

    fun sourceFile(ref: CanvasRef): File = File(dirOf(ref), SOURCE)

    fun thumbFile(ref: CanvasRef): File = File(dirOf(ref), THUMB)

    fun writeThumb(ref: CanvasRef, png: ByteArray) {
        val dir = dirOf(ref)
        if (!dir.isDirectory) return
        try {
            files.write(File(dir, THUMB), png)
        } catch (_: IOException) {
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
        if (!dir.isDirectory) return false
        for (name in listOf(LAST_GOOD, SAVE, VIEW, THUMB)) {
            val f = File(dir, name)
            if (f.exists() && !f.delete()) {
                reporter.report(CanvasStorageException("${ref.reportLabel}: clearing $name failed"))
                return false
            }
        }
        AtomicFiles.syncDir(dir)
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
        if (!dir.exists()) return true
        val doomed = File(dir.parentFile, ".${ref.id}.deleted")
        return try {
            doomed.deleteRecursively()
            Files.move(dir.toPath(), doomed.toPath(), StandardCopyOption.ATOMIC_MOVE)
            AtomicFiles.syncDir(dir.parentFile)
            doomed.deleteRecursively()
            true
        } catch (e: IOException) {
            reporter.report(CanvasStorageException("${ref.reportLabel}: delete failed", e))
            false
        }
    }

    /** Clears leftovers of interrupted creates, deletes and writes. Call once at startup, before opening canvases. */
    fun sweep() {
        for (kind in CanvasKind.entries) {
            val parent = File(root, kind.dir)
            parent.listFiles()?.forEach { f ->
                when {
                    f.name.startsWith(".") -> f.deleteRecursively()
                    f.isDirectory -> f.listFiles { t -> t.name.endsWith(AtomicFiles.TEMP_SUFFIX) }?.forEach { it.delete() }
                }
            }
        }
    }

    private sealed interface Attempt<out T> {
        class Ok<T>(val value: T) : Attempt<T>

        class Failed(val problem: LoadProblem) : Attempt<Nothing>
    }

    private fun <T> read(file: File, which: CanvasFile, decode: (ByteArray) -> Decoded<T>): Attempt<T> {
        if (!file.isFile) return Attempt.Failed(LoadProblem.Missing(which))
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
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
    private fun writeFailed(ref: CanvasRef, what: String, e: IOException): WriteResult.WriteFailed {
        val diskFull = AtomicFiles.isDiskFull(e)
        if (!diskFull) reporter.report(CanvasStorageException("${ref.reportLabel}: $what write failed", e))
        return WriteResult.WriteFailed(diskFull)
    }

    internal companion object {
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
