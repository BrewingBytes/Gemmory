package com.brewingbytes.gemmory.photo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.ConverterOptions
import com.brewingbytes.gemmory.converter.FaceCheck
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.converter.PhotoFlowRules
import com.brewingbytes.gemmory.converter.SizeRules
import com.brewingbytes.gemmory.converter.convert
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.policy.ConsumeResult
import com.brewingbytes.gemmory.render.decodeImage
import com.brewingbytes.gemmory.render.encodePng
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

sealed interface Conversion {
    data object Running : Conversion

    data class Ready(val pattern: Pattern, val preview: ImageBitmap) : Conversion

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
    val source: ImageBitmap? = null,
    val ratio: AspectRatio = AspectRatio.SQUARE,
    val crop: CropView = CropView(),
    val cropped: ImageBitmap? = null,
    val size: CanvasSize = CanvasSize.MEDIUM,
    val pickedByPlayer: Boolean = false,
    val faceCheck: FaceCheck = FaceCheck.RUNNING,
    /** Every size at Standard colors, for the size picker. */
    val conversions: Map<CanvasSize, Conversion> = emptyMap(),
    /** The color choice on Preview (#29), kept for the whole draft. */
    val choice: ColorChoice = ColorChoice.STANDARD,
    /** Fewer and More, converted on Preview when chosen (#29). */
    val others: Map<Pair<CanvasSize, ColorChoice>, Conversion> = emptyMap(),
    /** A video unlocked More for this photo (#29): the colors video, or a canvas video watched on this Preview. */
    val moreUnlocked: Boolean = false,
    val starting: Boolean = false,
    val startFailed: Boolean = false,
    val nav: PhotoNav? = null,
) {
    val rules: SizeRules get() = PhotoFlowRules.sizeRules(faceCheck)

    fun conversion(size: CanvasSize, choice: ColorChoice): Conversion? =
        if (choice == ColorChoice.STANDARD) conversions[size] else others[size to choice]

    /** What Preview shows and Start makes: the chosen size at the chosen colors. */
    val current: Conversion? get() = conversion(size, choice)

    /**
     * Start waits for the face check, so the face rule always applies to the size it starts (design D4). Whether the
     * color choice is allowed is Preview's primary (#29), checked again in [PhotoFlowViewModel.start].
     */
    val canStart: Boolean get() = PhotoFlowRules.canStart(faceCheck, size, converted = current is Conversion.Ready)
}

/**
 * Photo Picker → Crop → Size → Preview → Start (design D4). Small state lives in [SavedStateHandle]; the decoded
 * photo (≤ 1024 px) is kept as a draft in the cache's `photo-draft/`, which is never backed up, so the flow survives
 * rotation and process death. Nothing leaves the phone; on Start the cropped photo moves into the canvas directory
 * the backup rules exclude, and the draft is deleted. Shared since iOS Stage 1c (T64): the image work is the
 * platform's [PhotoDecoder] and [FaceFinder], read from the [AppGraph].
 */
