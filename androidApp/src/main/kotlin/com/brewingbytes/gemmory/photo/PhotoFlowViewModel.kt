package com.brewingbytes.gemmory.photo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.FaceCheck
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.converter.PhotoFlowRules
import com.brewingbytes.gemmory.converter.SizeRules
import com.brewingbytes.gemmory.converter.convert
import com.brewingbytes.gemmory.policy.ConsumeResult
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface Conversion {
    data object Running : Conversion

    data class Ready(val pattern: Pattern, val preview: Bitmap) : Conversion

    data object Failed : Conversion
}

/** Pinch and drag inside the crop frame: [zoom] ≥ 1 over "cover", [centerX]/[centerY] in source-photo fractions. */
data class CropView(val zoom: Float = 1f, val centerX: Float = 0.5f, val centerY: Float = 0.5f)

/** One-shot navigation the photo flow asks for. */
sealed interface PhotoNav {
    data object OpenCrop : PhotoNav

    data class Started(val ref: CanvasRef) : PhotoNav

    /** The draft is gone (e.g. the cache was cleared while the app was dead): back to the gallery. */
    data object Lost : PhotoNav
}

data class PhotoFlowState(
    val decoding: Boolean = false,
    /** "This photo couldn't be opened. Try another one." on the gallery (design D4). */
    val decodeFailed: Boolean = false,
    val source: Bitmap? = null,
    val ratio: AspectRatio = AspectRatio.SQUARE,
    val crop: CropView = CropView(),
    val cropped: Bitmap? = null,
    val size: CanvasSize = CanvasSize.MEDIUM,
    val pickedByPlayer: Boolean = false,
    val faceCheck: FaceCheck = FaceCheck.RUNNING,
    val conversions: Map<CanvasSize, Conversion> = emptyMap(),
    val starting: Boolean = false,
    val startFailed: Boolean = false,
    val nav: PhotoNav? = null,
) {
    val rules: SizeRules get() = PhotoFlowRules.sizeRules(faceCheck)

    /** Start waits for the face check, so the face rule always applies to the size it starts (design D4). */
    val canStart: Boolean get() = PhotoFlowRules.canStart(faceCheck, size, converted = conversions[size] is Conversion.Ready)
}

/**
 * Photo Picker → Crop → Size → Preview → Start (design D4). Small state lives in [SavedStateHandle]; the decoded
 * photo (≤ 1024 px) is kept as a draft in `cacheDir/photo-draft/`, which is never backed up, so the flow survives
 * rotation and process death. Nothing leaves the phone; on Start the cropped photo moves into the canvas directory
 * the backup rules exclude, and the draft is deleted.
 */
