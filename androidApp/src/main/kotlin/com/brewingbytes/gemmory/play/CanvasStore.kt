package com.brewingbytes.gemmory.play

import android.os.SystemClock
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.CanvasRepository
import com.brewingbytes.gemmory.storage.LoadProblem
import com.brewingbytes.gemmory.storage.LoadResult
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One gallery tile's data. */
sealed interface CanvasSummary {
    val ref: CanvasRef
    val title: String
    val createdAtMillis: Long

    /** [state] is the restored progress (empty for a bundled picture never opened). */
    class Playable(
        override val ref: CanvasRef,
        override val title: String,
        val state: CanvasState,
        override val createdAtMillis: Long,
    ) : CanvasSummary

    /** "Can't open" (design D9): [thumb] is the last thumbnail, if one was ever written. */
    class Damaged(
        override val ref: CanvasRef,
        override val title: String,
        override val createdAtMillis: Long,
        val thumb: File?,
    ) : CanvasSummary
}

/**
 * App-wide entry to canvas files. Every repository call runs on [writer], a single-thread dispatcher, and a canvas
 * is opened, cleared or deleted only after the previous session's last write has finished, so a canvas never has two
 * writers and a late write can't bring back progress that was just cleared. [CanvasRepository.sweep] runs once
 * when the store is made (app start), before anything is opened or created.
 */
