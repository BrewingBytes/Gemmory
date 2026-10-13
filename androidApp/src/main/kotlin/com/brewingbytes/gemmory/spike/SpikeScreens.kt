package com.brewingbytes.gemmory.spike

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.ui.Chip
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun SpikeApp(vm: SpikeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhotoPicked(uri)
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BackHandler(enabled = state.step != SpikeStep.START) { vm.back() }

    Box(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        when (state.step) {
            SpikeStep.START -> StartScreen(state.decodeFailed, pickPhoto)
            SpikeStep.CROP -> state.source?.let { CropScreen(it, state.ratio, vm::onRatio, vm::onCropped) }
            SpikeStep.SIZE -> SizeScreen(state, vm::onSize, vm::toPreview)
            SpikeStep.PREVIEW -> PreviewScreen(state, onExport = vm::export, onBack = vm::back)
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(text, style = GemType.display, color = GemColors.Bone, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun StartScreen(decodeFailed: Boolean, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Your photos, in gems", style = GemType.displayLarge, color = GemColors.Bone)
        Spacer(Modifier.height(12.dp))
        Text(
            "Turn a photo of your own into a canvas you can finish. It never leaves this phone.",
            style = GemType.body,
            color = GemColors.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("New from photo", onPick)
        if (decodeFailed) {
            Spacer(Modifier.height(16.dp))
            Text("This photo couldn't be opened. Try another one.", style = GemType.body, color = GemColors.Bone)
        }
    }
}

/** Pinch and drag inside the frame; the photo always covers it. No rotation. */
@Composable
private fun CropScreen(
    source: Bitmap,
    ratio: AspectRatio,
    onRatio: (AspectRatio) -> Unit,
    onNext: (android.graphics.Rect) -> Unit,
) {
    val image = remember(source) { source.asImageBitmap() }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(ratio) { mutableFloatStateOf(1f) }
    var offset by remember(ratio) { mutableStateOf<Offset?>(null) }

    fun coverScale() = max(frame.width / image.width.toFloat(), frame.height / image.height.toFloat())
    fun clamp(o: Offset, scale: Float) = Offset(
        o.x.coerceIn(frame.width - image.width * scale, 0f),
        o.y.coerceIn(frame.height - image.height * scale, 0f),
    )
    fun currentOffset(scale: Float) = offset ?: Offset(
        (frame.width - image.width * scale) / 2f,
        (frame.height - image.height * scale) / 2f,
    )

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Crop")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { r -> Chip(r.label, selected = r == ratio, onClick = { onRatio(r) }) }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .wrapContentSize()
                    .aspectRatio(ratio.width / ratio.height.toFloat())
                    .clipToBounds()
                    .onSizeChanged { frame = it }
                    .pointerInput(ratio, image) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            if (frame == IntSize.Zero) return@detectTransformGestures
                            val oldScale = coverScale() * zoom
                            zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            val newScale = coverScale() * zoom
                            val o = currentOffset(oldScale)
                            offset = clamp(centroid - (centroid - o) * (newScale / oldScale) + pan, newScale)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (frame == IntSize.Zero) return@Canvas
                    val scale = coverScale() * zoom
                    val o = clamp(currentOffset(scale), scale)
                    drawImage(
                        image,
                        dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
        PrimaryButton(
            "Next",
            onClick = {
                val scale = coverScale() * zoom
                val o = clamp(currentOffset(scale), scale)
                val left = (-o.x / scale).roundToInt()
                val top = (-o.y / scale).roundToInt()
                onNext(
                    android.graphics.Rect(
                        left,
                        top,
                        left + (frame.width / scale).roundToInt(),
                        top + (frame.height / scale).roundToInt(),
                    ),
                )
            },
            enabled = frame != IntSize.Zero,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

private val numbers: NumberFormat = NumberFormat.getIntegerInstance()

private fun summary(size: CanvasSize, ratio: AspectRatio, conversion: Conversion?): String {
    val spec = GridSpec.of(size, ratio)
    val colors = (conversion as? Conversion.Ready)?.pattern?.palette?.size ?: size.paletteCap
    return "${numbers.format(spec.cellCount)} gems · $colors colors"
}

@Composable
private fun SizeScreen(state: SpikeState, onSize: (CanvasSize) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Choose a size")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CanvasSize.entries.forEach { size ->
                SizeRow(
                    size = size,
                    summary = summary(size, state.ratio, state.conversions[size]),
                    conversion = state.conversions[size],
                    selected = size == state.size,
                    onClick = { onSize(size) },
                )
            }
        }
        PrimaryButton("Next", onNext, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun SizeRow(size: CanvasSize, summary: String, conversion: Conversion?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, GemColors.Lamp, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .feltGround(LocalFelt.current.deep)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            when (conversion) {
                is Conversion.Ready -> GemImage(remember(conversion) { conversion.preview.asImageBitmap() }, "${size.label} preview")
                Conversion.Running -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = GemColors.Bone,
                    trackColor = Color.Transparent,
                )
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
private fun GemImage(bitmap: ImageBitmap, description: String, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.Low,
        modifier = modifier,
    )
}

@Composable
private fun PreviewScreen(state: SpikeState, onExport: () -> Unit, onBack: () -> Unit) {
    val conversion = state.conversions[state.size]
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Preview")
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
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = GemColors.Bone,
                        trackColor = Color.Transparent,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (conversion) {
            is Conversion.Failed -> {
                Text("Couldn't make this canvas. Try Medium or another photo.", style = GemType.body, color = GemColors.Bone)
                Spacer(Modifier.height(12.dp))
                QuietButton("Back", onBack, Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            else -> {
                Text(
                    "${state.size.label} · ${summary(state.size, state.ratio, conversion)}",
                    style = GemType.body,
                    color = GemColors.Bone,
                )
                ExportLine(state.export)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "Save before/after",
                    onExport,
                    enabled = conversion is Conversion.Ready && state.export != ExportStatus.Saving,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExportLine(status: ExportStatus) {
    val text = when (status) {
        ExportStatus.Idle -> return
        ExportStatus.Saving -> "Saving…"
        is ExportStatus.Saved -> "Saved to Pictures/Gemmory as ${status.name}"
        ExportStatus.Failed -> "Couldn't save the picture. Try again."
    }
    Text(text, style = GemType.label, color = GemColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
}

private const val MAX_ZOOM = 4f
