package com.brewingbytes.gemmory.spike

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.brewingbytes.gemmory.compose.res.Res
import com.brewingbytes.gemmory.compose.res.atkinson_hyperlegible_mono
import com.brewingbytes.gemmory.compose.res.probe_percent
import com.brewingbytes.gemmory.compose.res.probe_plural
import com.brewingbytes.gemmory.compose.res.spike_title
import com.brewingbytes.gemmory.game.CanvasGestures
import com.brewingbytes.gemmory.game.CanvasZoom
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.game.TapAction
import com.brewingbytes.gemmory.game.nextRemainingCell
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/**
 * Brackets each canvas draw, so the iOS shell can wrap it in an `os_signpost` interval that an XCTest metric reads
 * (eng D12, the frame gate). Android passes none.
 */
interface FrameProbe {
    fun beginDraw()

    fun endDraw()
}

private val FELT = Color(0xFF4A544D)
private val BONE = Color(0xFFE9E4D8)

/**
 * The Stage 0 spike app (T59): the largest bundled picture on the play canvas with pinch zoom, pan and tap to place,
 * a color tray, save and resume, a timed pan-and-zoom run (the frame gate) and a screen of the Stage 0 checks.
 * [autoBench] starts the timed run at once (the XCTest launches the app with `-bench`).
 */
@Composable
internal fun SpikeApp(files: SpikeFiles, probe: FrameProbe? = null, autoBench: Boolean = false) {
    val gridBytes by produceState<ByteArray?>(null) { value = Res.readBytes("files/pictures/deco-peacock.gmrg") }
    val bytes = gridBytes ?: return Box(Modifier.fillMaxSize().background(FELT))
    val game = remember(bytes) { SpikeGame.open(bytes, files) }
    var checks by remember { mutableStateOf(false) }
    if (checks) {
        ChecksScreen(onBack = { checks = false })
    } else {
        PlayScreen(game, probe, autoBench, onChecks = { checks = true })
    }
}

@Composable
private fun PlayScreen(game: SpikeGame, probe: FrameProbe?, autoBench: Boolean, onChecks: () -> Unit) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val mono = FontFamily(Font(Res.font.atkinson_hyperlegible_mono, FontWeight.Bold))
    val renderer = remember(game, mono) { SpikeRenderer(game.pattern, measurer, mono) }
    val bench = remember { Bench() }
    var benchRun by remember { mutableStateOf(if (autoBench) 1 else 0) }
    val viewport = game.viewport
    val title = stringResource(Res.string.spike_title)

    if (benchRun > 0) {
        LaunchedEffect(benchRun) { bench.run(viewport) }
    }

    Column(Modifier.fillMaxSize().background(FELT).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(title, style = TextStyle(color = BONE, fontSize = 20.sp))
            BasicText(
                "${game.placedCount}/${game.pattern.cells.size}",
                Modifier.testTag("progress"),
                style = TextStyle(color = BONE, fontSize = 16.sp),
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { viewport.onLayout(it.width, it.height, density) }
                .semantics {
                    contentDescription = "$title, ${game.placedCount} of ${game.pattern.cells.size} placed"
                    liveRegion = LiveRegionMode.Polite
                    // Checked with VoiceOver (T59): Compose custom actions should show in the rotor's Actions.
                    customActions = listOf(
                        CustomAccessibilityAction("Place next") {
                            val cell = nextRemainingCell(game.pattern, game::isPlaced, game.selectedSlot, -1)
                            cell != null && game.place(cell) is PlaceResult.Placed
                        },
                        CustomAccessibilityAction("Zoom in") {
                            viewport.set(viewport.zoomDp * 1.5f, viewport.centerX, viewport.centerY)
                            true
                        },
                    )
                }
                .testTag("canvas")
                .pointerInput(viewport) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport.transform(centroid.x, centroid.y, pan.x, pan.y, zoom)
                    }
                }
                .pointerInput(game) {
                    detectTapGestures { at -> tap(game, at) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Read here so the canvas redraws on each change.
                game.placedCount
                game.selectedSlot
                viewport.zoomDp
                viewport.centerX
                viewport.centerY
                drawIntoCanvas { canvas ->
                    probe?.beginDraw()
                    val start = TimeSource.Monotonic.markNow()
                    renderer.draw(canvas, game, viewport, density, HighlightStyle.TINT)
                    bench.drew(start.elapsedNow().inWholeNanoseconds)
                    probe?.endDraw()
                }
            }
        }
        // What the UI test reads (T59): zoom and center move with gestures; placed and rejected with taps.
        val status = "zoom=${fmt1(viewport.zoomDp)} cx=${fmt1(viewport.centerX)} cy=${fmt1(viewport.centerY)} " +
            "placed=${game.placedCount} rejected=${game.rejections} slot=${game.selectedSlot + 1} " +
            "mid=${viewport.cellAt(viewport.width / 2f, viewport.height / 2f)?.let { game.pattern.cells[it] + 1 } ?: 0}"
        BasicText(
            status,
            Modifier.padding(horizontal = 12.dp).testTag("status").semantics { contentDescription = status },
            style = TextStyle(color = BONE, fontSize = 12.sp),
        )
        Tray(game)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button("Bench", "bench") { benchRun++ }
            Button("Checks", "checks", onChecks)
        }
        BasicText(
            bench.result,
            Modifier.padding(horizontal = 12.dp).testTag("bench-result").semantics { contentDescription = bench.result },
            style = TextStyle(color = BONE, fontSize = 12.sp),
        )
    }
}

private fun fmt1(value: Float): String = ((value * 10).toInt() / 10.0).toString()

