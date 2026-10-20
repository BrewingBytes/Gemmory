package com.brewingbytes.gemmory.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.collections.CollectionCopy
import com.brewingbytes.gemmory.collections.Keepsake
import com.brewingbytes.gemmory.collections.Settled
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.game.Frost
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.photo.PhotoTitles
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.remote.CollectionRules
import com.brewingbytes.gemmory.share.ProgressSharing
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.CanvasEntry
import com.brewingbytes.gemmory.timelapse.ProgressShare
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** One gallery tile (design system, Gallery tile). [aspect] is width / height, honoring 1:1, 3:4 and 4:3. */
data class Tile(
    val ref: CanvasRef,
    val title: String,
    val aspect: Float,
    val image: ImageBitmap?,
    val percent: Int,
    val done: Boolean,
    val damaged: Boolean,
    /** At least one gem placed: a started picture never locks again (eng E4c). */
    val started: Boolean = false,
    /** Unfinished and past the minimum, so its ⋮ menu offers Share progress (#36). */
    val shareable: Boolean = false,
    /** The picture's catalog lock flag (bundled `FREE`, or a remote picture's `locked`), before unlocks and VIP. */
    val lockable: Boolean = false,
    /** A remote picture that arrived in the last 7 days and hasn't been opened (#43): the "New" label. */
    val isNew: Boolean = false,
    /** Quick / Medium / Big; null for the starter and photo canvases. */
    val size: CanvasSize? = null,
    /**
     * An unstarted remote picture summarized from its catalog entry (T36, eng D12): [image] is null and the tile
     * draws its thumbnail lazily ([GalleryViewModel.thumbnail]) once it's on screen.
     */
    val lazyImage: Boolean = false,
    /** The local epoch day it was first finished (T35 `play` file, #89); null if unfinished or finished before T35. */
    val finishedDay: Long? = null,
    /** When its canvas was made (0 for a picture never opened): the Finished section's tie-break (#89). */
    val createdAtMillis: Long = 0L,
) {
    /** Mono "42%" or "Done"; never "100%" before the last gem. */
    val progressLabel: String get() = if (done) "Done" else "$percent%"
}

/**
 * Pictures tab order (#58): every picture a tap opens (the starter, the free ones, unlocked or started ones, today's
 * free picture (#34), all of them for VIP), then the locked ones, each group keeping the catalog's Quick, Medium, Big
 * ramp (#31). "Opens" is [Tile.locked], the rule the tap follows.
 */
internal fun picturesInOrder(pictures: List<Tile>, unlocked: Set<String>, entitlements: Entitlements, todayFree: Set<String> = emptySet()): List<Tile> =
    pictures.sortedBy { it.locked(unlocked, entitlements, todayFree) }

/**
 * The Finished section (#89) at the end of each tab: the finished canvases, newest finish first. Those finished before
 * T35 have no date and go after the dated ones; a tie on the day, and the undated group, go newest created first.
 */
internal fun finishedInOrder(tiles: List<Tile>): List<Tile> =
    tiles.filter { it.done }.sortedWith(
        compareBy<Tile> { it.finishedDay == null }
            .thenByDescending { it.finishedDay ?: 0L }
            .thenByDescending { it.createdAtMillis },
    )

/**
 * The Today's picture card (#34) above the Pictures grid. [preview] is the finished picture until a gem is placed,
 * then the tile's real progress. [free]: a free player's pick not started yet ("free today"); [vip]: "Today's pick".
 * [drop]: it's today's drop (T36), which the card shows once it's here, with the "New today" chip.
 */
data class TodayCard(val tile: Tile, val preview: ImageBitmap?, val size: String, val vip: Boolean, val free: Boolean, val drop: Boolean = false)

/**
 * One collection row (T37, design system "Collection and event rows"): [members] are the strip's tiles in the
 * collection's order, the keepsake last once it's in; [coming] the "more coming" well. [glint]: complete (or an
 * event's keepsake earned, T38) and its glint hasn't played. [keepsakeTitle] is set once the keepsake is in, for the announcement. [inWindow]: a weekly event in
 * its window (T38), opened with the event entry; [earned]: its keepsake earned with 3 finishes in the window.
 */
