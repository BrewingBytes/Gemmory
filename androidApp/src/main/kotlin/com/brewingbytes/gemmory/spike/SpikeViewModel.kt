package com.brewingbytes.gemmory.spike

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.converter.convert
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class SpikeStep { START, CROP, SIZE, PREVIEW }

sealed interface Conversion {
    data object Running : Conversion
    data class Ready(val pattern: Pattern, val preview: Bitmap) : Conversion
    data object Failed : Conversion
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Saving : ExportStatus
    data class Saved(val name: String) : ExportStatus
    data object Failed : ExportStatus
}

data class SpikeState(
    val step: SpikeStep = SpikeStep.START,
    val decodeFailed: Boolean = false,
    val source: Bitmap? = null,
    val ratio: AspectRatio = AspectRatio.SQUARE,
    val cropped: Bitmap? = null,
    val size: CanvasSize = CanvasSize.MEDIUM,
    val conversions: Map<CanvasSize, Conversion> = emptyMap(),
    val export: ExportStatus = ExportStatus.Idle,
    /** Temporary entry point until #13: saved photo canvases, newest first, and the Play action's state. */
    val savedCanvases: List<String> = emptyList(),
    val creatingCanvas: Boolean = false,
    val createFailed: Boolean = false,
    /** A canvas just created by Play, for the screen to open once. */
    val created: String? = null,
)

/**
 * Spike flow only: pick → crop → size → preview → before/after PNG. Everything stays in memory;
 * the only file written is the PNG the user exports to their own Pictures folder.
 */
class SpikeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SpikeState())
    val state: StateFlow<SpikeState> = _state.asStateFlow()
    private var conversionJobs: List<Job> = emptyList()
    private val store = (application as GemmoryApp).store

    fun refreshSavedCanvases() {
        viewModelScope.launch {
            val ids = store.list(CanvasKind.PHOTO).sortedDescending()
            _state.update { it.copy(savedCanvases = ids) }
        }
    }

    /** Play: saves the previewed grid as a new photo canvas (GridCodec.encode → CanvasRepository.create). Single-flight. */
    fun play() {
        val s = _state.value
        val ready = s.conversions[s.size] as? Conversion.Ready ?: return
        if (s.creatingCanvas) return
        _state.update { it.copy(creatingCanvas = true, createFailed = false) }
        viewModelScope.launch {
            val id = SpikeCanvases.newId()
            val result = store.create(CanvasRef(CanvasKind.PHOTO, id), ready.pattern)
            _state.update {
                if (result == WriteResult.Written) {
                    SpikeState(created = id, savedCanvases = listOf(id) + it.savedCanvases)
                } else {
                    it.copy(creatingCanvas = false, createFailed = true)
                }
            }
        }
    }

    fun onCreatedOpened() = _state.update { it.copy(created = null) }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { decode(uri) }.getOrNull() }
            if (bitmap == null) {
                _state.update { it.copy(step = SpikeStep.START, decodeFailed = true) }
                return@launch
            }
            val ratio = when {
                bitmap.width > bitmap.height -> AspectRatio.LANDSCAPE
                bitmap.height > bitmap.width -> AspectRatio.PORTRAIT
                else -> AspectRatio.SQUARE
            }
            _state.update { SpikeState(step = SpikeStep.CROP, source = bitmap, ratio = ratio) }
        }
    }

    fun onRatio(ratio: AspectRatio) = _state.update { it.copy(ratio = ratio) }

    /** [crop] is in source-bitmap pixels. Starts all three sizes so the picker can show them side by side. */
    fun onCropped(crop: Rect) {
        val source = _state.value.source ?: return
        val r = Rect(crop)
        if (!r.intersect(0, 0, source.width, source.height) || r.isEmpty) return
        val cropped = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
        conversionJobs.forEach { it.cancel() }
        _state.update {
            it.copy(
                step = SpikeStep.SIZE,
                cropped = cropped,
                conversions = CanvasSize.entries.associateWith { Conversion.Running },
                export = ExportStatus.Idle,
            )
        }
        val ratio = _state.value.ratio
        conversionJobs = CanvasSize.entries.map { size ->
            viewModelScope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        val pixels = IntArray(cropped.width * cropped.height)
                        cropped.getPixels(pixels, 0, cropped.width, 0, 0, cropped.width, cropped.height)
                        val pattern = convert(pixels, cropped.width, cropped.height, GridSpec.of(size, ratio), SEED)
                        Conversion.Ready(pattern, GemRenderer.render(pattern, cellPx = PREVIEW_CELL_PX))
                    }.getOrElse { Conversion.Failed }
                }
                _state.update { it.copy(conversions = it.conversions + (size to result)) }
            }
        }
    }

    fun onSize(size: CanvasSize) = _state.update { it.copy(size = size) }

    fun toPreview() = _state.update { it.copy(step = SpikeStep.PREVIEW, export = ExportStatus.Idle) }

    fun back() = _state.update {
        when (it.step) {
            SpikeStep.START -> it
            SpikeStep.CROP -> SpikeState()
            SpikeStep.SIZE -> it.copy(step = SpikeStep.CROP)
            SpikeStep.PREVIEW -> it.copy(step = SpikeStep.SIZE)
        }
    }

    fun export() {
        val s = _state.value
        val photo = s.cropped ?: return
        val ready = s.conversions[s.size] as? Conversion.Ready ?: return
        if (s.export == ExportStatus.Saving) return
        _state.update { it.copy(export = ExportStatus.Saving) }
        viewModelScope.launch {
            val spec = GridSpec.of(s.size, s.ratio)
            val name = "gemmory-${s.size.name.lowercase()}-${s.ratio.width}x${s.ratio.height}-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"
            val status = withContext(Dispatchers.IO) {
                runCatching {
                    val caption = "Gemmory · ${s.size.label} · ${spec.width}×${spec.height} · " +
                        "${ready.pattern.palette.size} colors"
                    val image = GemRenderer.beforeAfter(getApplication(), photo, ready.pattern, caption)
                    try {
                        GemRenderer.saveToPictures(getApplication(), image, name)
                    } finally {
                        image.recycle()
                    }
                    ExportStatus.Saved(name)
                }.getOrElse { ExportStatus.Failed }
            }
            _state.update { it.copy(export = status) }
        }
    }

    /** Decodes straight to ≤1024 px on the long edge (eng finding 14); never at full resolution. */
    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(getApplication<Application>().contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_SOURCE_PX) {
                val scale = MAX_SOURCE_PX.toDouble() / longEdge
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).roundToInt()),
                    max(1, (info.size.height * scale).roundToInt()),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private companion object {
        const val MAX_SOURCE_PX = 1024
        const val PREVIEW_CELL_PX = 8
        /** Fixed so the same photo and crop always give the same canvas. */
        const val SEED = 0x47_4D_52_59L
    }
}