private fun tap(game: SpikeGame, at: Offset) {
    val viewport = game.viewport
    val cell = viewport.cellAt(at.x, at.y)
    when (CanvasGestures.tapAction(viewport.zoomDp, cell)) {
        TapAction.ZOOM_IN -> viewport.transform(at.x, at.y, 0f, 0f, CanvasZoom.TAP_ZOOM_DP / viewport.zoomDp)
        TapAction.PLACE -> game.place(cell!!)
        TapAction.NONE -> Unit
    }
}

@Composable
private fun Tray(game: SpikeGame) {
    LazyRow(
        Modifier.fillMaxWidth().height(64.dp).testTag("tray"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(game.pattern.palette.indices.toList()) { slot ->
            val color = game.pattern.palette[slot]
            val selected = slot == game.selectedSlot
            Box(
                Modifier.size(if (selected) 52.dp else 44.dp).clip(CircleShape)
                    .background(Color(color.argb or (0xFF shl 24)))
                    .clickable { game.select(slot) }
                    .testTag("slot-$slot"),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "${color.number}",
                    style = TextStyle(color = Color(CellColors.readableOn(color.argb or (0xFF shl 24))), fontSize = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun Button(label: String, tag: String, onClick: () -> Unit) {
    BasicText(
        label,
        Modifier.clip(CircleShape).background(BONE).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(tag),
        style = TextStyle(color = FELT, fontSize = 16.sp),
    )
}

/**
 * The Stage 0 checks that need a screen (T59): plurals and `%%` in the system language, Dynamic Type (the font
 * scale shown live), and the iOS back swipe, which is on only while [NavigationBackHandler] is enabled (design D2).
 */
@Composable
private fun ChecksScreen(onBack: () -> Unit) {
    var backOn by remember { mutableStateOf(true) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backOn,
        onBackCompleted = onBack,
    )
    val fontScale = LocalDensity.current.fontScale
    val style = TextStyle(color = BONE, fontSize = 16.sp)
    Column(Modifier.fillMaxSize().background(FELT).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Checks", style = style.copy(fontSize = 22.sp))
        BasicText("fontScale=$fontScale", Modifier.testTag("font-scale"), style = style)
        BasicText(stringResource(Res.string.probe_percent, 40), Modifier.testTag("percent"), style = style)
        for (n in PLURAL_PROBES) {
            BasicText(pluralStringResource(Res.plurals.probe_plural, n, n), Modifier.testTag("plural-$n"), style = style)
        }
        Button(if (backOn) "Back swipe: on" else "Back swipe: off", "back-toggle") { backOn = !backOn }
        Button("Back", "back", onBack)
    }
}

/** 1 → one; 2, 22 → few in pl and ro; 5 → many in pl; 20 → other in ro; 101 → few in ro. */
internal val PLURAL_PROBES = intArrayOf(0, 1, 2, 5, 20, 22, 101)

/**
 * The frame gate's timed run (eng D12): for [SECONDS], zooms from fit to the largest cells and back while panning
 * across the canvas, one step per frame, and records each frame's interval (from the frame clock: a missed vsync
 * shows as a long one) and the time the canvas's draw took.
 */
private class Bench {
    var result by mutableStateOf("")
        private set
    private var drawNanos = LongArray(0)
    private var draws = 0

    fun drew(nanos: Long) {
        if (draws < drawNanos.size) drawNanos[draws++] = nanos
    }

    suspend fun run(viewport: CanvasViewport) {
        result = "running"
        drawNanos = LongArray(MAX_FRAMES)
        draws = 0
        val intervals = LongArray(MAX_FRAMES)
        var frames = 0
        val start = withFrameNanos { it }
        var last = start
        val fit = viewport.minZoomDp
        val max = CanvasViewport.MAX_ZOOM_DP
        while (true) {
            val now = withFrameNanos { it }
            if (frames < MAX_FRAMES) intervals[frames++] = now - last
            last = now
            val t = (now - start) / 1e9 / SECONDS
            if (t >= 1.0) break
            // Zoom: fit → max → fit, geometric; pan: a lap around the canvas.
            val wave = 0.5 - 0.5 * kotlin.math.cos(2 * kotlin.math.PI * t)
            val zoom = (fit * kotlin.math.exp(kotlin.math.ln(max / fit) * wave)).toFloat()
            val angle = 2 * kotlin.math.PI * t * 2
            val cx = (viewport.columns / 2f) * (1 + 0.8 * kotlin.math.cos(angle)).toFloat()
            val cy = (viewport.rows / 2f) * (1 + 0.8 * kotlin.math.sin(angle)).toFloat()
            viewport.set(zoom, cx, cy)
        }
        val ms = { values: LongArray, n: Int, q: Double ->
            val sorted = values.copyOf(n).sorted()
            if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * q).toInt()] / 1e6
        }
        val long = (1 until frames).count { intervals[it] > LONG_FRAME_NANOS }
        // The first interval is the start-up frame, so it's left out.
        val iv = intervals.copyOfRange(1, frames)
        result = "frames=${frames - 1} interval_p50=${fmt(ms(iv, iv.size, 0.5))} interval_p95=${fmt(ms(iv, iv.size, 0.95))} " +
            "interval_max=${fmt(ms(iv, iv.size, 1.0))} long=$long draw_p50=${fmt(ms(drawNanos, draws, 0.5))} " +
            "draw_p95=${fmt(ms(drawNanos, draws, 0.95))} draw_max=${fmt(ms(drawNanos, draws, 1.0))}"
        println("GemmoryBench $result")
    }

    private fun fmt(value: Double): String = ((value * 100).toLong() / 100.0).toString()

    companion object {
        const val SECONDS = 10.0
        const val MAX_FRAMES = 4_000

        /** More than 1.5 vsyncs at 120 Hz: a frame was missed. */
        const val LONG_FRAME_NANOS = 12_500_000L
    }
}