class CanvasStore(
    filesDir: File,
    private val scope: CoroutineScope,
    private val writer: CoroutineDispatcher,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    /** A bundled picture's frozen grid asset, read as is. */
    private val bundledGrid: (BundledPicture) -> ByteArray = { error("no bundled grids") },
    private val encodes: EncodeCanceller,
    reporter: NonFatalReporter = NonFatalReporter.Logcat,
) {
    val repository = CanvasRepository(filesDir, reporter)
    private val swept: Job = scope.launch(writer) { repository.sweep() }
    private val closing = ConcurrentHashMap<CanvasRef, Job>()

    sealed interface Opened {
        class Ready internal constructor(
            val ref: CanvasRef,
            val title: String,
            val grid: GridFile,
            val state: CanvasState,
            val view: ViewState?,
            val record: CanvasRecord?,
        ) : Opened

        /** "Can't open" (design D9). */
        data class Damaged(val problem: LoadProblem?) : Opened
    }

    /** Loads a canvas, copying a bundled picture's frozen grid in first if it was never opened. */
    suspend fun open(ref: CanvasRef): Opened {
        settle(ref)
        return withContext(writer) {
            ensureBundled(ref)
            val (grid, log) = when (val r = repository.load(ref)) {
                is LoadResult.Loaded -> r.grid to r.log
                is LoadResult.RecoveredFromLastGood -> r.grid to r.log
                is LoadResult.Damaged -> return@withContext Opened.Damaged(r.problem)
            }
            // The codec already refused duplicate and out-of-range logs, so a replay can't fail here.
            val state = CanvasState.restore(grid.pattern, log) ?: return@withContext Opened.Damaged(null)
            val record = repository.readRecord(ref)
            Opened.Ready(ref, titleOf(ref, record), grid, state, repository.readView(ref, grid.pattern.palette.size), record)
        }
    }

    /** Starts the session of an opened canvas. Not suspending, so a cancelled caller can't leak a save loop. */
    fun start(opened: Opened.Ready): PlaySession {
        val ref = opened.ref
        val binding = opened.grid.binding
        val saver = SaveLoop(
            scope = CoroutineScope(scope.coroutineContext + writer),
            initialLog = opened.state.log(),
            nowMillis = nowMillis,
            writeSave = { log -> repository.writeSave(ref, binding, log) },
            writeView = { view -> repository.writeView(ref, view) },
        )
        val clock = PlayClock(opened.record?.playMillis ?: 0L, nowMillis)
        return PlaySession(
            ref = ref,
            title = opened.title,
            pattern = opened.grid.pattern,
            state = opened.state,
            view = opened.view,
            saver = saver,
            clock = clock,
            onStopped = { played -> scope.launch(writer) { writePlayTime(ref, opened.record, played) } },
            onClosed = { lastWrite, played ->
                val done = scope.launch(writer) {
                    lastWrite.join()
                    writePlayTime(ref, opened.record, played)
                }
                closing[ref] = done
                done.invokeOnCompletion { closing.remove(ref, done) }
            },
        )
    }

    /** The meta line's play time and the title, for the completion screen. */
    suspend fun record(ref: CanvasRef): Pair<String, CanvasRecord?> {
        settle(ref)
        return withContext(writer) {
            val record = repository.readRecord(ref)
            titleOf(ref, record) to record
        }
    }

    /** Photo canvas from Start (design D4). Idempotent per [ref], so a repeated Start makes one canvas (eng E4a). */
    suspend fun createPhoto(ref: CanvasRef, pattern: Pattern, record: CanvasRecord, sourceJpeg: ByteArray): WriteResult {
        swept.join()
        return withContext(writer) {
            if (repository.dirOf(ref).isDirectory) return@withContext WriteResult.Written
            repository.create(ref, GridCodec.encode(pattern), record, sourceJpeg)
        }
    }

    suspend fun create(ref: CanvasRef, pattern: Pattern): WriteResult {
        swept.join()
        return withContext(writer) { repository.create(ref, GridCodec.encode(pattern)) }
    }

    suspend fun list(kind: CanvasKind): List<String> {
        swept.join()
        return withContext(writer) { repository.list(kind) }
    }

    /** Every bundled picture in catalog order, then photo canvases newest first. */
    suspend fun summaries(): Pair<List<CanvasSummary>, List<CanvasSummary>> {
        swept.join()
        closing.values.toList().forEach { it.join() }
        return withContext(writer) {
            val pictures = BundledPictures.ALL.map { picture ->
                val ref = CanvasRef(CanvasKind.BUNDLED, picture.id)
                if (repository.dirOf(ref).isDirectory) {
                    summarize(ref)
                } else {
                    val grid = (GridCodec.decode(bundledGrid(picture)) as Decoded.Ok).value
                    CanvasSummary.Playable(ref, picture.title, CanvasState.empty(grid.pattern), 0L)
                }
            }
            val photos = repository.list(CanvasKind.PHOTO).map { summarize(CanvasRef(CanvasKind.PHOTO, it)) }
                .sortedByDescending { it.createdAtMillis }
            pictures to photos
        }
    }

    /** The title is drawn into the time-lapse band, so the canvas's cached videos and pictures go with the old one. */
    suspend fun rename(ref: CanvasRef, title: String): WriteResult {
        settle(ref)
        val written = withContext(writer) {
            val record = repository.readRecord(ref) ?: CanvasRecord("", 0L, 0L)
            repository.writeRecord(ref, record.copy(title = title))
        }
        encodes.cancelAndClear(ref)
        return written
    }

    /**
     * Start over / Play again (design D3): clears progress, view state and play time. A bundled picture is removed
     * whole and copied fresh from its frozen grid on the next open, which also fixes a damaged one.
     */
    suspend fun startOver(ref: CanvasRef): Boolean {
        settle(ref)
        encodes.cancelAndClear(ref)
        return withContext(writer) {
            when (ref.kind) {
                CanvasKind.BUNDLED -> repository.delete(ref)
                CanvasKind.PHOTO -> repository.clearProgress(ref)
            }
        }
    }

    /** Removes the photo, grid, progress and view state from the phone (design system, Delete confirm). */
    suspend fun delete(ref: CanvasRef): Boolean {
        settle(ref)
        encodes.cancelAndClear(ref)
        return withContext(writer) { repository.delete(ref) }
    }

    /** Settings "Delete all photo canvases" (design D27): cancels running encodes first (eng E4d). */
    suspend fun deleteAllPhotos(): Boolean {
        swept.join()
        val ids = withContext(writer) { repository.list(CanvasKind.PHOTO) }
        var ok = true
        for (id in ids) ok = delete(CanvasRef(CanvasKind.PHOTO, id)) && ok
        return ok
    }

    /** App-scoped, so leaving Settings doesn't stop a delete halfway. */
    fun deleteAllPhotosInBackground(): Job = scope.launch { deleteAllPhotos() }

    suspend fun writeThumb(ref: CanvasRef, png: ByteArray) {
        withContext(writer) { repository.writeThumb(ref, png) }
    }

    private suspend fun settle(ref: CanvasRef) {
        swept.join()
        closing[ref]?.join()
    }

    private fun ensureBundled(ref: CanvasRef) {
        if (ref.kind != CanvasKind.BUNDLED || repository.dirOf(ref).isDirectory) return
        val picture = BundledPictures.byId(ref.id) ?: return
        repository.create(ref, bundledGrid(picture), CanvasRecord("", System.currentTimeMillis(), 0L))
    }

    private fun summarize(ref: CanvasRef): CanvasSummary {
        val record = repository.readRecord(ref)
        val title = titleOf(ref, record)
        val (grid, log) = when (val r = repository.load(ref)) {
            is LoadResult.Loaded -> r.grid to r.log
            is LoadResult.RecoveredFromLastGood -> r.grid to r.log
            is LoadResult.Damaged -> return damaged(ref, title, record)
        }
        val state = CanvasState.restore(grid.pattern, log) ?: return damaged(ref, title, record)
        return CanvasSummary.Playable(ref, title, state, record?.createdAtMillis ?: 0L)
    }

    private fun damaged(ref: CanvasRef, title: String, record: CanvasRecord?) =
        CanvasSummary.Damaged(ref, title, record?.createdAtMillis ?: 0L, repository.thumbFile(ref).takeIf { it.isFile })

    private fun writePlayTime(ref: CanvasRef, opened: CanvasRecord?, playMillis: Long) {
        // Re-read, so a rename made while the canvas was closed isn't overwritten by the opened copy.
        val current = repository.readRecord(ref) ?: opened ?: CanvasRecord("", 0L, 0L)
        repository.writeRecord(ref, current.copy(playMillis = playMillis))
    }

    companion object {
        const val DEFAULT_PHOTO_TITLE = "My photo"

        fun titleOf(ref: CanvasRef, record: CanvasRecord?): String = when (ref.kind) {
            CanvasKind.BUNDLED -> BundledPictures.byId(ref.id)?.title ?: ref.id
            CanvasKind.PHOTO -> record?.title?.takeIf { it.isNotBlank() } ?: DEFAULT_PHOTO_TITLE
        }
    }
}

/** Active play time of one session on top of what the record held: counts only between start and stop. */
class PlayClock(private val base: Long, private val nowMillis: () -> Long) {
    private var banked = base
    private var since: Long? = nowMillis()

    fun resume() {
        if (since == null) since = nowMillis()
    }

    /** Pauses and returns the total so far. */
    fun pause(): Long {
        since?.let { banked += (nowMillis() - it).coerceAtLeast(0) }
        since = null
        return banked
    }

    fun total(): Long = banked + (since?.let { (nowMillis() - it).coerceAtLeast(0) } ?: 0)
}