class PhotoFlowViewModel(private val graph: AppGraph, private val handle: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(PhotoFlowState())
    val state: StateFlow<PhotoFlowState> = _state.asStateFlow()
    private var work: List<Job> = emptyList()

    /** False once the flow's draft is gone; a photo-flow screen with no draft goes back to the gallery. */
    val hasDraft: Boolean get() = handle.contains(KEY_DRAFT) || _state.value.decoding

    init {
        restoreDraft()
    }

    /** [uri] is the picked photo as the platform's [PhotoDecoder] reads it. */
    fun onPhotoPicked(uri: String) {
        clearDraft()
        _state.value = PhotoFlowState(decoding = true)
        viewModelScope.launch {
            val id = "photo-${Clock.System.now().toEpochMilliseconds()}"
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { graph.photos.decode(uri, MAX_SOURCE_PX) }.getOrNull()?.also { saveDraft(id, it) }
            }
            if (bitmap == null) {
                _state.value = PhotoFlowState(decodeFailed = true)
                return@launch
            }
            graph.faces.warmUp()
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
    fun onCropNext(rect: IntRect) {
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
        convertChoice()
    }

    /** Fewer / Standard / More on Preview (#29): converts that size at those colors once, then swaps the preview. */
    fun onChoice(choice: ColorChoice) {
        handle[KEY_CHOICE] = choice.name
        _state.update { it.copy(choice = choice) }
        convertChoice()
    }

    /**
     * A rewarded video for this photo landed (#29): the colors video, or a canvas video (one video gets the canvas
     * with more colors, so nobody watches two). Kept for this draft only.
     */
    fun onMoreColorsUnlocked() {
        handle[KEY_MORE] = true
        _state.update { it.copy(moreUnlocked = true) }
    }

    /**
     * Start (design D4, eng E4a): single-flight, and the canvas id is the draft's, so a repeat makes one canvas and
     * uses one allowance (`DailyLedger.consume(draftId)` is idempotent). A canvas that can't be written gives the
     * allowance back.
     */
    fun start() {
        val s = _state.value
        val ready = s.current as? Conversion.Ready ?: return
        val photo = s.cropped ?: return
        val id = handle.get<String>(KEY_DRAFT) ?: return
        if (!s.canStart || s.starting) return
        val access = graph.access
        if (!PhotoFlowRules.colorsAllowed(s.choice, access.allowance.value, s.moreUnlocked, access.adsOn)) return
        _state.update { it.copy(starting = true, startFailed = false) }
        viewModelScope.launch {
            if (access.consume(id) == ConsumeResult.NoneLeft) {
                _state.update { it.copy(starting = false) }
                return@launch
            }
            val ref = CanvasRef(CanvasKind.PHOTO, id)
            val now = Clock.System.now().toEpochMilliseconds()
            val jpeg = withContext(Dispatchers.Default) { graph.photos.jpeg(photo, JPEG_QUALITY) }
            val record = CanvasRecord(PhotoTitles.default(graph.words, now), now, 0L)
            val result = graph.canvases.createPhoto(ref, ready.pattern, record, jpeg, s.choice)
            if (result == WriteResult.Written) {
                clearDraft()
                _state.value = PhotoFlowState(nav = PhotoNav.Started(ref))
            } else {
                access.release(id)
                _state.update { it.copy(starting = false, startFailed = true) }
            }
        }
    }

    /** Leaving the flow: the draft photo is deleted and any running conversion stops. */
    fun clearDraft() {
        work.forEach { it.cancel() }
        work = emptyList()
        for (key in listOf(KEY_DRAFT, KEY_RATIO, KEY_CROP_VIEW, KEY_CROP_RECT, KEY_SIZE, KEY_PICKED, KEY_CHOICE, KEY_MORE)) {
            handle.remove<Any>(key)
        }
        graph.cache.deleteRecursive(DRAFT_DIR)
        val decodeFailed = _state.value.decodeFailed
        _state.value = PhotoFlowState(decodeFailed = decodeFailed)
    }

    fun onDecodeMessageShown() = _state.update { it.copy(decodeFailed = false) }

    private fun cropAndConvert(rect: IntRect, size: CanvasSize, pickedByPlayer: Boolean) {
        val source = _state.value.source ?: return
        val r = rect.intersect(IntRect(0, 0, source.width, source.height))
        if (r.width <= 0 || r.height <= 0) return
        val cropped = graph.photos.crop(source, r)
        work.forEach { it.cancel() }
        _state.update {
            it.copy(
                cropped = cropped,
                size = size,
                pickedByPlayer = pickedByPlayer,
                faceCheck = FaceCheck.RUNNING,
                conversions = CanvasSize.entries.associateWith { Conversion.Running },
                others = emptyMap(),
                starting = false,
                startFailed = false,
            )
        }
        val ratio = _state.value.ratio
        val faceJob = viewModelScope.launch {
            val check = graph.faces.check(cropped)
            _state.update {
                val rules = PhotoFlowRules.sizeRules(check)
                val selected = PhotoFlowRules.selectionAfterCheck(it.size, it.pickedByPlayer, rules)
                handle[KEY_SIZE] = selected.name
                it.copy(faceCheck = check, size = selected)
            }
        }
        work = listOf(faceJob) + CanvasSize.entries.map { s ->
            viewModelScope.launch {
                val result = convertOne(cropped, s, ratio, ColorChoice.STANDARD)
                _state.update { it.copy(conversions = it.conversions + (s to result)) }
            }
        }
        convertChoice()
    }

    /** The chosen size at the chosen colors, when it's Fewer or More and not converted yet (#29). */
    private fun convertChoice() {
        val s = _state.value
        val cropped = s.cropped ?: return
        if (s.choice == ColorChoice.STANDARD || s.conversion(s.size, s.choice) != null) return
        val key = s.size to s.choice
        _state.update { it.copy(others = it.others + (key to Conversion.Running)) }
        work = work + viewModelScope.launch {
            val result = convertOne(cropped, key.first, s.ratio, key.second)
            _state.update { it.copy(others = it.others + (key to result)) }
        }
    }

    private suspend fun convertOne(cropped: ImageBitmap, size: CanvasSize, ratio: AspectRatio, choice: ColorChoice): Conversion =
        withContext(Dispatchers.Default) {
            runCatching {
                val pixels = IntArray(cropped.width * cropped.height)
                cropped.readPixels(pixels)
                val spec = GridSpec.of(size, ratio, choice)
                val pattern = convert(pixels, cropped.width, cropped.height, spec, SEED, ConverterOptions.of(choice))
                Conversion.Ready(pattern, graph.renderGems(pattern, PREVIEW_CELL_PX, null))
            }.getOrElse {
                if (it is CancellationException) throw it
                graph.reporter.report(ConverterException(size, ratio, cropped.width, cropped.height, it, choice))
                Conversion.Failed
            }
        }

    private fun restoreDraft() {
        val id = handle.get<String>(KEY_DRAFT) ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { graph.cache.read(draftPath(id)) }.getOrNull()?.let(::decodeImage)
            }
            if (bitmap == null) {
                clearDraft()
                _state.update { it.copy(nav = PhotoNav.Lost) }
                return@launch
            }
            graph.faces.warmUp()
            val ratio = handle.get<String>(KEY_RATIO)?.let(AspectRatio::valueOf) ?: AspectRatio.SQUARE
            val crop = handle.get<FloatArray>(KEY_CROP_VIEW)?.let { CropView(it[0], it[1], it[2]) } ?: CropView()
            val choice = handle.get<String>(KEY_CHOICE)?.let(ColorChoice::valueOf) ?: ColorChoice.STANDARD
            val more = handle.get<Boolean>(KEY_MORE) ?: false
            _state.value = PhotoFlowState(source = bitmap, ratio = ratio, crop = crop, choice = choice, moreUnlocked = more)
            val rect = handle.get<IntArray>(KEY_CROP_RECT) ?: return@launch
            val size = handle.get<String>(KEY_SIZE)?.let(CanvasSize::valueOf) ?: CanvasSize.MEDIUM
            cropAndConvert(IntRect(rect[0], rect[1], rect[2], rect[3]), size, handle.get<Boolean>(KEY_PICKED) ?: false)
        }
    }

    /** Throws when it can't be kept: the photo then counts as one that couldn't be opened, as before the move. */
    private fun saveDraft(id: String, bitmap: ImageBitmap) {
        graph.cache.mkdirs(DRAFT_DIR)
        graph.cache.writeAtomic(draftPath(id), bitmap.encodePng())
    }

    private fun draftPath(id: String) = "$DRAFT_DIR/$id.png"

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
        const val KEY_CHOICE = "colors"
        const val KEY_MORE = "moreUnlocked"
    }
}

/** Photo canvas titles (design D26). */
object PhotoTitles {
    /** "My photo · 13 Oct", the date in the app's language (eng O-E4). */
    fun default(words: Words, createdAtMillis: Long): String =
        words.text("photo_default_title_dated", words.dayMonth(createdAtMillis))

    /** A rename that's blank keeps the old title; long ones are cut to the record's limit ([TitleInput]). */
    fun cleaned(input: String): String? = TitleInput.cleaned(input)

    /** At most [MAX_CHARS] UTF-16 units, never cutting a surrogate pair (an emoji) in half. */
    fun capped(input: String): String = TitleInput.capped(input)

    const val MAX_CHARS = TitleInput.MAX_CHARS
}

/** The converter failed on a photo (#12): the size, ratio, colors and crop dimensions only, never the photo. */
class ConverterException(
    size: CanvasSize,
    ratio: AspectRatio,
    width: Int,
    height: Int,
    cause: Throwable,
    choice: ColorChoice = ColorChoice.STANDARD,
) : Exception("convert ${size.name.lowercase()} ${ratio.label} ${choice.param} from ${width}x$height failed", cause)
