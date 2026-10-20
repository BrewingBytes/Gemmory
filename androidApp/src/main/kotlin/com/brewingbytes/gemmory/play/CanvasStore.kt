package com.brewingbytes.gemmory.play

import android.os.SystemClock
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.policy.AchievementRules
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.GameEvent
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.remote.RemoteCatalogView
import com.brewingbytes.gemmory.remote.RemotePictureRules
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.save.PlayTimes
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.CanvasRepository
import com.brewingbytes.gemmory.storage.LoadProblem
import com.brewingbytes.gemmory.storage.LoadResult
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.CanvasEntry
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * One gallery tile's data. [size] is a picture's Quick / Medium / Big (null for the starter and photo canvases);
 * [locked] is its catalog flag (`BundledPictures.FREE`, or a remote picture's `locked`), before unlocks and VIP.
 */
sealed interface CanvasSummary {
    val ref: CanvasRef
    val title: String
    val createdAtMillis: Long
    val size: CanvasSize?
    val locked: Boolean

    /** [state] is the restored progress (empty for a picture never opened). */
    class Playable(
        override val ref: CanvasRef,
        override val title: String,
        val state: CanvasState,
        override val createdAtMillis: Long,
        override val size: CanvasSize? = null,
        override val locked: Boolean = false,
    ) : CanvasSummary

    /**
     * A remote picture never opened (T36, eng D12), from its catalog entry alone: nothing is decoded until its
     * thumbnail is drawn ([CanvasStore.pattern]), so a year of daily drops doesn't slow the gallery. [width] ×
     * [height] is its grid.
     */
    class Listed(
        override val ref: CanvasRef,
        override val title: String,
        val width: Int,
        val height: Int,
        override val size: CanvasSize?,
        override val locked: Boolean,
    ) : CanvasSummary {
        override val createdAtMillis: Long get() = 0L
    }

    /** "Can't open" (design D9): [thumb] is the last thumbnail, if one was ever written. */
    class Damaged(
        override val ref: CanvasRef,
        override val title: String,
        override val createdAtMillis: Long,
        val thumb: File?,
        override val size: CanvasSize? = null,
        override val locked: Boolean = false,
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
    private val analytics: Analytics = Analytics.None,
    /** Remote pictures (#43): listed in the gallery like bundled ones, their grids copied in on first open. */
    private val remote: RemoteCatalogView = RemoteCatalogView.None,
    /** Play Games achievements (#49): a fresh finish is counted here, with the canvas's size and kind. */
    private val achievements: (GameEvent) -> Unit = {},
    /** Wall time and local date for the `play` file (T35): when a gem was last saved and the day of the finish. */
    private val dayClock: DayClock = DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() },
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
            /** Where the player opened it from, for the canvas events (T36). */
            val entry: CanvasEntry = CanvasEntry.GALLERY,
            /** Its `play` file (T35), carried into the session so each save updates it without a read. */
            val play: PlayTimes = PlayTimes(),
        ) : Opened

        /** "Can't open" (design D9). */
        data class Damaged(val problem: LoadProblem?) : Opened
    }

    /**
     * Loads a canvas, copying a bundled or remote picture's frozen grid in first if it was never opened. A remote
     * picture that isn't listed (a dated drop before its date, T36) can't be copied in, so it's Damaged and the
     * canvas screen goes back to the gallery.
     */
    suspend fun open(ref: CanvasRef, entry: CanvasEntry = CanvasEntry.GALLERY): Opened {
        settle(ref)
        return withContext(writer) {
            val started = ensurePicture(ref)
            val (grid, log) = when (val r = repository.load(ref)) {
                is LoadResult.Loaded -> r.grid to r.log
                is LoadResult.RecoveredFromLastGood -> r.grid to r.log
                is LoadResult.Damaged -> return@withContext Opened.Damaged(r.problem)
            }
            if (started) {
                val days = remote.daysSinceAvailable(ref.id).takeIf { ref.kind == CanvasKind.REMOTE }
                analytics.log(AnalyticsEvent.canvasStarted(ref, grid.pattern.width, grid.pattern.height, entry = entry, daysSinceAvailable = days))
            }
            // The codec already refused duplicate and out-of-range logs, so a replay can't fail here.
            val state = CanvasState.restore(grid.pattern, log) ?: return@withContext Opened.Damaged(null)
            val record = repository.readRecord(ref)
            val play = repository.readPlay(ref) ?: PlayTimes()
            Opened.Ready(ref, titleOf(ref, record), grid, state, repository.readView(ref, grid.pattern.palette.size), record, entry, play)
        }
    }

    /** Starts the session of an opened canvas. Not suspending, so a cancelled caller can't leak a save loop. */
    fun start(opened: Opened.Ready): PlaySession {
        val ref = opened.ref
        val binding = opened.grid.binding
        val cells = opened.state.cellCount
        // Touched on [writer] only. The finish is the save of the full log, so its day is stamped in the same call.
        var play = opened.play
        val saver = SaveLoop(
            scope = CoroutineScope(scope.coroutineContext + writer),
            initialLog = opened.state.log(),
            nowMillis = nowMillis,
            writeSave = { log ->
                repository.writeSave(ref, binding, log).also { result ->
                    if (result == WriteResult.Written && log.isNotEmpty()) {
                        val finishedDay = play.firstFinishedDay ?: dayClock.today().takeIf { log.size == cells }
                        play = PlayTimes(dayClock.nowMillis(), finishedDay)
                        repository.writePlay(ref, play)
                    }
                }
            },
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
            onPictureFinished = {
                val pattern = opened.grid.pattern
                val days = remote.daysSinceAvailable(ref.id).takeIf { ref.kind == CanvasKind.REMOTE }
                analytics.log(AnalyticsEvent.canvasFinished(ref, pattern.width, pattern.height, opened.entry, days))
                achievements(GameEvent.Finished(sizeOf(ref, pattern), ref.kind == CanvasKind.PHOTO))
            },
            onPlaced = { encodes.onPlaced(ref) },
            // VIP uses the tools without limit; anyone else pays with fills (#38).
            onToolUsed = { tool, unlimited ->
                val pattern = opened.grid.pattern
                val tier = if (unlimited) "vip" else "free"
                analytics.log(AnalyticsEvent.fillToolUsed(ref, pattern.width, pattern.height, tool.param, tier))
            },
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

    /**
     * Photo canvas from Start (design D4). Idempotent per [ref], so a repeated Start makes one canvas (eng E4a).
     * [colors] is the Preview choice it was made with (#29), for the start events only.
     */
    suspend fun createPhoto(
        ref: CanvasRef,
        pattern: Pattern,
        record: CanvasRecord,
        sourceJpeg: ByteArray,
        colors: ColorChoice = ColorChoice.STANDARD,
    ): WriteResult {
        swept.join()
        return withContext(writer) {
            if (repository.dirOf(ref).isDirectory) return@withContext WriteResult.Written
            repository.create(ref, GridCodec.encode(pattern), record, sourceJpeg).also {
                if (it == WriteResult.Written) {
                    analytics.log(AnalyticsEvent.canvasStarted(ref, pattern.width, pattern.height, colors, CanvasEntry.GALLERY))
                    analytics.log(AnalyticsEvent.photoCanvasStarted(colors))
                }
            }
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

    /**
     * Every picture in catalog order (bundled, with remote pictures merged into their size, #43), then photo
     * canvases newest first. A remote picture shows while the catalog lists it, or once started (the withdraw rule).
     * One never opened is [CanvasSummary.Listed], from its catalog entry (T36, eng D12).
     */
    suspend fun summaries(): Pair<List<CanvasSummary>, List<CanvasSummary>> {
        swept.join()
        closing.values.toList().forEach { it.join() }
        return withContext(writer) {
            val bundled = BundledPictures.ALL.map { picture ->
                val ref = CanvasRef(CanvasKind.BUNDLED, picture.id)
                val size = BundledPictures.canvasSizeOf(picture.id)
                if (repository.dirOf(ref).isDirectory) {
                    summarize(ref, size, picture.locked)
                } else {
                    val grid = (GridCodec.decode(bundledGrid(picture)) as Decoded.Ok).value
                    CanvasSummary.Playable(ref, picture.title, CanvasState.empty(grid.pattern), 0L, size, picture.locked)
                }
            }
            val listed = remote.listed()
            val listedIds = listed.map { it.id }.toSet()
            val added = listed.associate { it.id to it.addedAt }
            val inCatalog = listed.map { picture ->
                val ref = CanvasRef(CanvasKind.REMOTE, picture.id)
                if (repository.dirOf(ref).isDirectory) {
                    summarize(ref, picture.size, picture.locked)
                } else {
                    CanvasSummary.Listed(ref, picture.title, picture.width, picture.height, picture.size, picture.locked)
                }
            }
            // Withdrawn from the catalog (or a restored save before the first check): shown only once started.
            val kept = repository.list(CanvasKind.REMOTE).filter { it !in listedIds }.mapNotNull { id ->
                val summary = summarize(CanvasRef(CanvasKind.REMOTE, id), null, false)
                val started = summary is CanvasSummary.Damaged || (summary as CanvasSummary.Playable).state.placedCount > 0
                if (!RemotePictureRules.shows(inCatalog = false, gridReady = false, started = started)) return@mapNotNull null
                if (summary is CanvasSummary.Playable) summary.withSize(sizeOfGrid(summary.state)) else summary
            }
            val pictures = RemotePictureRules.merge(bundled, inCatalog + kept, { it.size }, { added[it.ref.id] }, { it.ref.id })
            val photos = repository.list(CanvasKind.PHOTO).map { summarize(CanvasRef(CanvasKind.PHOTO, it), null, false) }
                .sortedByDescending { it.createdAtMillis }
            pictures to photos
        }
    }

    /**
     * Every canvas with a gem placed, as the evening reminder reads it (T35, eng D12): its progress and `play` file,
     * nothing rendered. Damaged canvases are left out. [ReminderCanvas.key] is [reminderKey].
     */
    suspend fun reminderCanvases(): List<ReminderCanvas> {
        swept.join()
        closing.values.toList().forEach { it.join() }
        return withContext(writer) {
            CanvasKind.entries.flatMap { kind ->
                repository.list(kind).mapNotNull { id ->
                    val ref = CanvasRef(kind, id)
                    val (grid, log) = when (val r = repository.load(ref)) {
                        is LoadResult.Loaded -> r.grid to r.log
                        is LoadResult.RecoveredFromLastGood -> r.grid to r.log
                        is LoadResult.Damaged -> return@mapNotNull null
                    }
                    if (log.isEmpty()) return@mapNotNull null
                    val pictureId = id.takeIf { kind != CanvasKind.PHOTO }
                    ReminderCanvas(reminderKey(ref), pictureId, log.size, grid.pattern.cells.size, repository.readPlay(ref)?.lastGemMillis)
                }
            }
        }
    }

    /**
     * The finished picture of a [CanvasSummary.Listed] remote picture, for its thumbnail or the Today card (T36, eng
     * D12); null when it isn't listed any more or its grid was refused. Reads only the download cache, never a canvas,
     * so it stays off [writer]: call it on an IO thread.
     */
    fun pattern(ref: CanvasRef): Pattern? {
        if (ref.kind != CanvasKind.REMOTE) return null
        return remote.grid(ref.id)?.let { (GridCodec.decode(it) as? Decoded.Ok)?.value?.pattern }
    }

    /**
     * Whether [ref] is finished on disk, once its closing write is done. A tile tap opens from this: the gallery's
     * tiles can still show a canvas as it was before the last visit (#50).
     */
    suspend fun isFinished(ref: CanvasRef): Boolean {
        settle(ref)
        return withContext(writer) {
            repository.dirOf(ref).isDirectory && (summarize(ref, null, false) as? CanvasSummary.Playable)?.state?.isFinished == true
        }
    }

    /** The title is drawn into the time-lapse band, so the canvas's cached videos and pictures go with the old one. */
    /** Whether [ref] has a gem placed on disk (a started picture never locks again, eng E4c). */
    suspend fun isStarted(ref: CanvasRef): Boolean {
        settle(ref)
        return withContext(writer) {
            repository.dirOf(ref).isDirectory && when (val r = repository.load(ref)) {
                is LoadResult.Loaded -> r.log.isNotEmpty()
                is LoadResult.RecoveredFromLastGood -> r.log.isNotEmpty()
                is LoadResult.Damaged -> true
            }
        }
    }

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
     * Start over / Play again (design D3): clears progress, view state and play time. A bundled or remote picture is
     * removed whole and copied fresh from its frozen grid on the next open, which also fixes a damaged one. A remote
     * picture withdrawn from the catalog is then gone, like any withdrawn picture not started (#43). The first finished
     * day (T35) is kept: a picture that has one is copied fresh at once, with only that day in its `play` file.
     */
    suspend fun startOver(ref: CanvasRef): Boolean {
        settle(ref)
        encodes.cancelAndClear(ref)
        return withContext(writer) {
            when (ref.kind) {
                CanvasKind.BUNDLED, CanvasKind.REMOTE -> {
                    val finishedDay = repository.readPlay(ref)?.firstFinishedDay
                    repository.delete(ref).also { deleted ->
                        if (deleted && finishedDay != null && ensurePicture(ref)) repository.writePlay(ref, PlayTimes(firstFinishedDay = finishedDay))
                    }
                }
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

    /**
     * True when this call copied the picture in, i.e. the player just started it. A remote picture's record keeps
     * its catalog title, so it still has one after it leaves the catalog or comes back from a backup (#43).
     */
    private fun ensurePicture(ref: CanvasRef): Boolean {
        if (repository.dirOf(ref).isDirectory) return false
        val now = System.currentTimeMillis()
        return when (ref.kind) {
            CanvasKind.PHOTO -> false
            CanvasKind.BUNDLED -> {
                val picture = BundledPictures.byId(ref.id) ?: return false
                repository.create(ref, bundledGrid(picture), CanvasRecord("", now, 0L)) == WriteResult.Written
            }
            CanvasKind.REMOTE -> {
                val picture = remote.listed().firstOrNull { it.id == ref.id } ?: return false
                val grid = remote.grid(ref.id) ?: return false
                repository.create(ref, grid, CanvasRecord(picture.title, now, 0L)) == WriteResult.Written
            }
        }
    }

    private fun summarize(ref: CanvasRef, size: CanvasSize?, locked: Boolean): CanvasSummary {
        val record = repository.readRecord(ref)
        val title = titleOf(ref, record)
        val (grid, log) = when (val r = repository.load(ref)) {
            is LoadResult.Loaded -> r.grid to r.log
            is LoadResult.RecoveredFromLastGood -> r.grid to r.log
            is LoadResult.Damaged -> return damaged(ref, title, record, size, locked)
        }
        val state = CanvasState.restore(grid.pattern, log) ?: return damaged(ref, title, record, size, locked)
        return CanvasSummary.Playable(ref, title, state, record?.createdAtMillis ?: 0L, size, locked)
    }

    private fun damaged(ref: CanvasRef, title: String, record: CanvasRecord?, size: CanvasSize?, locked: Boolean) =
        CanvasSummary.Damaged(ref, title, record?.createdAtMillis ?: 0L, repository.thumbFile(ref).takeIf { it.isFile }, size, locked)

    private fun CanvasSummary.Playable.withSize(size: CanvasSize?) = CanvasSummary.Playable(ref, title, state, createdAtMillis, size, locked)

    /** A canvas's size for achievements: a bundled picture's catalog size, else its grid's shape (null for the starter). */
    private fun sizeOf(ref: CanvasRef, pattern: Pattern): CanvasSize? =
        if (ref.kind == CanvasKind.BUNDLED) BundledPictures.canvasSizeOf(ref.id) else AchievementRules.sizeOf(pattern.width, pattern.height)

    /** A withdrawn remote picture's size, from its grid's shape. */
    private fun sizeOfGrid(state: CanvasState): CanvasSize? {
        val p = state.pattern
        return CanvasSize.entries.firstOrNull { size -> GridSpec.PICKER.any { it.width == p.width && it.height == p.height && it.id.startsWith(size.name.lowercase()) } }
    }

    private fun writePlayTime(ref: CanvasRef, opened: CanvasRecord?, playMillis: Long) {
        // Re-read, so a rename made while the canvas was closed isn't overwritten by the opened copy.
        val current = repository.readRecord(ref) ?: opened ?: CanvasRecord("", 0L, 0L)
        repository.writeRecord(ref, current.copy(playMillis = playMillis))
    }

    companion object {
        const val DEFAULT_PHOTO_TITLE = "My photo"

        /** How a [ReminderCanvas] names its canvas, and back (T35). */
        fun reminderKey(ref: CanvasRef): String = "${ref.kind.name}/${ref.id}"

        fun refOfReminderKey(key: String): CanvasRef? {
            val kind = CanvasKind.entries.firstOrNull { key.startsWith("${it.name}/") } ?: return null
            val id = key.substringAfter('/')
            return if (CanvasRef.ID.matches(id)) CanvasRef(kind, id) else null
        }

        fun titleOf(ref: CanvasRef, record: CanvasRecord?): String = when (ref.kind) {
            CanvasKind.BUNDLED -> BundledPictures.byId(ref.id)?.title ?: ref.id
            CanvasKind.REMOTE -> record?.title?.takeIf { it.isNotBlank() } ?: ref.id
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
