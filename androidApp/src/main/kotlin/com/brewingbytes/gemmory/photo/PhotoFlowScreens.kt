package com.brewingbytes.gemmory.photo

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.FaceCheck
import com.brewingbytes.gemmory.converter.FaceHint
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.Chip
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import java.text.NumberFormat
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
fun CropScreen(state: PhotoFlowState, onRatio: (AspectRatio) -> Unit, onView: (CropView) -> Unit, onNext: (Rect) -> Unit, onBack: () -> Unit) {
    FlowScaffold("Crop", onBack) {
        val source = state.source
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { r -> Chip(r.label, selected = r == state.ratio, onClick = { onRatio(r) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            if (source != null) CropFrame(source, state.ratio, state.crop, onView)
        }
        PrimaryButton(
            "Next",
            onClick = { source?.let { onNext(cropRect(it, state.ratio, state.crop)) } },
            enabled = source != null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

/** The crop in source pixels for [view]: the frame's ratio, scaled down by zoom, around the clamped center. */
internal fun cropRect(source: Bitmap, ratio: AspectRatio, view: CropView): Rect {
    val frameAspect = ratio.width / ratio.height.toFloat()
    // At zoom 1 the frame covers the photo: the largest rect of the frame's ratio inside it.
    val baseW = minOf(source.width.toFloat(), source.height * frameAspect)
    val w = baseW / view.zoom
    val h = w / frameAspect
    val cx = (view.centerX * source.width).coerceIn(w / 2, source.width - w / 2)
    val cy = (view.centerY * source.height).coerceIn(h / 2, source.height - h / 2)
    val left = (cx - w / 2).roundToInt().coerceAtLeast(0)
    val top = (cy - h / 2).roundToInt().coerceAtLeast(0)
    return Rect(left, top, minOf(source.width, left + w.roundToInt()), minOf(source.height, top + h.roundToInt()))
}

@Composable
private fun CropFrame(source: Bitmap, ratio: AspectRatio, view: CropView, onView: (CropView) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(view)
    Box(
        Modifier
            .wrapContentSize()
            .aspectRatio(ratio.width / ratio.height.toFloat())
            .clipToBounds()
            .onSizeChanged { frame = it }
            .pointerInput(ratio, image) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (frame == IntSize.Zero) return@detectTransformGestures
                    val v = current
                    val zoom = (v.zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                    val rect = cropRect(source, ratio, v.copy(zoom = zoom))
                    // Pan moves the photo under the frame: convert frame px into source fractions.
                    val pxPerSource = frame.width / rect.width().toFloat()
                    val cx = v.centerX - pan.x / pxPerSource / source.width
                    val cy = v.centerY - pan.y / pxPerSource / source.height
                    val clamped = cropRect(source, ratio, CropView(zoom, cx, cy))
                    onView(CropView(zoom, clamped.exactCenterX() / source.width, clamped.exactCenterY() / source.height))
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (frame == IntSize.Zero) return@Canvas
            val rect = cropRect(source, ratio, view)
            drawImage(
                image,
                srcOffset = IntOffset(rect.left, rect.top),
                srcSize = IntSize(rect.width(), rect.height()),
                dstSize = IntSize(frame.width, frame.height),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private val numbers: NumberFormat = NumberFormat.getIntegerInstance()

/** "8,112 gems · 24 colors": the real palette once converted, else the size's cap. */
internal fun summary(size: CanvasSize, ratio: AspectRatio, conversion: Conversion?): String {
    val spec = GridSpec.of(size, ratio)
    val colors = (conversion as? Conversion.Ready)?.pattern?.palette?.size ?: size.paletteCap
    return "${numbers.format(spec.cellCount)} gems · $colors colors"
}

internal fun hintText(hint: FaceHint): String = when (hint) {
    FaceHint.LOOK_BEST_MEDIUM_OR_BIG -> "Faces look best at Medium or Big"
    FaceHint.NEED_BIG -> "Faces need Big"
    FaceHint.NOT_SUPPORTED -> "Faces aren't supported yet"
}

/** Size picker (design system, Layout): the face hint on top, then three option rows. */
@Composable
fun SizeScreen(state: PhotoFlowState, onSize: (CanvasSize) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    FlowScaffold("Choose a size", onBack) {
        val rules = state.rules
        val hint = rules.hint
        when {
            state.faceCheck == FaceCheck.RUNNING ->
                Text("Checking for faces…", style = GemType.label, color = GemColors.Bone, modifier = Modifier.padding(bottom = 12.dp))
            hint != null ->
                Text(hintText(hint), style = GemType.body, color = GemColors.Lamp, modifier = Modifier.padding(bottom = 12.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CanvasSize.entries.forEach { size ->
                SizeRow(
                    size = size,
                    summary = summary(size, state.ratio, state.conversions[size]),
                    conversion = state.conversions[size],
                    selected = size == state.size && size !in rules.disabled,
                    enabled = size !in rules.disabled,
                    onClick = { onSize(size) },
                )
            }
        }
        PrimaryButton("Next", onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun SizeRow(size: CanvasSize, summary: String, conversion: Conversion?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
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
                is Conversion.Ready -> GemImage(remember(conversion) { conversion.preview.asImageBitmap() }, "${size.label} preview")
                Conversion.Running -> ThinProgress()
                else -> Unit
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(size.label, style = GemType.button, color = GemColors.Bone)
            Text(summary, style = GemType.label, color = GemColors.TextMuted)
        }
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

/** The allowance line on Preview and the gallery note (design D5); null when there is none (VIP). */
internal fun allowanceLine(allowance: Allowance): String? = when (allowance) {
    Allowance.Unlimited -> null
    Allowance.Free -> "Uses today's free photo canvas."
    Allowance.Bonus -> "Uses a bonus canvas from a video."
    Allowance.WatchVideo -> "Today's free one is used. Watch a video for another."
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
) {
    val conversion = state.conversions[state.size]
    val rules = state.rules
    FlowScaffold("Preview", onBack) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .feltGround(LocalFelt.current.deep),
            contentAlignment = Alignment.Center,
        ) {
            when (conversion) {
                is Conversion.Ready -> {
                    val bitmap = remember(conversion) { conversion.preview.asImageBitmap() }
                    GemImage(bitmap, "Gem preview", Modifier.fillMaxSize().padding(8.dp))
                }
                is Conversion.Failed -> Unit
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Setting the gems…", style = GemType.body, color = GemColors.Bone)
                    Spacer(Modifier.height(12.dp))
                    ThinProgress()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            conversion is Conversion.Failed -> {
                Text("Couldn't make this canvas. Try Medium or another photo.", style = GemType.body, color = GemColors.Bone)
                Spacer(Modifier.height(12.dp))
                QuietButton("Back", onBack, Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            !rules.startAllowed -> {
                Text(hintText(rules.hint ?: FaceHint.NOT_SUPPORTED), style = GemType.body, color = GemColors.Lamp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Choose another photo", onChooseAnother, Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            else -> {
                Text(
                    "${state.size.label} · ${summary(state.size, state.ratio, conversion)}",
                    style = GemType.body,
                    color = GemColors.Bone,
                )
                if (state.faceCheck == FaceCheck.RUNNING) {
                    Text(
                        "Checking for faces…",
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.startFailed) {
                    Text(
                        "Couldn't save this canvas on the phone. Try again.",
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                allowanceLine(allowance)?.let {
                    Text(
                        it,
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.padding(top = 4.dp).testTag("allowance-line"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (allowance == Allowance.WatchVideo) {
                    PrimaryButton("Watch a video", onWatchVideo, Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("preview-primary"))
                } else {
                    PrimaryButton(
                        "Start",
                        onStart,
                        enabled = state.canStart && allowance.canStart,
                        held = state.starting,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("preview-primary"),
                    )
                }
            }
        }
    }
}

private const val MAX_ZOOM = 4f
