package com.brewingbytes.gemmory.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.bundled.TodayOption
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.photo.PhotoTitles
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.share.ProgressSharing
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.timelapse.ProgressShare
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    /** Mono "42%" or "Done"; never "100%" before the last gem. */
    val progressLabel: String get() = if (done) "Done" else "$percent%"
}

/**
 * Pictures tab order (#58): every picture a tap opens (the starter, the free ones, unlocked or started ones, today's
 * free picture (#34), all of them for VIP), then the locked ones, each group keeping the catalog's Quick, Medium, Big
 * ramp (#31). "Opens" is [Tile.locked], the rule the tap follows.
 */
internal fun picturesInOrder(pictures: List<Tile>, unlocked: Set<String>, entitlements: Entitlements, todayFree: String? = null): List<Tile> =
    pictures.sortedBy { it.locked(unlocked, entitlements, todayFree) }

/**
 * The Today's picture card (#34) above the Pictures grid. [preview] is the finished picture until a gem is placed,
 * then the tile's real progress. [free]: a free player's pick not started yet ("free today"); [vip]: "Today's pick".
 */
data class TodayCard(val tile: Tile, val preview: ImageBitmap?, val size: String, val vip: Boolean, val free: Boolean)

/**
 * [streak]: days in a row with a picture finished (#33), as of this visit; 0 when broken or never played. [today]:
 * today's picture (#34), null when there's nothing to pick.
 */
data class GalleryState(
    val loaded: Boolean = false,
    val pictures: List<Tile> = emptyList(),
    val photos: List<Tile> = emptyList(),
    val streak: Int = 0,
    val today: TodayCard? = null,
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

    /**
     * Re-reads every canvas, each time the Gallery shows. The Pictures order is set here, from the unlocks and
     * entitlements of this moment, so a picture unlocked by a video moves up on the next visit, never under the
     * player's finger (#58). The locked labels follow them live.
     */
    fun refresh() {
        // Remote pictures (#43): a background check when one is due. What it brings shows on the next visit.
        app.remote.checkIfDue()
        viewModelScope.launch {
            val (pictures, photos) = store.summaries()
            val tiles = withContext(Dispatchers.Default) { pictures.map { tile(it) } to photos.map { tile(it) } }
            val monetization = app.monetization
            val unlocked = monetization.unlocks.unlocked.value
            val entitlements = monetization.entitlements.value
            val today = todayCard(pictures, tiles.first, unlocked, entitlements.vip)
            val todayFree = today?.takeIf { !it.vip }?.tile?.ref?.id
            val ordered = picturesInOrder(tiles.first, unlocked, entitlements, todayFree)
            _state.value = GalleryState(loaded = true, pictures = ordered, photos = tiles.second, streak = app.streak.current(), today = today)
        }
    }

    /** A tile was tapped: a remote picture's "New" label goes (#43), whether it opens or shows its locked sheet. */
    fun opened(tile: Tile) {
        if (tile.ref.kind == CanvasKind.REMOTE && tile.isNew) app.remote.opened(tile.ref.id)
    }

    /** Whether [tile] is today's free picture right now (#34), read live so a gallery left open past midnight can't grant it. */
    fun isFreeToday(tile: Tile): Boolean = app.todays.isFreeToday(tile.ref)

    /** Today's picture (#34): picked on the day's first visit, then the same one all day. */
    private suspend fun todayCard(summaries: List<CanvasSummary>, tiles: List<Tile>, unlocked: Set<String>, vip: Boolean): TodayCard? {
        val started = tiles.filter { it.started }.map { it.ref.id }.toSet()
        val options = tiles.filter { it.size != null }.map { TodayOption(it.ref.id, it.lockable) }
        val newIds = tiles.filter { it.isNew }.map { it.ref.id }.toSet()
        val id = withContext(Dispatchers.IO) { app.todays.pick(started, vip, options) { it in newIds } } ?: return null
        val tile = tiles.firstOrNull { it.ref.id == id && !it.damaged } ?: return null
        val size = tile.size?.label ?: return null
        val preview = if (tile.started) {
            tile.image
        } else {
            val pattern = (summaries.firstOrNull { it.ref == tile.ref } as? CanvasSummary.Playable)?.state?.pattern
            pattern?.let { withContext(Dispatchers.Default) { GemRenderer.render(it, cellPxFor(it.width)).asImageBitmap() } } ?: tile.image
        }
        return TodayCard(tile, preview, size, vip, free = !vip && !tile.started && id !in unlocked)
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
            )
        }
    }

    private fun Bitmap.toPng(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private companion object {
        /** Tiles are at most ~200dp wide; about 240 px of gems is sharp enough without costing memory. */
        fun cellPxFor(width: Int): Int = (240 / width).coerceIn(2, 10)

        const val KEY_TAB = "tab"
    }
}
