package com.brewingbytes.gemmory.play

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.GameEvent
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.game.Rejection
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The play canvas (design system, Play canvas): top bar with back, title and mono progress; the canvas on
 * felt-deep; the sorting tray at the bottom. No ads and no other chrome.
 */
@Composable
fun PlayScreen(ref: CanvasRef, title: String, onBack: () -> Unit, vm: PlayViewModel = viewModel()) {
    LaunchedEffect(ref) { vm.open(ref) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    val leave = {
        vm.close()
        onBack()
    }
    BackHandler(onBack = leave)

    val felt = LocalFelt.current
    Column(
        Modifier
            .fillMaxSize()
            .feltGround(felt.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (val ui = vm.ui) {
            PlayUi.Loading -> {
                TopBar(title, progress = null, onBack = leave)
                Box(Modifier.weight(1f).fillMaxWidth().background(felt.deep))
            }
            PlayUi.Damaged -> {
                TopBar(title, progress = null, onBack = leave)
                Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center) {
                    Text("This canvas's file is damaged, so it can't be opened.", style = GemType.body, color = GemColors.Bone)
                    Spacer(Modifier.height(16.dp))
                    QuietButton("Back", leave, Modifier.fillMaxWidth())
                }
            }
            is PlayUi.Ready -> {
                val session = ui.session
                TopBar(title, progress = session.placedCount to session.cellCount, onBack = leave)
                val diskFull by session.diskFull.collectAsStateWithLifecycle()
                if (diskFull) {
                    Text(
                        "Progress isn't saving: phone storage is full.",
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                val context = LocalContext.current
                val feedback = remember { Feedback(context) }
                DisposableEffect(feedback) { onDispose { feedback.release() } }
                GemCanvas(session, feedback, Modifier.weight(1f).fillMaxWidth())
                Tray(session)
            }
        }
    }
}

private val BackArrow: ImageVector = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f, autoMirror = true)
    .path(fill = SolidColor(Color.White)) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineToRelative(5.59f, -5.59f)
        lineTo(12f, 4f)
        lineToRelative(-8f, 8f)
        lineToRelative(8f, 8f)
        lineToRelative(1.41f, -1.41f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        close()
    }
    .build()

@Composable
private fun TopBar(title: String, progress: Pair<Int, Int>?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            Image(BackArrow, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = GemType.display,
            color = GemColors.Bone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (progress != null) {
            val format = remember { NumberFormat.getIntegerInstance() }
            Text(
                "${format.format(progress.first)} / ${format.format(progress.second)}",
                style = GemType.mono.copy(fontSize = 14.sp),
                color = GemColors.TextMuted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun GemCanvas(session: PlaySession, feedback: Feedback, modifier: Modifier) {
    val viewport = session.viewport
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val painter = remember(session.pattern) { CellPainter(context, session.pattern) }
    val scope = rememberCoroutineScope()

    fun onTap(at: Offset) {
        val cell = viewport.cellAt(at.x, at.y) ?: return
        if (viewport.zoomDp < CanvasViewport.PLACE_MIN_DP) {
            // Below 16dp a tap never places: it zooms to 24dp cells centered on the tap.
            val (toX, toY) = viewport.pointAt(at.x, at.y)
            val fromZoom = viewport.zoomDp
            val fromX = viewport.centerX
            val fromY = viewport.centerY
            scope.launch {
                animate(0f, 1f, animationSpec = tween(TAP_ZOOM_MS, easing = FastOutSlowInEasing)) { t, _ ->
                    viewport.set(
                        fromZoom + (CanvasViewport.TAP_ZOOM_DP - fromZoom) * t,
                        fromX + (toX - fromX) * t,
                        fromY + (toY - fromY) * t,
                    )
                }
            }
            return
        }
        when (val result = session.place(cell)) {
            is PlaceResult.Placed -> result.events.forEach { event -> play(event, feedback, view) }
            is PlaceResult.Rejected -> if (result.reason == Rejection.WRONG_COLOR) feedback.wrongColor(view)
        }
    }

    Canvas(
        modifier
            .clipToBounds()
            .background(LocalFelt.current.deep)
            .onSizeChanged { viewport.onLayout(it.width, it.height, density) }
            .pointerInput(session) { detectTapGestures(onTap = ::onTap) }
            .pointerInput(session) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport.transform(centroid.x, centroid.y, pan.x, pan.y, zoom)
                }
            },
    ) {
        // Read so the canvas redraws on every placement and selection change; zoom and pan are read inside draw().
        session.placedCount
        val selected = session.selectedSlot
        drawIntoCanvas { painter.draw(it.nativeCanvas, viewport, session, selected, density) }
    }
}

private fun play(event: GameEvent, feedback: Feedback, view: View) = when (event) {
    is GameEvent.GemPlaced -> feedback.gemPlaced(view)
    is GameEvent.ColorFinished -> feedback.colorFinished(view)
    GameEvent.PictureFinished -> feedback.pictureFinished()
}

/**
 * Draws the visible cells on the native canvas (design system zoom table): flat cells below 8dp, cushion-cut gems
 * from 8dp, numbers on unplaced cells from 16dp, facets on placed gems from 20dp. A placed gem is the exact grid
 * color; an unplaced cell is that color mixed 78% toward bone, its number in on-bone. Remaining cells of the selected
 * color use a stronger tint at every zoom (40% toward bone), their number in whichever of on-bone or bone reads better.
 */
private class CellPainter(context: Context, private val pattern: Pattern) {
    private val gem = IntArray(pattern.palette.size) { pattern.palette[it].argb or OPAQUE }
    private val gemSeam = IntArray(gem.size) { shade(gem[it], 0.8f) }
    private val tint = IntArray(gem.size) { mix(gem[it], BONE, 0.78f) }
    private val tintSeam = IntArray(gem.size) { shade(tint[it], 0.92f) }
    private val selectedTint = IntArray(gem.size) { mix(gem[it], BONE, SELECTED_TINT) }
    private val selectedSeam = IntArray(gem.size) { shade(selectedTint[it], 0.92f) }
    private val selectedNumber = IntArray(gem.size) { readableOn(selectedTint[it]) }
    private val labels = Array(gem.size) { (it + 1).toString() }
    private val flat = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ON_BONE
        textAlign = Paint.Align.CENTER
        val mono = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_mono) ?: Typeface.MONOSPACE
        typeface = Typeface.create(mono, 700, false)
    }
    private val rect = RectF()
    private val path = Path()

    fun draw(
        canvas: android.graphics.Canvas,
        viewport: CanvasViewport,
        session: PlaySession,
        selectedSlot: Int,
        density: Float,
    ) {
        val cell = viewport.cellPx
        if (cell <= 0f) return
        val ox = viewport.offsetX
        val oy = viewport.offsetY
        val zoomDp = viewport.zoomDp
        // Only the cells on screen are visited; the full renderer (sprites, baked low-zoom bitmap) is #10.
        val col0 = maxOf(0, floor(-ox / cell).toInt())
        val col1 = minOf(pattern.width - 1, floor((viewport.width - ox) / cell).toInt())
        val row0 = maxOf(0, floor(-oy / cell).toInt())
        val row1 = minOf(pattern.height - 1, floor((viewport.height - oy) / cell).toInt())

        val inset = minOf(GEM_INSET_DP * density, cell * 0.1f)
        val size = cell - 2 * inset
        val radius = size * GEM_RADIUS
        val numbers = zoomDp >= CanvasViewport.PLACE_MIN_DP
        val facets = zoomDp >= FACET_MIN_DP
        number.textSize = cell * NUMBER_SCALE
        val baseline = -(number.ascent() + number.descent()) / 2f

        for (row in row0..row1) {
            val top = oy + row * cell
            for (col in col0..col1) {
                val index = row * pattern.width + col
                val slot = pattern.cells[index]
                val placed = session.isPlaced(index)
                val selected = !placed && slot == selectedSlot
                val left = ox + col * cell
                val face = when {
                    placed -> gem[slot]
                    selected -> selectedTint[slot]
                    else -> tint[slot]
                }
                if (zoomDp < CUSHION_MIN_DP) {
                    flat.color = face
                    canvas.drawRect(left, top, left + cell, top + cell, flat)
                    continue
                }
                // A seam in the cell's own darker color, so the grid reads without dark gaps.
                flat.color = when {
                    placed -> gemSeam[slot]
                    selected -> selectedSeam[slot]
                    else -> tintSeam[slot]
                }
                canvas.drawRect(left, top, left + cell, top + cell, flat)
                rect.set(left + inset, top + inset, left + inset + size, top + inset + size)
                fill.color = face
                canvas.drawRoundRect(rect, radius, radius, fill)
                if (placed && facets) drawFacets(canvas, size, radius)
                if (!placed && numbers) {
                    number.color = if (selected) selectedNumber[slot] else ON_BONE
                    canvas.drawText(labels[slot], left + cell / 2f, top + cell / 2f + baseline, number)
                }
            }
        }
    }

    /** Two-facet highlight: top-left triangle at 20% white, bottom-right at 14% black. */
    private fun drawFacets(canvas: android.graphics.Canvas, size: Float, radius: Float) {
        val l = rect.left
        val t = rect.top
        canvas.withSave {
            path.reset()
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            clipPath(path)
            path.reset()
            path.moveTo(l, t)
            path.lineTo(l + size * 0.5f, t)
            path.lineTo(l, t + size * 0.5f)
            path.close()
            drawPath(path, light)
            path.reset()
            path.moveTo(l + size, t + size)
            path.lineTo(l + size, t + size * 0.5f)
            path.lineTo(l + size * 0.5f, t + size)
            path.close()
            drawPath(path, dark)
        }
    }

    private companion object {
        const val OPAQUE = 0xFF shl 24
        const val BONE = 0xFFE9E4D8.toInt()
        const val ON_BONE = 0xFF2B322D.toInt()
        const val SELECTED_TINT = 0.40f
        const val GEM_INSET_DP = 0.6f
        const val GEM_RADIUS = 0.18f
        const val NUMBER_SCALE = 0.46f
        const val CUSHION_MIN_DP = 8f
        const val FACET_MIN_DP = 20f

        fun shade(argb: Int, factor: Float): Int = mix(argb, OPAQUE, 1f - factor)

        /** On-bone or bone, whichever has more contrast on [argb]. */
        fun readableOn(argb: Int): Int =
            if (ColorUtils.calculateContrast(ON_BONE, argb) >= ColorUtils.calculateContrast(BONE, argb)) ON_BONE else BONE

        fun mix(from: Int, to: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = (from shr shift) and 0xFF
                val b = (to shr shift) and 0xFF
                return (a + (b - a) * amount + 0.5f).toInt()
            }
            return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}

/**
 * The sorting tray: one trough per color in tray order (finished ones at the end), the selected one lifted with
 * a lamp outline and scrolled into view. A wrong-color tap nudges the selected trough 2dp sideways over 150ms.
 */
@Composable
private fun Tray(session: PlaySession) {
    session.placedCount // recompose on every placement: counts, heaps and order change
    val order = session.trayOrder().toList()
    // A finished picture has nothing left to select, so no trough wears the lamp outline.
    val selected = if (session.isFinished) -1 else session.selectedSlot
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val nudge = remember { Animatable(0f) }

    LaunchedEffect(session) {
        snapshotFlow { session.wrongTaps }.drop(1).collect {
            nudge.snapTo(0f)
            nudge.animateTo(
                0f,
                keyframes {
                    durationMillis = NUDGE_MS
                    2f at 40
                    -2f at 100
                },
            )
        }
    }
    val selectedIndex = order.indexOf(selected)
    LaunchedEffect(selected, selectedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val fullyVisible = visible != null && visible.offset >= 0 && visible.offset + visible.size <= viewportEnd
        if (!fullyVisible && selectedIndex >= 0) {
            val itemPx = with(density) { TROUGH_WIDTH.roundToPx() }
            listState.animateScrollToItem(selectedIndex, -(viewportEnd - itemPx) / 2)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(LocalFelt.current.deep),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(order, key = { it }) { slot ->
            val isSelected = slot == selected
            Trough(
                number = slot + 1,
                color = Color(session.pattern.palette[slot].argb or (0xFF shl 24)),
                remaining = session.remaining(slot),
                selected = isSelected,
                nudgeDp = if (isSelected) nudge.value else 0f,
                onClick = { session.select(slot) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun Trough(
    number: Int,
    color: Color,
    remaining: Int,
    selected: Boolean,
    nudgeDp: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = remaining == 0
    val lift by animateDpAsState(if (selected) (-3).dp else 0.dp, tween(LIFT_MS), label = "lift")
    val shape = RoundedCornerShape(8.dp)
    val format = remember { NumberFormat.getIntegerInstance() }
    Column(
        modifier
            .width(TROUGH_WIDTH)
            .graphicsLayer {
                translationY = lift.toPx()
                translationX = nudgeDp * density
            }
            .border(BorderStroke(2.dp, if (selected) GemColors.Lamp else Color.Transparent), shape)
            .clip(shape)
            .clickable(enabled = !finished, role = Role.Button, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Well(color, remaining)
        Spacer(Modifier.height(4.dp))
        Text(
            number.toString(),
            style = GemType.mono.copy(fontSize = 15.sp),
            color = GemColors.Bone,
            textDecoration = if (finished) TextDecoration.LineThrough else null,
        )
        Text(
            if (finished) "" else format.format(remaining),
            style = GemType.mono.copy(fontSize = 12.sp),
            color = GemColors.TextMuted,
            maxLines = 1,
        )
    }
}

/** A recessed 44dp well holding a heap of up to 9 mini gems (one per ~3.5 cells left), or a closed flap when done. */
@Composable
private fun Well(color: Color, remaining: Int) {
    val felt = LocalFelt.current
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val corner = CornerRadius(6.dp.toPx())
        drawRoundRect(felt.ground, cornerRadius = corner)
        if (remaining == 0) {
            drawRoundRect(felt.raised, cornerRadius = corner)
            drawLine(
                felt.deep,
                Offset(4.dp.toPx(), 6.dp.toPx()),
                Offset(size.width - 4.dp.toPx(), 6.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }
        // Inset shadow (0 3dp 6dp, 45% black) at the top of the recess.
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endY = 6.dp.toPx()),
            cornerRadius = corner,
        )
        val gems = heapSize(remaining)
        val gemSize = 9.dp.toPx()
        val step = gemSize + 1.dp.toPx()
        var drawn = 0
        // Bottom row of 4, then 3, then 2, each row centered and resting on the one below.
        for ((rowIndex, rowCount) in HEAP_ROWS.withIndex()) {
            val count = minOf(rowCount, gems - drawn)
            if (count <= 0) break
            val y = size.height - 4.dp.toPx() - gemSize - rowIndex * (gemSize - 1.dp.toPx())
            val x0 = (size.width - count * step + 1.dp.toPx()) / 2f
            for (i in 0 until count) {
                val topLeft = Offset(x0 + i * step, y)
                drawRoundRect(color, topLeft, Size(gemSize, gemSize), CornerRadius(gemSize * 0.18f))
                drawRoundRect(
                    Color.White.copy(alpha = 0.2f),
                    topLeft,
                    Size(gemSize * 0.5f, gemSize * 0.5f),
                    CornerRadius(gemSize * 0.18f),
                )
            }
            drawn += count
        }
    }
}

private fun heapSize(remaining: Int): Int = if (remaining <= 0) 0 else ceil(remaining / CELLS_PER_HEAP_GEM).toInt().coerceIn(1, 9)

private val HEAP_ROWS = intArrayOf(4, 3, 2)
private val TROUGH_WIDTH = 52.dp
private const val CELLS_PER_HEAP_GEM = 3.5
private const val NUDGE_MS = 150
private const val LIFT_MS = 150
private const val TAP_ZOOM_MS = 250