data class CollectionRow(
    val id: String,
    val title: String,
    val members: List<Tile>,
    val finished: Int,
    val size: Int,
    val coming: Int,
    val line: String,
    val complete: Boolean,
    val glint: Boolean,
    val keepsakeTitle: String? = null,
    val inWindow: Boolean = false,
    val earned: Boolean = false,
) {
    /** A thumbnail's `entry` (T36-T38): the event during its window, then a collection like any other. */
    val entry: CanvasEntry get() = if (inWindow) CanvasEntry.EVENT else CanvasEntry.COLLECTION
}

/**
 * The gallery's collections (T37): [rows] above the grid ([rowsLarge] above 1.5× font, D4), whether the "All
 * collections" link shows with each, and [all] for its sheet, newest first, complete ones last.
 */
data class CollectionsUi(
    val rows: List<CollectionRow> = emptyList(),
    val showAll: Boolean = false,
    val rowsLarge: List<CollectionRow> = emptyList(),
    val showAllLarge: Boolean = false,
    val all: List<CollectionRow> = emptyList(),
)

/**
 * [streak]: days in a row with a picture finished (#33), as of this visit; 0 when broken or never played. [today]:
 * today's picture (#34), null when there's nothing to pick. [todayFree]: what opens without a video today, today's
 * free picture and today's drop (T36). [tomorrow]: the frost of tomorrow's drop, stored and verified (T36), for the
 * Tomorrow line; null when there's none. [pictures] and [photos] are what's left to play; [finishedPictures] and
 * [finishedPhotos] are each tab's Finished section (#89). [collections]: the collection rows and sheet (T37).
 */
data class GalleryState(
    val loaded: Boolean = false,
    val pictures: List<Tile> = emptyList(),
    val photos: List<Tile> = emptyList(),
    val finishedPictures: List<Tile> = emptyList(),
    val finishedPhotos: List<Tile> = emptyList(),
    val streak: Int = 0,
    val today: TodayCard? = null,
    val todayFree: Set<String> = emptySet(),
    val tomorrow: ImageBitmap? = null,
    val collections: CollectionsUi = CollectionsUi(),
)

