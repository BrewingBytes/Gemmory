package com.brewingbytes.gemmory.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.copy.LocalWords
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.FaceCheck
import com.brewingbytes.gemmory.converter.FaceHint
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.PhotoFlowRules
import com.brewingbytes.gemmory.converter.PreviewPrimary
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.Chip
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import kotlin.math.roundToInt

@Composable
private fun FlowScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar(title, onBack)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { content() }
    }
}

/** Crop (design D4): ratio chips, pinch and drag inside the frame, no rotation; the photo always covers the frame. */
@Composable
fun CropScreen(state: PhotoFlowState, onRatio: (AspectRatio) -> Unit, onView: (CropView) -> Unit, onNext: (IntRect) -> Unit, onBack: () -> Unit) {
    FlowScaffold(LocalWords.current.text("photo_crop"), onBack) {
        val source = state.source
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { r -> Chip(r.label, selected = r == state.ratio, onClick = { onRatio(r) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            if (source != null) CropFrame(source, state.ratio, state.crop, onView)
        }
        PrimaryButton(
            LocalWords.current.text("action_next"),
            onClick = { source?.let { onNext(cropRect(it, state.ratio, state.crop)) } },
            enabled = source != null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

/** The crop in source pixels for [view]: the frame's ratio, scaled down by zoom, around the clamped center. */
fun cropRect(source: ImageBitmap, ratio: AspectRatio, view: CropView): IntRect {
    val frameAspect = ratio.width / ratio.height.toFloat()
    // At zoom 1 the frame covers the photo: the largest rect of the frame's ratio inside it.
    val baseW = minOf(source.width.toFloat(), source.height * frameAspect)
    val w = baseW / view.zoom
    val h = w / frameAspect
    val cx = (view.centerX * source.width).coerceIn(w / 2, source.width - w / 2)
    val cy = (view.centerY * source.height).coerceIn(h / 2, source.height - h / 2)
    val left = (cx - w / 2).roundToInt().coerceAtLeast(0)
    val top = (cy - h / 2).roundToInt().coerceAtLeast(0)
    return IntRect(left, top, minOf(source.width, left + w.roundToInt()), minOf(source.height, top + h.roundToInt()))
}

@Composable
private fun CropFrame(source: ImageBitmap, ratio: AspectRatio, view: CropView, onView: (CropView) -> Unit) {
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(view)
    Box(
        Modifier
            .wrapContentSize()
            .aspectRatio(ratio.width / ratio.height.toFloat())
            .clipToBounds()
            .onSizeChanged { frame = it }
            .pointerInput(ratio, source) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (frame == IntSize.Zero) return@detectTransformGestures
                    val v = current
                    val zoom = (v.zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                    val rect = cropRect(source, ratio, v.copy(zoom = zoom))
                    // Pan moves the photo under the frame: convert frame px into source fractions.
                    val pxPerSource = frame.width / rect.width.toFloat()
                    val cx = v.centerX - pan.x / pxPerSource / source.width
                    val cy = v.centerY - pan.y / pxPerSource / source.height
                    val clamped = cropRect(source, ratio, CropView(zoom, cx, cy))
                    onView(CropView(zoom, clamped.exactCenterX / source.width, clamped.exactCenterY / source.height))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (frame == IntSize.Zero) return@Canvas
            val rect = cropRect(source, ratio, view)
            drawImage(
                source,
                srcOffset = IntOffset(rect.left, rect.top),
                srcSize = IntSize(rect.width, rect.height),
                dstSize = IntSize(frame.width, frame.height),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** "8,112 gems · 24 colors": the real palette once converted, else the cap for the size and color choice (#29). */
fun summary(s: Words, size: CanvasSize, ratio: AspectRatio, conversion: Conversion?, choice: ColorChoice = ColorChoice.STANDARD): String {
    val spec = GridSpec.of(size, ratio, choice)
    val colors = (conversion as? Conversion.Ready)?.pattern?.palette?.size ?: spec.paletteCap
    return s.plural("photo_summary", colors, s.number(spec.cellCount), colors)
}

/**
 * The line under Preview's size line (#29): what More needs while it's chosen without VIP, else the allowance line
 * (design D5). Null when there's nothing to say (VIP).
 */
fun previewLine(s: Words, allowance: Allowance, choice: ColorChoice, moreUnlocked: Boolean, kept: Int = 0, adsOn: Boolean = true): String? =
    when (PhotoFlowRules.previewPrimary(allowance, choice, moreUnlocked, adsOn)) {
        PreviewPrimary.WATCH_FOR_COLORS -> s.text("photo_more_colors_line")
        PreviewPrimary.WATCH_FOR_CANVAS ->
            if (!PhotoFlowRules.colorsAllowed(choice, allowance, moreUnlocked, adsOn)) {
                s.text("photo_one_video_more_colors")
            } else {
                allowanceLine(s, allowance, kept, adsOn)
            }
        PreviewPrimary.START -> allowanceLine(s, allowance, kept, adsOn)
    }

/** Preview's primary label; while ads are off ([adsOn] false, #203) the next canvas needs no video: "Start canvas". */
fun primaryText(s: Words, primary: PreviewPrimary, adsOn: Boolean = true): String = when (primary) {
    PreviewPrimary.START -> s.text("action_start")
    PreviewPrimary.WATCH_FOR_CANVAS -> s.text(if (adsOn) "video_watch" else "rewarded_grace_canvas")
    PreviewPrimary.WATCH_FOR_COLORS -> s.text("photo_watch_for_colors")
}

/** A color trough's TalkBack line (#29): "More, 60 colors, needs a video or VIP". */
fun colorTroughLabel(s: Words, size: CanvasSize, choice: ColorChoice, needsVideo: Boolean): String =
    s.plural(if (needsVideo) "photo_colors_trough_video" else "photo_colors_trough", size.cap(choice), Messages.colors(s, choice), size.cap(choice))

fun hintText(s: Words, hint: FaceHint): String = s.text(
    when (hint) {
        FaceHint.LOOK_BEST_MEDIUM_OR_BIG -> "photo_faces_best"
        FaceHint.NEED_BIG -> "photo_faces_need_big"
        FaceHint.NOT_SUPPORTED -> "photo_faces_unsupported"
    },
)

/** Size picker (design system, Layout): the face hint on top, then three option rows. */
@Composable
fun SizeScreen(state: PhotoFlowState, onSize: (CanvasSize) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val words = LocalWords.current
    FlowScaffold(LocalWords.current.text("photo_choose_size"), onBack) {
        val rules = state.rules
        val hint = rules.hint
        when {
            state.faceCheck == FaceCheck.RUNNING ->
                Text(LocalWords.current.text("photo_checking_faces"), style = GemType.label, color = GemColors.Bone, modifier = Modifier.padding(bottom = 12.dp))
            hint != null ->
                Text(hintText(words, hint), style = GemType.body, color = GemColors.Lamp, modifier = Modifier.padding(bottom = 12.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CanvasSize.entries.forEach { size ->
                SizeRow(
                    size = size,
                    ratio = state.ratio,
                    summary = summary(words, size, state.ratio, state.conversions[size]),
                    conversion = state.conversions[size],
                    selected = size == state.size && size !in rules.disabled,
                    enabled = size !in rules.disabled,
                    onClick = { onSize(size) },
                )
            }
        }
        PrimaryButton(LocalWords.current.text("action_next"), onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun SizeRow(size: CanvasSize, ratio: AspectRatio, summary: String, conversion: Conversion?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val name = Messages.size(LocalWords.current, size)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, GemColors.Lamp, shape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .feltGround(LocalFelt.current.deep)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            when (conversion) {
                is Conversion.Ready -> GemImage(conversion.preview, LocalWords.current.text("photo_size_preview", name))
                Conversion.Running -> ConvertingSlot(ratio)
                else -> Unit
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name, style = GemType.button, color = GemColors.Bone)
            Text(summary, style = GemType.label, color = GemColors.TextMuted)
        }
    }
}

/**
 * A size still converting: a `felt-raised` placeholder in the crop's shape with the 2dp indeterminate line inside it,
 * read as "Setting the gems…" (design system, Photo flow states).
 */
@Composable
private fun ConvertingSlot(ratio: AspectRatio) {
    val label = LocalWords.current.text("photo_setting_gems")
    Box(
        Modifier
            .aspectRatio(ratio.width / ratio.height.toFloat())
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.raised)
            .testTag("size-converting")
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.BottomCenter,
    ) {
        ThinProgress(Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
    }
}

@Composable
private fun ThinProgress(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = GemColors.Bone,
        trackColor = Color.Transparent,
    )
}

@Composable
private fun GemImage(bitmap: ImageBitmap, description: String, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.Low,
        modifier = modifier,
    )
}

/**
 * Preview + Start (design D4, D5): a flat-cushion preview of the whole canvas, the size line, the allowance line and
 * Start. With today's free canvas used, the primary becomes "Watch a video" (rewarded sheet); every video watched
 * gives one more canvas, with no daily cap (founder call #7).
 */
@Composable
fun PreviewScreen(
    state: PhotoFlowState,
    allowance: Allowance,
    onStart: () -> Unit,
    onWatchVideo: () -> Unit,
    onChooseAnother: () -> Unit,
    onBack: () -> Unit,
    /** Photo canvases kept in the inventory (#38), for "Uses a kept photo canvas (2 left)." */
    kept: Int = 0,
    onChoice: (ColorChoice) -> Unit = {},
    /** "Watch a video for more colors" (#29). */
    onWatchColors: () -> Unit = {},
    /** False while ads are off (#203): More colors is free, and the next canvas starts with no video. */
    adsOn: Boolean = true,
) {
    val conversion = state.current
    val rules = state.rules
    // While another color choice converts, the last preview stays, dimmed under a 2dp line (#29).
    var lastReady by remember { mutableStateOf<Conversion.Ready?>(null) }
    LaunchedEffect(conversion) { if (conversion is Conversion.Ready) lastReady = conversion }
    val words = LocalWords.current
    FlowScaffold(LocalWords.current.text("photo_preview"), onBack) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .feltGround(LocalFelt.current.deep),
            contentAlignment = Alignment.Center,
        ) {
            val previous = lastReady
            when {
                conversion is Conversion.Ready -> {
                    val bitmap = conversion.preview
                    GemImage(bitmap, LocalWords.current.text("photo_gem_preview"), Modifier.fillMaxSize().padding(8.dp))
                }
                conversion is Conversion.Failed -> Unit
                previous != null && previous.pattern.width == GridSpec.of(state.size, state.ratio).width -> {
                    val bitmap = previous.preview
                    GemImage(bitmap, LocalWords.current.text("photo_gem_preview_updating"), Modifier.fillMaxSize().padding(8.dp).alpha(0.35f))
                    ThinProgress(Modifier.padding(horizontal = 24.dp))
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(LocalWords.current.text("photo_setting_gems"), style = GemType.body, color = GemColors.Bone)
                    Spacer(Modifier.height(12.dp))
                    ThinProgress()
                }
            }
        }
        if (rules.startAllowed) {
            ColorRow(state.size, state.choice, needsVideo = !PhotoFlowRules.colorsAllowed(ColorChoice.MORE, allowance, state.moreUnlocked, adsOn), onChoice)
        }
        Spacer(Modifier.height(16.dp))
        when {
            conversion is Conversion.Failed -> {
                Text(LocalWords.current.text("photo_convert_failed"), style = GemType.body, color = GemColors.Bone)
                Spacer(Modifier.height(12.dp))
                QuietButton(LocalWords.current.text("action_back"), onBack, Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            !rules.startAllowed -> {
                Text(hintText(words, rules.hint ?: FaceHint.NOT_SUPPORTED), style = GemType.body, color = GemColors.Lamp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(LocalWords.current.text("photo_choose_another"), onChooseAnother, Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            else -> {
                Text(
                    words.text("photo_size_summary", Messages.size(words, state.size), summary(words, state.size, state.ratio, conversion, state.choice)),
                    style = GemType.body,
                    color = GemColors.Bone,
                )
                if (state.faceCheck == FaceCheck.RUNNING) {
                    Text(
                        LocalWords.current.text("photo_checking_faces"),
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.startFailed) {
                    Text(
                        LocalWords.current.text("photo_save_failed"),
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                previewLine(words, allowance, state.choice, state.moreUnlocked, kept, adsOn)?.let {
                    Text(
                        it,
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.padding(top = 4.dp).testTag("allowance-line"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                val primary = PhotoFlowRules.previewPrimary(allowance, state.choice, state.moreUnlocked, adsOn)
                val tag = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("preview-primary")
                when (primary) {
                    PreviewPrimary.WATCH_FOR_CANVAS -> PrimaryButton(primaryText(words, primary, adsOn), onWatchVideo, tag)
                    PreviewPrimary.WATCH_FOR_COLORS -> PrimaryButton(primaryText(words, primary), onWatchColors, tag)
                    PreviewPrimary.START -> PrimaryButton(
                        primaryText(words, primary),
                        onStart,
                        enabled = state.canStart && allowance.canStart,
                        held = state.starting,
                        modifier = tag,
                    )
                }
            }
        }
    }
}

/**
 * Fewer / Standard / More under the preview (#29): three troughs with the count in mono. The chosen one lifts 3dp with
 * the lamp outline, as a tray trough does; More carries a play mark while it needs a video.
 */
@Composable
private fun ColorRow(size: CanvasSize, choice: ColorChoice, needsVideo: Boolean, onChoice: (ColorChoice) -> Unit) {
    Spacer(Modifier.height(12.dp))
    val words = LocalWords.current
    Text(LocalWords.current.text("photo_colors"), style = GemType.label, color = GemColors.Bone, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorChoice.entries.forEach { c ->
            val selected = c == choice
            val locked = c == ColorChoice.MORE && needsVideo
            val shape = RoundedCornerShape(8.dp)
            val troughLabel = colorTroughLabel(words, size, c, locked)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .offset(y = if (selected) (-3).dp else 0.dp)
                    .border(2.dp, if (selected) GemColors.Lamp else Color.Transparent, shape)
                    .clip(shape)
                    .feltGround(LocalFelt.current.deep)
                    .selectable(selected = selected, role = Role.RadioButton) { onChoice(c) }
                    // The tag goes before the clear, which drops semantics set after it (lessons, #35).
                    .testTag("colors-${c.param}")
                    .clearAndSetSemantics {
                        contentDescription = troughLabel
                        this.selected = selected
                        role = Role.RadioButton
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Messages.colors(words, c), style = GemType.label, color = GemColors.Bone, textAlign = TextAlign.Center, maxLines = 2)
                    Text("${size.cap(c)}", style = GemType.mono.copy(fontSize = 13.sp), color = GemColors.TextMuted)
                }
                if (locked) {
                    Image(
                        GemIcons.Play,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GemColors.Bone),
                        modifier = Modifier.align(Alignment.TopEnd).size(10.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_ZOOM = 4f

/** The rect's center as `android.graphics.Rect.exactCenterX` gives it: in halves of a pixel, not rounded. */
private val IntRect.exactCenterX: Float get() = (left + right) * 0.5f

private val IntRect.exactCenterY: Float get() = (top + bottom) * 0.5f
