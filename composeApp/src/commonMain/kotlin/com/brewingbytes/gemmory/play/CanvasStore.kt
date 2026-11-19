package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.remote.titleIn
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
import com.brewingbytes.gemmory.remote.Mystery
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.remote.RemoteCatalogView
import com.brewingbytes.gemmory.remote.RemotePicture
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
import com.brewingbytes.gemmory.storage.FileStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * App-wide entry to canvas files. Every repository call runs on [writer], a single-thread dispatcher, and a canvas
 * is opened, cleared or deleted only after the previous session's last write has finished, so a canvas never has two
 * writers and a late write can't bring back progress that was just cleared. [CanvasRepository.sweep] runs once
 * when the store is made (app start), before anything is opened or created. Common since iOS Stage 1c (T64), over
 * [FileStore]; the shell makes it and hands it to shared screens as [Canvases].
 */
class CanvasStore(
    files: FileStore,
    private val scope: CoroutineScope,
    private val writer: CoroutineDispatcher,
    /** The monotonic clock play time counts on (Android: `SystemClock.elapsedRealtime`). */
    private val nowMillis: () -> Long = TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } },
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
    private val dayClock: DayClock = DayClock({ Clock.System.now().toEpochMilliseconds() }) { TimeZone.currentSystemDefault() },
    /**
     * The app's words now (T48): a photo canvas's default title, a mystery's neutral name, TalkBack's lines. Read at
     * each use, so a language switch (T49) shows at once.
     */
    private val words: () -> Words,
    /** Wall time for a new canvas's record. */
    private val wallMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : Canvases {
    val repository = CanvasRepository(files, reporter)
    private val swept: Job = scope.launch(writer) { repository.sweep() }

    /** Each closing session's last write, by canvas. Replaced whole on each change, so any thread may read it. */
    private val closing = MutableStateFlow<Map<CanvasRef, Job>>(emptyMap())

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
            /** An unfinished mystery picture's neutral name (T43): the canvas hides its title, tints, Peek and Share. */
            val hiddenAs: String? = null,
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
                analytics.log(
                    AnalyticsEvent.canvasStarted(
                        ref,
                        grid.pattern.width,
                        grid.pattern.height,
                        entry = entry,
                        daysSinceAvailable = days,
                        collectionId = remote.collectionOf(ref.id),
                        eventId = remote.eventOf(ref.id),
                        mystery = ref.kind == CanvasKind.REMOTE && remote.listed().any { it.id == ref.id && it.mystery },
                    ),
                )
            }
            // The codec already refused duplicate and out-of-range logs, so a replay can't fail here.
            val state = CanvasState.restore(grid.pattern, log) ?: return@withContext Opened.Damaged(null)
            val record = repository.readRecord(ref)
            val play = repository.readPlay(ref) ?: PlayTimes()
            val hidden = hiddenName(ref, record, state.isFinished, remote.listed().firstOrNull { it.id == ref.id })
            Opened.Ready(ref, titleOf(ref, record), grid, state, repository.readView(ref, grid.pattern.palette.size), record, entry, play, hidden)
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
            hiddenAs = opened.hiddenAs,
            a11y = A11yText(words()),
            pattern = opened.grid.pattern,
            state = opened.state,
            view = opened.view,
            saver = saver,
            clock = clock,
            onStopped = { played -> scope.launch(writer) { writePlayTime(ref, opened.record, played) } },
            onPictureFinished = {
                val pattern = opened.grid.pattern
                val days = remote.daysSinceAvailable(ref.id).takeIf { ref.kind == CanvasKind.REMOTE }
                analytics.log(AnalyticsEvent.canvasFinished(ref, pattern.width, pattern.height, opened.entry, days, remote.collectionOf(ref.id), remote.eventOf(ref.id)))
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
                closing.update { it + (ref to done) }
                done.invokeOnCompletion { closing.update { now -> if (now[ref] === done) now - ref else now } }
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
    override suspend fun createPhoto(
        ref: CanvasRef,
        pattern: Pattern,
        record: CanvasRecord,
        sourceJpeg: ByteArray,
        colors: ColorChoice,
    ): WriteResult {
        swept.join()
        return withContext(writer) {
            if (repository.exists(ref)) return@withContext WriteResult.Written
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
    override suspend fun summaries(): Pair<List<CanvasSummary>, List<CanvasSummary>> {
        swept.join()
        closing.value.values.forEach { it.join() }
        return withContext(writer) {
            val bundled = BundledPictures.ALL.map { picture ->
                val ref = CanvasRef(CanvasKind.BUNDLED, picture.id)
                val size = BundledPictures.canvasSizeOf(picture.id)
                if (repository.exists(ref)) {
                    summarize(ref, size, picture.locked)
                } else {
                    val grid = (GridCodec.decode(bundledGrid(picture)) as Decoded.Ok).value
                    CanvasSummary.Playable(ref, picture.title(words()), CanvasState.empty(grid.pattern), 0L, size, picture.locked)
                }
            }
            val listed = remote.listed()
            val listedIds = listed.map { it.id }.toSet()
            val added = listed.associate { it.id to it.addedAt }
            val inCatalog = listed.map { picture ->
                val ref = CanvasRef(CanvasKind.REMOTE, picture.id)
                if (repository.exists(ref)) {
                    summarize(ref, picture.size, picture.locked, picture)
                } else {
                    val hidden = hiddenName(ref, null, finished = false, picture)
                    CanvasSummary.Listed(ref, picture.titleIn(words().tag), picture.width, picture.height, picture.size, picture.locked, hidden)
                }
            }
            // Withdrawn from the catalog (or a restored save before the first check): shown only once started, or when
            // it's an earned keepsake (T37).
            val kept = repository.list(CanvasKind.REMOTE).filter { it !in listedIds }.mapNotNull { id ->
                val summary = summarize(CanvasRef(CanvasKind.REMOTE, id), null, false)
                val started = summary is CanvasSummary.Damaged || (summary as CanvasSummary.Playable).state.placedCount > 0
                if (!RemotePictureRules.shows(inCatalog = false, gridReady = false, started = started || remote.earned(id))) return@mapNotNull null
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
    override suspend fun reminderCanvases(): List<ReminderCanvas> {
        swept.join()
        closing.value.values.forEach { it.join() }
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
    override fun pattern(ref: CanvasRef): Pattern? {
        if (ref.kind != CanvasKind.REMOTE) return null
        return remote.grid(ref.id)?.let { (GridCodec.decode(it) as? Decoded.Ok)?.value?.pattern }
    }

    /**
     * Copies an earned keepsake's verified grid into its canvas (T37), through the same create path as a first open,
     * so it stays after the catalog withdraws it and goes along with a backup. It must be listed (earned) first.
     */
    suspend fun keep(id: String): KeepResult {
        swept.join()
        val ref = CanvasRef(CanvasKind.REMOTE, id)
        return withContext(writer) {
            if (repository.exists(ref)) return@withContext KeepResult.PRESENT
            val picture = remote.listed().firstOrNull { it.id == id } ?: return@withContext KeepResult.MISSING
            val grid = remote.grid(id) ?: return@withContext KeepResult.MISSING
            when (val r = repository.create(ref, grid, CanvasRecord(picture.title, wallMillis(), 0L))) {
                WriteResult.Written -> KeepResult.CREATED
                is WriteResult.WriteFailed -> if (r.diskFull) KeepResult.NO_SPACE else KeepResult.FAILED
            }
        }
    }

    /**
     * Whether [ref] is finished on disk, once its closing write is done. A tile tap opens from this: the gallery's
     * tiles can still show a canvas as it was before the last visit (#50).
     */
    suspend fun isFinished(ref: CanvasRef): Boolean {
        settle(ref)
        return withContext(writer) {
            repository.exists(ref) && (summarize(ref, null, false) as? CanvasSummary.Playable)?.state?.isFinished == true
        }
    }

    /**
     * [ref]'s neutral name while it's an unfinished mystery picture (T43), else null: what the evening reminder names
     * and draws instead of the picture. Reads only, off [writer], like the reminder's other reads (files are replaced
     * atomically, so a read never sees half a write).
     */
    fun hiddenAs(ref: CanvasRef): String? {
        if (ref.kind != CanvasKind.REMOTE) return null
        val entry = remote.listed().firstOrNull { it.id == ref.id }
        if (!repository.exists(ref)) return hiddenName(ref, null, finished = false, entry)
        return summarize(ref, null, false, entry).hiddenAs
    }

    /**
     * What an open canvas is called in the app's language now (T49): a language switch keeps the canvas's session, whose
     * [opened] title (or [hidden] mystery name) is the old language's. A photo's title is the player's own, so it stays.
     */
    fun titleNow(ref: CanvasRef, opened: String, hidden: String?): String {
        val entry = if (ref.kind == CanvasKind.REMOTE) remote.listed().firstOrNull { it.id == ref.id } else null
        if (hidden != null) return hiddenName(ref, null, finished = false, entry) ?: hidden
        return when (ref.kind) {
            CanvasKind.PHOTO -> opened
            CanvasKind.BUNDLED -> titleOf(ref, null)
            CanvasKind.REMOTE -> entry?.titleIn(words().tag) ?: opened
        }
    }

    /** Whether [ref] has a gem placed on disk (a started picture never locks again, eng E4c). */
    suspend fun isStarted(ref: CanvasRef): Boolean {
        settle(ref)
        return withContext(writer) {
            repository.exists(ref) && when (val r = repository.load(ref)) {
                is LoadResult.Loaded -> r.log.isNotEmpty()
                is LoadResult.RecoveredFromLastGood -> r.log.isNotEmpty()
                is LoadResult.Damaged -> true
            }
        }
    }

    override suspend fun rename(ref: CanvasRef, title: String): WriteResult {
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
    override suspend fun startOver(ref: CanvasRef): Boolean {
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
    override suspend fun delete(ref: CanvasRef): Boolean {
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

    override suspend fun writeThumb(ref: CanvasRef, png: ByteArray) {
        withContext(writer) { repository.writeThumb(ref, png) }
    }

    override suspend fun progress(ref: CanvasRef): CanvasProgress? {
        val opened = open(ref) as? Opened.Ready ?: return null
        if (opened.hiddenAs != null) return null
        return CanvasProgress(opened.grid.pattern, opened.state.log(), opened.title)
    }

    private suspend fun settle(ref: CanvasRef) {
        swept.join()
        closing.value[ref]?.join()
    }

    /**
     * True when this call copied the picture in, i.e. the player just started it. A remote picture's record keeps
     * its catalog title, so it still has one after it leaves the catalog or comes back from a backup (#43).
     */
    private fun ensurePicture(ref: CanvasRef): Boolean {
        if (repository.exists(ref)) return false
        val now = wallMillis()
        return when (ref.kind) {
            CanvasKind.PHOTO -> false
            CanvasKind.BUNDLED -> {
                val picture = BundledPictures.byId(ref.id) ?: return false
                repository.create(ref, bundledGrid(picture), CanvasRecord("", now, 0L)) == WriteResult.Written
            }
            CanvasKind.REMOTE -> {
                val picture = remote.listed().firstOrNull { it.id == ref.id } ?: return false
                val grid = remote.grid(ref.id) ?: return false
                repository.create(ref, grid, CanvasRecord(picture.title, now, 0L, picture.mystery)) == WriteResult.Written
            }
        }
    }

    /** [entry]: its catalog entry, when listed; a withdrawn mystery is still hidden by its record (T43). */
    private fun summarize(ref: CanvasRef, size: CanvasSize?, locked: Boolean, entry: RemotePicture? = null): CanvasSummary {
        val record = repository.readRecord(ref)
        val title = titleOf(ref, record)
        val (grid, log) = when (val r = repository.load(ref)) {
            is LoadResult.Loaded -> r.grid to r.log
            is LoadResult.RecoveredFromLastGood -> r.grid to r.log
            is LoadResult.Damaged -> return damaged(ref, title, record, size, locked, entry)
        }
        val state = CanvasState.restore(grid.pattern, log) ?: return damaged(ref, title, record, size, locked, entry)
        // The Finished section's order (#89): only a finished canvas reads its play file.
        val finishedDay = if (state.isFinished) repository.readPlay(ref)?.firstFinishedDay else null
        val hidden = hiddenName(ref, record, state.isFinished, entry)
        return CanvasSummary.Playable(ref, title, state, record?.createdAtMillis ?: 0L, size, locked, finishedDay, hidden)
    }

    /** A damaged mystery can't show it's finished, so it stays hidden (T43). */
    private fun damaged(ref: CanvasRef, title: String, record: CanvasRecord?, size: CanvasSize?, locked: Boolean, entry: RemotePicture?) =
        CanvasSummary.Damaged(
            ref,
            title,
            record?.createdAtMillis ?: 0L,
            repository.readThumb(ref),
            size,
            locked,
            hiddenName(ref, record, finished = false, entry),
        )

    /**
     * The neutral name of a mystery picture (T43) while it's unfinished, from its record (kept after a withdrawal) or
     * its catalog [entry]; null for anything else.
     */
    private fun hiddenName(ref: CanvasRef, record: CanvasRecord?, finished: Boolean, entry: RemotePicture?): String? {
        if (ref.kind != CanvasKind.REMOTE) return null
        val mystery = record?.mystery == true || entry?.mystery == true
        if (!Mystery.hidden(mystery, finished)) return null
        return Messages.mystery(words(), Mystery.name(entry?.availableFrom, LocalDate.fromEpochDays(dayClock.today())))
    }

    private fun CanvasSummary.Playable.withSize(size: CanvasSize?) =
        CanvasSummary.Playable(ref, title, state, createdAtMillis, size, locked, firstFinishedDay, hiddenAs)

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

    fun titleOf(ref: CanvasRef, record: CanvasRecord?): String =
        titleOf(ref, record, words(), if (ref.kind == CanvasKind.REMOTE) remote.listed().firstOrNull { it.id == ref.id } else null)

    companion object {

        /** How a [ReminderCanvas] names its canvas, and back (T35). */
        fun reminderKey(ref: CanvasRef): String = Canvases.reminderKey(ref)

        fun refOfReminderKey(key: String): CanvasRef? {
            val kind = CanvasKind.entries.firstOrNull { key.startsWith("${it.name}/") } ?: return null
            val id = key.substringAfter('/')
            return if (CanvasRef.ID.matches(id)) CanvasRef(kind, id) else null
        }

        /**
         * A canvas's shown title, in the app's language (T49 X4): a bundled picture's `title_<id>`; a remote one's from
         * the catalog ([listed], through the language chain), else its record's English title (a withdrawn picture).
         */
        fun titleOf(ref: CanvasRef, record: CanvasRecord?, words: Words, listed: RemotePicture? = null): String = when (ref.kind) {
            CanvasKind.BUNDLED -> BundledPictures.byId(ref.id)?.title(words) ?: ref.id
            CanvasKind.REMOTE -> listed?.titleIn(words.tag) ?: record?.title?.takeIf { it.isNotBlank() } ?: ref.id
            CanvasKind.PHOTO -> record?.title?.takeIf { it.isNotBlank() } ?: words.text("photo_default_title")
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