class GalleryViewModel(application: Application, private val handle: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as GemmoryApp
    private val store = app.store

    /** 0 = Pictures, 1 = My Photos. Kept here so it survives a trip to a canvas, rotation and process death. */
    var tab by mutableIntStateOf(handle.get<Int>(KEY_TAB) ?: 0)
        private set

    fun selectTab(index: Int) {
        tab = index
        handle[KEY_TAB] = index
    }
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    /** Placed count each thumbnail file was written for, so a gallery visit rewrites only changed ones. */
    private val thumbsWritten = mutableMapOf<CanvasRef, Int>()

    /** Lazy thumbnails of unstarted remote pictures (T36, eng D12), the ones scrolled past most recently. */
    private val lazyThumbs = LruCache<CanvasRef, ImageBitmap>(LAZY_THUMBS)

    init {
        // A background check that brought something new (T36): reload now, not on the next visit.
        viewModelScope.launch { app.remote.updates.drop(1).collect { refresh() } }
    }

    /** Millis to the next local midnight, when the gallery reloads for the day's drop (T36). */
    fun millisUntilNextDay(): Long = app.dayClock.millisUntilNextDay()

    /** The local day the last [refresh] read, so a return after midnight reloads (T36). */
    @Volatile
    private var loadedDay: Long? = null

    /**
     * The Gallery came back to the foreground (the phone unlocked, the app reopened): a new local day since the last
     * load reloads it, so yesterday's drop, its "free today" and the Tomorrow line don't linger (T36). The daily
     * catalog check runs either way when it's due.
     */
    fun resumed() {
        if (loadedDay != null && loadedDay != app.dayClock.today()) refresh() else app.remote.checkIfDue()
    }

    /**
     * Re-reads every canvas, each time the Gallery shows. The Pictures order is set here, from the unlocks and
     * entitlements of this moment, so a picture unlocked by a video moves up on the next visit, never under the
     * player's finger (#58). The locked labels follow them live. A finished canvas moves into its tab's Finished
     * section (#89) the same way, and back out after Play again.
     */
    fun refresh() {
        // Remote pictures (#43): a background check when one is due. What it brings shows on the next visit.
        app.remote.checkIfDue()
        loadedDay = app.dayClock.today()
        viewModelScope.launch {
            var (pictures, photos) = store.summaries()
            // Collections (T37): a completion grants its keepsake and token here; a keepsake copied in now joins the grid.
            val settled = app.collections.settle(pictures)
            val ids = pictures.map { it.ref.id }.toSet()
            if (settled.statuses.any { it.keepsakeState == Keepsake.ADDED && it.keepsake?.id !in ids }) {
                val again = store.summaries()
                pictures = again.first
                photos = again.second
            }
            val tiles = withContext(Dispatchers.Default) { pictures.map { tile(it) } to photos.map { tile(it) } }
            val monetization = app.monetization
            val unlocked = monetization.unlocks.unlocked.value
            val entitlements = monetization.entitlements.value
            val today = todayCard(pictures, tiles.first, unlocked, entitlements.vip)
            val todayFree = if (entitlements.vip) emptySet() else app.todays.freeToday()
            val ordered = picturesInOrder(tiles.first.filterNot { it.done }, unlocked, entitlements, todayFree)
            val tomorrow = withContext(Dispatchers.IO) { tomorrowFrost() }
            _state.value = GalleryState(
                loaded = true,
                pictures = ordered,
                photos = tiles.second.filterNot { it.done },
                finishedPictures = finishedInOrder(tiles.first),
                finishedPhotos = finishedInOrder(tiles.second),
                streak = app.streak.current(),
                today = today,
                todayFree = todayFree,
                tomorrow = tomorrow,
                collections = collectionsUi(settled, tiles.first, tiles.second, entitlements.vip),
            )
        }
    }

    /** The rows, the link and the sheet (T37) from a settle and this visit's tiles; none before the first finish (D5). */
    private fun collectionsUi(settled: Settled, pictures: List<Tile>, photos: List<Tile>, vip: Boolean): CollectionsUi {
        if (!settled.fetched || settled.statuses.isEmpty()) return CollectionsUi()
        val byId = pictures.associateBy { it.ref.id }
        val rows = settled.statuses.associate { status ->
            val members = status.progress.shown.mapNotNull { byId[it] } +
                listOfNotNull(status.keepsake?.takeIf { status.keepsakeState == Keepsake.ADDED }?.let { byId[it.id] })
            status.id to CollectionRow(
                id = status.id,
                title = status.collection.title,
                members = members,
                finished = status.progress.finished,
                size = status.progress.size,
                coming = status.progress.coming,
                line = CollectionCopy.line(status, vip),
                complete = status.complete,
                glint = status.glintPending,
                keepsakeTitle = status.keepsake?.title?.takeIf { status.keepsakeState == Keepsake.ADDED },
                inWindow = status.inWindow,
                earned = status.earned,
            )
        }
        val progress = settled.statuses.map { it.progress }
        val completed = settled.statuses.mapNotNull { s -> s.completedDay?.let { s.id to it } }.toMap()
        val glints = settled.statuses.filter { it.glintPending }.map { it.id }.toSet()
        val anyFinished = pictures.any { it.done } || photos.any { it.done }
        val today = app.dayClock.today()
        val inWindow = settled.statuses.filter { it.inWindow }.map { it.id }.toSet()
        val pick = CollectionRules.rows(progress, completed, glints, today, anyFinished, largeFont = false, inWindow)
        val large = CollectionRules.rows(progress, completed, glints, today, anyFinished, largeFont = true, inWindow)
        val all = if (anyFinished) CollectionRules.sheet(progress, completed.keys).mapNotNull { rows[it] } else emptyList()
        return CollectionsUi(pick.rows.mapNotNull { rows[it] }, pick.showAll, large.rows.mapNotNull { rows[it] }, large.showAll, all)
    }

    /** A completed collection's glint played (or showed at once under reduced motion): never again (T37). */
    fun glintShown(id: String) {
        app.collections.glintShown(id)
        _state.value = _state.value.let { s ->
            val off = { r: CollectionRow -> if (r.id == id) r.copy(glint = false) else r }
            val c = s.collections
            s.copy(collections = c.copy(rows = c.rows.map(off), rowsLarge = c.rowsLarge.map(off), all = c.all.map(off)))
        }
    }

    /**
     * An unstarted remote picture's thumbnail (T36, eng D12), decoded and drawn when its tile first shows, then
     * cached. Null when its grid can't be read (it may have been withdrawn since the visit began).
     */
    suspend fun thumbnail(tile: Tile): ImageBitmap? {
        lazyThumbs.get(tile.ref)?.let { return it }
        val pattern = withContext(Dispatchers.IO) { store.pattern(tile.ref) } ?: return null
        val image = withContext(Dispatchers.Default) { GemRenderer.render(pattern, cellPxFor(pattern.width)).asImageBitmap() }
        lazyThumbs.put(tile.ref, image)
        return image
    }

    /** The Tomorrow line's frost (T36): tomorrow's drop drawn as the frost, square; the UI lays `felt-deep` on it. */
    private fun tomorrowFrost(): ImageBitmap? {
        val bytes = app.remote.tomorrowGrid() ?: return null
        val pattern = (GridCodec.decode(bytes) as? Decoded.Ok)?.value?.pattern ?: return null
        val pixels = Frost.image(pattern, FROST_PX, FROST_PX)
        return Bitmap.createBitmap(pixels, FROST_PX, FROST_PX, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** A tile was tapped: a remote picture's "New" label goes (#43), whether it opens or shows its locked sheet. */
    fun opened(tile: Tile) {
        if (tile.ref.kind == CanvasKind.REMOTE && tile.isNew) app.remote.opened(tile.ref.id)
    }

    /** Whether the Finished section (#89) of [tab] is expanded, remembered across visits and backed up. */
    fun finishedOpen(tab: Int): Boolean = app.settings.finishedOpen(tab)

    fun setFinishedOpen(tab: Int, open: Boolean) = app.settings.setFinishedOpen(tab, open)

    /** The Tomorrow line was tapped (T36): "Opens tomorrow", and Analytics hears of it. */
    fun teaserTapped() = app.analytics.log(AnalyticsEvent.teaserTapped)

    /**
     * Whether [tile] is today's free picture or today's drop right now (#34, T36), read live so a gallery left open past
     * midnight can't grant it.
     */
    fun isFreeToday(tile: Tile): Boolean = app.todays.isFreeToday(tile.ref)

    /**
     * Today's picture (#34): picked on the day's first visit, then the same one all day. Today's drop (T36) takes the
     * card once it's here, even after the pick was made (eng D9); the pick stays free today, in the grid.
     */
    private suspend fun todayCard(summaries: List<CanvasSummary>, tiles: List<Tile>, unlocked: Set<String>, vip: Boolean): TodayCard? {
        // The same choices the evening reminder reads (T35, eng D3), so the card and the notification agree.
        val started = tiles.filter { it.started }.map { it.ref.id }.toSet()
        val picked = withContext(Dispatchers.IO) {
            val remote = app.remote
            app.todays.pick(started, vip, remote.listed(), remote.drops(), remote::isNew)
        }
        val todays = withContext(Dispatchers.IO) { app.remote.todaysDrops() }
        val drop = tiles.firstOrNull { it.ref.kind == CanvasKind.REMOTE && it.ref.id in todays && !it.damaged }
        val tile = drop ?: tiles.firstOrNull { it.ref.id == picked && !it.damaged } ?: return null
        val size = tile.size?.label ?: return null
        val preview = if (tile.started) {
            tile.image
        } else {
            // A remote picture never opened is only its catalog entry (T36), so its grid is read for the preview.
            val pattern = (summaries.firstOrNull { it.ref == tile.ref } as? CanvasSummary.Playable)?.state?.pattern
                ?: withContext(Dispatchers.IO) { store.pattern(tile.ref) }
            pattern?.let { withContext(Dispatchers.Default) { GemRenderer.render(it, cellPxFor(it.width)).asImageBitmap() } } ?: tile.image
        }
        return TodayCard(tile, preview, size, vip, free = !vip && !tile.started && tile.ref.id !in unlocked, drop = drop != null)
    }


    fun rename(ref: CanvasRef, input: String) {
        val title = PhotoTitles.cleaned(input) ?: return
        viewModelScope.launch {
            store.rename(ref, title)
            refresh()
        }
    }

    /** Share progress (#36) from a tile's ⋮ menu; kept here so a rotation keeps its sheet. */
    val sharing = ProgressSharing(app)

    /** Share progress tap: the canvas's saved log so far, read fresh (a canvas just left may still be writing). */
    fun shareProgress(ref: CanvasRef) {
        viewModelScope.launch {
            val opened = store.open(ref) as? CanvasStore.Opened.Ready ?: return@launch
            sharing.share(ref, opened.grid.pattern, opened.state.log(), opened.title)
        }
    }

    fun delete(ref: CanvasRef) {
        viewModelScope.launch {
            store.delete(ref)
            thumbsWritten.remove(ref)
            refresh()
        }
    }

    fun startOver(ref: CanvasRef) {
        viewModelScope.launch {
            store.startOver(ref)
            thumbsWritten.remove(ref)
            refresh()
        }
    }

    private suspend fun tile(summary: CanvasSummary): Tile = when (summary) {
        is CanvasSummary.Listed -> Tile(
            ref = summary.ref,
            title = summary.title,
            aspect = summary.width / summary.height.toFloat(),
            image = lazyThumbs.get(summary.ref),
            percent = 0,
            done = false,
            damaged = false,
            lockable = summary.locked,
            isNew = app.remote.isNew(summary.ref.id),
            size = summary.size,
            lazyImage = true,
        )
        is CanvasSummary.Damaged -> {
            val thumb = summary.thumb?.let { f -> runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() }
            Tile(
                ref = summary.ref,
                title = summary.title,
                aspect = thumb?.let { it.width / it.height.toFloat() } ?: (3f / 4f),
                image = thumb?.asImageBitmap(),
                percent = 0,
                done = false,
                damaged = true,
                lockable = summary.locked,
                size = summary.size,
            )
        }
        is CanvasSummary.Playable -> {
            val state = summary.state
            val pattern = state.pattern
            val bitmap = GemRenderer.render(pattern, cellPxFor(pattern.width), state::isPlaced)
            // Kept for the "Can't open" tile (design D9); only canvases that exist on disk get one.
            val onDisk = summary.ref.kind == CanvasKind.PHOTO || state.placedCount > 0
            if (onDisk && thumbsWritten[summary.ref] != state.placedCount) {
                store.writeThumb(summary.ref, bitmap.toPng())
                thumbsWritten[summary.ref] = state.placedCount
            }
            Tile(
                ref = summary.ref,
                title = summary.title,
                aspect = pattern.width / pattern.height.toFloat(),
                image = bitmap.asImageBitmap(),
                percent = (state.placedCount * 100L / state.cellCount).toInt(),
                done = state.isFinished,
                damaged = false,
                started = state.placedCount > 0,
                shareable = ProgressShare.isOffered(state.placedCount, state.cellCount),
                lockable = summary.locked,
                isNew = summary.ref.kind == CanvasKind.REMOTE && app.remote.isNew(summary.ref.id),
                size = summary.size,
                finishedDay = summary.firstFinishedDay,
                createdAtMillis = summary.createdAtMillis,
            )
        }
    }

    private fun Bitmap.toPng(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private companion object {
        /** Tiles are at most ~200dp wide; about 240 px of gems is sharp enough without costing memory. */
        fun cellPxFor(width: Int): Int = (240 / width).coerceIn(2, 10)

        const val KEY_TAB = "tab"

        /** About two screens of tiles; each is ~240 px square or less. */
        const val LAZY_THUMBS = 48

        /** The Tomorrow line's 40dp frost at up to 3× density. */
        const val FROST_PX = 120
    }
}