class PhotoFlowViewModel(application: Application, private val handle: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as GemmoryApp
    private val _state = MutableStateFlow(PhotoFlowState())
    val state: StateFlow<PhotoFlowState> = _state.asStateFlow()
    private var work: List<Job> = emptyList()
    private val draftDir = File(application.cacheDir, DRAFT_DIR)

    /** False once the flow's draft is gone; a photo-flow screen with no draft goes back to the gallery. */
    val hasDraft: Boolean get() = handle.contains(KEY_DRAFT) || _state.value.decoding

    init {
        restoreDraft()
    }

    fun onPhotoPicked(uri: Uri) {
        clearDraft()
        _state.value = PhotoFlowState(decoding = true)
        viewModelScope.launch {
            val id = "photo-${System.currentTimeMillis()}"
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decode(uri) }.getOrNull()?.also { saveDraft(id, it) }
            }
            if (bitmap == null) {
                _state.value = PhotoFlowState(decodeFailed = true)
                return@launch
            }
            FaceDetector.warmUp()
            val ratio = PhotoFlowRules.defaultRatio(bitmap.width, bitmap.height)
            handle[KEY_DRAFT] = id
            handle[KEY_RATIO] = ratio.name
            _state.value = PhotoFlowState(source = bitmap, ratio = ratio, nav = PhotoNav.OpenCrop)
        }
    }

    fun onNavHandled() = _state.update { it.copy(nav = null) }

    fun onRatio(ratio: AspectRatio) {
        handle[KEY_RATIO] = ratio.name
        handle.remove<FloatArray>(KEY_CROP_VIEW)
        _state.update { it.copy(ratio = ratio, crop = CropView()) }
    }

    fun onCropView(view: CropView) {
        handle[KEY_CROP_VIEW] = floatArrayOf(view.zoom, view.centerX, view.centerY)
        _state.update { it.copy(crop = view) }
    }

    /** [rect] is in source-photo pixels. Starts the face check and all three sizes for the size picker. */
    fun onCropNext(rect: Rect) {
        handle[KEY_CROP_RECT] = intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
        handle[KEY_SIZE] = CanvasSize.MEDIUM.name
        handle[KEY_PICKED] = false
        cropAndConvert(rect, CanvasSize.MEDIUM, pickedByPlayer = false)
    }

    fun onSize(size: CanvasSize) {
        if (size in _state.value.rules.disabled) return
        handle[KEY_SIZE] = size.name
        handle[KEY_PICKED] = true
        _state.update { it.copy(size = size, pickedByPlayer = true) }
    }

    /**
     * Start (design D4, eng E4a): single-flight, and the canvas id is the draft's, so a repeat makes one canvas and
     * uses one allowance (`DailyLedger.consume(draftId)` is idempotent). A canvas that can't be written gives the
     * allowance back.
     */
    fun start() {
        val s = _state.value
        val ready = s.conversions[s.size] as? Conversion.Ready ?: return
        val photo = s.cropped ?: return
        val id = handle.get<String>(KEY_DRAFT) ?: return
        if (!s.canStart || s.starting) return
        _state.update { it.copy(starting = true, startFailed = false) }
        viewModelScope.launch {
            if (app.monetization.consume(id) == ConsumeResult.NoneLeft) {
                _state.update { it.copy(starting = false) }
                return@launch
            }
            val ref = CanvasRef(CanvasKind.PHOTO, id)
            val now = System.currentTimeMillis()
            val jpeg = withContext(Dispatchers.Default) { photo.toJpeg() }
            val record = CanvasRecord(PhotoTitles.default(now, Locale.getDefault()), now, 0L)
            val result = app.store.createPhoto(ref, ready.pattern, record, jpeg)
            if (result == WriteResult.Written) {
                clearDraft()
                _state.value = PhotoFlowState(nav = PhotoNav.Started(ref))
            } else {
                app.monetization.release(id)
                _state.update { it.copy(starting = false, startFailed = true) }
            }
        }
    }

    /** Leaving the flow: the draft photo is deleted and any running conversion stops. */
    fun clearDraft() {
        work.forEach { it.cancel() }
        work = emptyList()
        for (key in listOf(KEY_DRAFT, KEY_RATIO, KEY_CROP_VIEW, KEY_CROP_RECT, KEY_SIZE, KEY_PICKED)) handle.remove<Any>(key)
        draftDir.deleteRecursively()
        val decodeFailed = _state.value.decodeFailed
        _state.value = PhotoFlowState(decodeFailed = decodeFailed)
    }

    fun onDecodeMessageShown() = _state.update { it.copy(decodeFailed = false) }

    private fun cropAndConvert(rect: Rect, size: CanvasSize, pickedByPlayer: Boolean) {
        val source = _state.value.source ?: return
        val r = Rect(rect)
        if (!r.intersect(0, 0, source.width, source.height) || r.isEmpty) return
        val cropped = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
        work.forEach { it.cancel() }
        _state.update {
            it.copy(
                cropped = cropped,
                size = size,
                pickedByPlayer = pickedByPlayer,
                faceCheck = FaceCheck.RUNNING,
                conversions = CanvasSize.entries.associateWith { Conversion.Running },
                starting = false,
                startFailed = false,
            )
        }
        val ratio = _state.value.ratio
        val faceJob = viewModelScope.launch {
            val check = FaceDetector.check(cropped)
            _state.update {
                val rules = PhotoFlowRules.sizeRules(check)
                val selected = PhotoFlowRules.selectionAfterCheck(it.size, it.pickedByPlayer, rules)
                handle[KEY_SIZE] = selected.name
                it.copy(faceCheck = check, size = selected)
            }
        }
        work = listOf(faceJob) + CanvasSize.entries.map { s ->
            viewModelScope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        val pixels = IntArray(cropped.width * cropped.height)
                        cropped.getPixels(pixels, 0, cropped.width, 0, 0, cropped.width, cropped.height)
                        val pattern = convert(pixels, cropped.width, cropped.height, GridSpec.of(s, ratio), SEED)
                        Conversion.Ready(pattern, GemRenderer.render(pattern, cellPx = PREVIEW_CELL_PX))
                    }.getOrElse { Conversion.Failed }
                }
                _state.update { it.copy(conversions = it.conversions + (s to result)) }
            }
        }
    }

    private fun restoreDraft() {
        val id = handle.get<String>(KEY_DRAFT) ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                File(draftDir, "$id.png").takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            }
            if (bitmap == null) {
                clearDraft()
                _state.update { it.copy(nav = PhotoNav.Lost) }
                return@launch
            }
            FaceDetector.warmUp()
            val ratio = handle.get<String>(KEY_RATIO)?.let(AspectRatio::valueOf) ?: AspectRatio.SQUARE
            val crop = handle.get<FloatArray>(KEY_CROP_VIEW)?.let { CropView(it[0], it[1], it[2]) } ?: CropView()
            _state.value = PhotoFlowState(source = bitmap, ratio = ratio, crop = crop)
            val rect = handle.get<IntArray>(KEY_CROP_RECT) ?: return@launch
            val size = handle.get<String>(KEY_SIZE)?.let(CanvasSize::valueOf) ?: CanvasSize.MEDIUM
            cropAndConvert(Rect(rect[0], rect[1], rect[2], rect[3]), size, handle.get<Boolean>(KEY_PICKED) ?: false)
        }
    }

    private fun saveDraft(id: String, bitmap: Bitmap) {
        draftDir.mkdirs()
        File(draftDir, "$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Decodes straight to ≤ 1024 px on the long edge (eng finding 14); never at full resolution. */
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

    private fun Bitmap.toJpeg(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()

    private companion object {
        const val DRAFT_DIR = "photo-draft"
        const val MAX_SOURCE_PX = 1024
        const val PREVIEW_CELL_PX = 8
        const val JPEG_QUALITY = 90

        /** Fixed so the same photo and crop always give the same canvas. */
        const val SEED = 0x47_4D_52_59L

        const val KEY_DRAFT = "draft"
        const val KEY_RATIO = "ratio"
        const val KEY_CROP_VIEW = "cropView"
        const val KEY_CROP_RECT = "cropRect"
        const val KEY_SIZE = "size"
        const val KEY_PICKED = "picked"
    }
}

/** Photo canvas titles (design D26). */
object PhotoTitles {
    /** "My photo · 13 Oct". */
    fun default(createdAtMillis: Long, locale: Locale): String =
        "My photo · " + SimpleDateFormat("d MMM", locale).format(Date(createdAtMillis))

    /** A rename that's blank keeps the old title; long ones are cut to the record's limit. */
    fun cleaned(input: String): String? =
        capped(input.trim().replace(Regex("\\s+"), " ")).takeIf { it.isNotEmpty() }

    /** At most [MAX_CHARS] UTF-16 units, never cutting a surrogate pair (an emoji) in half. */
    fun capped(input: String): String {
        val cut = input.take(MAX_CHARS)
        return if (cut.length < input.length && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    const val MAX_CHARS = 60
}
