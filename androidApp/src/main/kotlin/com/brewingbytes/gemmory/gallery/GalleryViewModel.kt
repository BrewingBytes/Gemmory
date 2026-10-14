package com.brewingbytes.gemmory.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.photo.PhotoTitles
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
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
) {
    /** Mono "42%" or "Done"; never "100%" before the last gem. */
    val progressLabel: String get() = if (done) "Done" else "$percent%"
}

data class GalleryState(val loaded: Boolean = false, val pictures: List<Tile> = emptyList(), val photos: List<Tile> = emptyList())

class GalleryViewModel(application: Application, private val handle: SavedStateHandle) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store

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

    fun refresh() {
        viewModelScope.launch {
            val (pictures, photos) = store.summaries()
            val tiles = withContext(Dispatchers.Default) { pictures.map { tile(it) } to photos.map { tile(it) } }
            _state.value = GalleryState(loaded = true, pictures = tiles.first, photos = tiles.second)
        }
    }

    fun rename(ref: CanvasRef, input: String) {
        val title = PhotoTitles.cleaned(input) ?: return
        viewModelScope.launch {
            store.rename(ref, title)
            refresh()
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
