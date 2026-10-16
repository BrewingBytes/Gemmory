package com.brewingbytes.gemmory.play

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.game.CanvasGestures
import com.brewingbytes.gemmory.game.CanvasZoom
import com.brewingbytes.gemmory.game.GameEvent
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.game.Rejection
import com.brewingbytes.gemmory.game.TapAction
import com.brewingbytes.gemmory.game.TapOutcome
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.ceil

/**
 * The play canvas (design system, Play canvas): top bar with back, title and mono progress; the canvas on
 * felt-deep; the sorting tray at the bottom. No ads and no other chrome. When the last gem is placed the chrome
 * slides away over 250ms and [onFinished] hands over to the completion screen ("lights down").
 */
@Composable
fun PlayScreen(ref: CanvasRef, onBack: () -> Unit, onFinished: () -> Unit, vm: PlayViewModel = viewModel()) {
    LaunchedEffect(ref) { vm.open(ref) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    val leave = {
        vm.close()
        onBack()
    }
    BackHandler(onBack = leave)

    val felt = LocalFelt.current
    val reducedMotion = rememberReducedMotion()
    val chrome = remember { Animatable(1f) }
    // Reduced motion (D21): the chrome fades over 150ms instead of sliding away.
    val slide = if (reducedMotion) 0f else 1f
    val ui = vm.ui
    val session = (ui as? PlayUi.Ready)?.session
    // Finishing the last gem, or reopening a canvas that was finished before its completion showed.
    val finished = session != null && session.placedCount == session.cellCount
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        if (reducedMotion) {
            withFullMotion { chrome.animateTo(0f, tween(REDUCED_FADE_MS)) }
        } else {
            chrome.animateTo(0f, tween(CHROME_OUT_MS, easing = FastOutLinearInEasing))
        }
        vm.close()
        onFinished()
    }
    Column(
        Modifier
            .fillMaxSize()
            .feltGround(felt.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (ui) {
            PlayUi.Loading -> {
                TopBar("", progress = null, onBack = leave)
                Box(Modifier.weight(1f).fillMaxWidth().background(felt.deep))
            }
            PlayUi.Damaged -> {
                TopBar("", progress = null, onBack = leave)
                Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center) {
                    Text("This canvas's file is damaged, so it can't be opened.", style = GemType.body, color = GemColors.Bone)
                    Spacer(Modifier.height(16.dp))
                    QuietButton("Back", leave, Modifier.fillMaxWidth())
                }
            }
            is PlayUi.Ready -> {
                val s = ui.session
                val context = LocalContext.current
                val view = LocalView.current
                val scope = rememberCoroutineScope()
                val settings = (context.applicationContext as GemmoryApp).settings
                val milestonesOn by settings.milestones.collectAsStateWithLifecycle()
                SideEffect { s.milestonesOn = milestonesOn }
                val peek = remember(s) { Peek(scope) }
                TopBar(
                    s.title,
                    progress = s.placedCount to s.cellCount,
                    onBack = leave,
                    peek = peek,
                    modifier = Modifier.graphicsLayer {
                        alpha = chrome.value
                        translationY = -(1f - chrome.value) * size.height * slide
                    },
                )
                val diskFull by s.diskFull.collectAsStateWithLifecycle()
                if (diskFull) {
                    Text(
                        "Progress isn't saving: phone storage is full.",
                        style = GemType.label,
                        color = GemColors.Bone,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                val feedback = remember { Feedback(context, settings) }
                DisposableEffect(feedback) { onDispose { feedback.release() } }
                val controller = remember(s) { CanvasController(s, scope, feedback, view, vm.hints, reducedMotion) }
                val window = LocalWindowInfo.current.containerSize
                // Large screens (design D22): at 600dp and wider in landscape the tray is a 2-column rail on the right.
                val rail = with(LocalDensity.current) { window.width.toDp() >= RAIL_MIN_WIDTH } && window.width > window.height
                if (rail) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        GemCanvas(s, controller, vm.hints, peek, Modifier.weight(1f).fillMaxHeight())
                        Box(
                            Modifier.fillMaxHeight().graphicsLayer {
                                alpha = chrome.value
                                translationX = (1f - chrome.value) * size.width * slide
                            },
                        ) { Tray(s, controller, rail = true, reducedMotion) }
                    }
                } else {
                    GemCanvas(s, controller, vm.hints, peek, Modifier.weight(1f).fillMaxWidth())
                    Box(
                        Modifier.graphicsLayer {
                            alpha = chrome.value
                            translationY = (1f - chrome.value) * size.height * slide
                        },
                    ) { Tray(s, controller, rail = false, reducedMotion) }
                }
            }
        }
    }
}

/**
 * Top bar (design D20): the title ellipsizes to one line; above 1.5× font scale progress shows as a mono "59%". The
 * Peek eye (#35) sits between the title and the progress.
 */
@Composable
private fun TopBar(title: String, progress: Pair<Int, Int>?, onBack: () -> Unit, modifier: Modifier = Modifier, peek: Peek? = null) {
    val largeFont = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(GemIcons.Back, "Back", onBack)
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = GemType.display,
            color = GemColors.Bone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (peek != null) PeekButton(peek, Modifier.padding(start = 4.dp))
        if (progress != null) {
            val format = remember { NumberFormat.getIntegerInstance() }
            val (placed, total) = progress
            Text(
                if (largeFont) "${placed * 100L / maxOf(1, total)}%" else "${format.format(placed)} / ${format.format(total)}",
                style = GemType.mono.copy(fontSize = 14.sp),
                color = GemColors.Bone,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp).testTag("play-progress"),
            )
        }
    }
}

/**
 * Everything the canvas, the tray's Find button and the hints share: zoom animations, gesture outcomes, glints and
 * pulses. Lives as long as the session's screen.
 */
@Stable
private class CanvasController(
    private val session: PlaySession,
    private val scope: CoroutineScope,
    val feedback: Feedback,
    val view: View,
    private val hints: CoachHints,
    val reducedMotion: Boolean,
) : CanvasInputHandler {
    val effects = CanvasEffects()

    /** The frame clock while an effect runs; the canvas redraws on every change. */
    var frameNanos by mutableLongStateOf(System.nanoTime())
        private set
    private val viewport = session.viewport
    private var animation: Job? = null
    private var ticker: Job? = null

    override fun onTouch() {
        animation?.cancel()
    }

    override fun onTap(at: Offset): TapOutcome {
        val cell = viewport.cellAt(at.x, at.y)
        return when (CanvasGestures.tapAction(viewport.zoomDp, cell)) {
            TapAction.NONE -> TapOutcome.OTHER
            TapAction.ZOOM_IN -> {
                // Below 16dp a tap never places: it zooms to 24dp cells centered on the tap.
                val (x, y) = viewport.pointAt(at.x, at.y)
                animateTo(CanvasZoom.TAP_ZOOM_DP, x, y)
                hints.onTapZoom()
                TapOutcome.ZOOMED_IN
            }
            TapAction.PLACE -> when (val result = session.place(cell!!)) {
                is PlaceResult.Placed -> {
                    placed(result)
                    TapOutcome.PLACED
                }
                is PlaceResult.Rejected -> {
                    if (result.reason == Rejection.WRONG_COLOR) feedback.wrongColor(view)
                    TapOutcome.OTHER
                }
            }
        }
    }

    override fun onDoubleTap(at: Offset) {
        val fit = viewport.minZoomDp
        val target = CanvasZoom.doubleTapTarget(viewport.zoomDp, fit)
        if (target == fit) {
            animateTo(fit, viewport.columns / 2f, viewport.rows / 2f)
        } else {
            val (x, y) = viewport.pointAt(at.x, at.y)
            animateTo(target, x, y)
        }
    }

    override fun onLongPress(cell: Int) = session.select(session.pattern.cells[cell])

    override fun onPaint(cell: Int, slot: Int) {
        // A drag paints only its own color: once that color finishes and the next one is selected, it stops.
        if (session.selectedSlot != slot || session.pattern.cells[cell] != slot || session.isPlaced(cell)) return
        (session.place(cell) as? PlaceResult.Placed)?.let(::placed)
    }

    /** Find (design D24): pan and zoom to 24dp cells on the next remaining cell, then pulse it once. */
    fun find(): Boolean {
        val cell = session.findNext() ?: return false
        val columns = viewport.columns
        animateTo(CanvasZoom.TAP_ZOOM_DP, cell % columns + 0.5f, cell / columns + 0.5f) {
            if (!reducedMotion) {
                effects.pulse(cell, System.nanoTime())
                tick()
            }
        }
        return true
    }

    /** TalkBack "Place next gem" (design D19): placed like a tap, with the same haptic, sound and glint. */
    fun placeNext(): Boolean {
        val result = session.placeNext() as? PlaceResult.Placed ?: return false
        placed(result)
        return true
    }

    /** TalkBack "Zoom in" / "Zoom out": one step of the zoom table around the middle of the view. */
    fun zoomStep(zoomIn: Boolean): Boolean {
        val target = CanvasZoom.step(viewport.zoomDp, viewport.minZoomDp, zoomIn) ?: return false
        animateTo(target, viewport.centerX, viewport.centerY)
        return true
    }

    /** TalkBack "Whole picture": back to fit. */
    fun wholePicture(): Boolean {
        animateTo(viewport.minZoomDp, viewport.columns / 2f, viewport.rows / 2f)
        return true
    }

    private fun placed(result: PlaceResult.Placed) {
        for (event in result.events) {
            when (event) {
                is GameEvent.GemPlaced -> {
                    feedback.gemPlaced(view)
                    if (!reducedMotion && viewport.zoomDp >= CanvasZoom.FACET_MIN_DP) {
                        effects.glint(event.cell, System.nanoTime())
                        tick()
                    }
                }
                is GameEvent.ColorFinished -> feedback.colorFinished(view)
                // The picture-finished chime and haptic play on the completion screen, at their step of "lights down".
                GameEvent.PictureFinished -> Unit
            }
        }
        hints.onPlaced()
    }

    private fun animateTo(zoomDp: Float, centerX: Float, centerY: Float, then: () -> Unit = {}) {
        animation?.cancel()
        val fromZoom = viewport.zoomDp
        val fromX = viewport.centerX
        val fromY = viewport.centerY
        animation = scope.launch {
            animate(0f, 1f, animationSpec = tween(ZOOM_MS, easing = FastOutSlowInEasing)) { t, _ ->
                viewport.set(fromZoom + (zoomDp - fromZoom) * t, fromX + (centerX - fromX) * t, fromY + (centerY - fromY) * t)
            }
            then()
        }
    }

    private fun tick() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (effects.isActive(System.nanoTime())) withFrameNanos { frameNanos = it }
            frameNanos = System.nanoTime()
        }
    }
}

@Composable
private fun GemCanvas(session: PlaySession, controller: CanvasController, hints: CoachHints, peek: Peek, modifier: Modifier) {
    val viewport = session.viewport
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(session.pattern) { CanvasRenderer(context, session.pattern) }
    // Read in the draw block, so a change in Settings redraws an open canvas at once (#19).
    val highlight by (context.applicationContext as GemmoryApp).settings.highlight.collectAsStateWithLifecycle()

    LaunchedEffect(session, hints) {
        snapshotFlow { viewport.isLaidOut }.first { it }
        hints.onOpened(viewport.zoomDp)
        snapshotFlow { viewport.zoomDp }.collect { hints.onZoom(it) }
    }
    val hint = hints.current
    LaunchedEffect(hint) {
        if (hint == null) return@LaunchedEffect
        delay(HINT_MS)
        hints.expire(hint)
    }

    // TalkBack (design D19): the canvas is one node with six actions instead of a node per cell. Its description
    // changes at most once per placement. The peek action (#35) reads "Back to canvas" while the picture shows.
    val format = remember { NumberFormat.getIntegerInstance() }
    val slot = session.selectedSlot
    val description = A11yText.canvas(session.placedCount, session.cellCount, slot + 1, session.remaining(slot), format)
    val peeking = peek.shown
    val actions = remember(controller, peeking) {
        listOf(
            CustomAccessibilityAction("Place next gem") { controller.placeNext() },
            CustomAccessibilityAction("Find next gem") { controller.find() },
            CustomAccessibilityAction("Zoom in") { controller.zoomStep(zoomIn = true) },
            CustomAccessibilityAction("Zoom out") { controller.zoomStep(zoomIn = false) },
            CustomAccessibilityAction("Whole picture") { controller.wholePicture() },
            CustomAccessibilityAction(if (peeking) "Back to canvas" else "Peek at picture") {
                session.announcePeek(peek.toggle())
                true
            },
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { canvasSize = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("play-canvas")
                .semantics {
                    contentDescription = description
                    customActions = actions
                }
                .clipToBounds()
                .background(LocalFelt.current.deep)
                .onSizeChanged { viewport.onLayout(it.width, it.height, density) }
                .canvasInput(session, controller),
        ) {
            // Read so the canvas redraws on every placement, selection change and effect frame; zoom and pan are
            // read inside draw().
            session.placedCount
            session.selectedSlot
            val now = controller.frameNanos
            val style = highlight
            drawIntoCanvas { renderer.draw(it.nativeCanvas, session, density, style, controller.effects, now) }
        }
        Crossfade(hint, Modifier.align(Alignment.TopCenter).padding(top = 12.dp), label = "hint") { shown ->
            if (shown != null) HintChip(shown.text)
        }
        // Over the hint chip, so the peek shows only the finished picture; under the banner, so a milestone still shows.
        PeekOverlay(session.pattern, peek, canvasSize)
        MilestoneBanner(session, controller.feedback, controller.view, controller.reducedMotion)
        Announcer(session)
    }
}

/**
 * The one polite live region for the canvas's announcements (design D19; eng re-check: not the deprecated
 * announceForAccessibility). It has nothing to draw; it is cleared a moment after it speaks, so TalkBack doesn't
 * find a stale message while exploring.
 */
@Composable
private fun Announcer(session: PlaySession) {
    val announcement = session.announcement
    LaunchedEffect(announcement) {
        if (announcement == null) return@LaunchedEffect
        delay(ANNOUNCEMENT_MS)
        session.announced(announcement)
    }
    Box(
        Modifier
            .size(1.dp)
            .testTag("announcer")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                // A changed description is what makes TalkBack speak; a trailing space on every other announcement
                // makes the same words ("Not color 7" twice) a change too.
                if (announcement != null) contentDescription = announcement.text + if (announcement.id % 2 == 0) " " else ""
            },
    )
}

/** A first-time hint (design D12): an inline bone label on a felt-deep chip over the canvas, read by TalkBack. */
@Composable
private fun HintChip(text: String) {
    Text(
        text,
        style = GemType.label,
        color = GemColors.Bone,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(LocalFelt.current.deep)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("coach-hint")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
/**
 * The sorting tray: one trough per color in tray order (finished ones at the end), the selected one lifted with
 * a lamp outline and scrolled into view. A wrong-color tap nudges the selected trough 2dp sideways over 150ms.
 * Find sits at the tray's start while the selected color has 20 or fewer cells left (design D24). As a [rail]
 * (large screens, D22) the troughs stack in two columns with Find on top.
 */
@Composable
private fun Tray(session: PlaySession, controller: CanvasController, rail: Boolean, reducedMotion: Boolean) {
    session.placedCount // recompose on every placement: counts, heaps and order change
    val order = session.trayOrder().toList()
    // A finished picture has nothing left to select, so no trough wears the lamp outline.
    val selected = if (session.isFinished) -1 else session.selectedSlot
    val showFind = !session.isFinished && session.findVisible
    val nudge = remember { Animatable(0f) }
    // Reduced motion (D21): the nudge becomes a 120ms blink of the lamp outline (the haptic stays).
    var blink by remember { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale
    // Font scale (design D20): troughs grow with the font up to 72dp; above 1.5× the count hides.
    val troughWidth = (TROUGH_WIDTH * fontScale).coerceIn(TROUGH_WIDTH, TROUGH_MAX_WIDTH)
    val showCount = fontScale <= LARGE_FONT_SCALE

    LaunchedEffect(session) {
        snapshotFlow { session.wrongTaps }.drop(1).collect {
            if (reducedMotion) {
                blink = true
                delay(BLINK_MS)
                blink = false
                return@collect
            }
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
    val trough: @Composable (Int, Modifier) -> Unit = { slot, modifier ->
        val isSelected = slot == selected
        Trough(
            number = slot + 1,
            color = Color(session.pattern.palette[slot].argb or (0xFF shl 24)),
            remaining = session.remaining(slot),
            selected = isSelected,
            outline = isSelected && !blink,
            instantLift = reducedMotion,
            width = troughWidth,
            showCount = showCount,
            nudgeDp = if (isSelected) nudge.value else 0f,
            onClick = { session.select(slot) },
            modifier = modifier,
        )
    }
    val deep = LocalFelt.current.deep

    if (rail) {
        val gridState = rememberLazyGridState()
        LaunchedEffect(selected, selectedIndex) {
            if (selectedIndex < 0) return@LaunchedEffect
            val visible = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            val end = gridState.layoutInfo.viewportEndOffset
            if (visible == null || visible.offset.y < 0 || visible.offset.y + visible.size.height > end) {
                gridState.animateScrollToItem(selectedIndex)
            }
        }
        // Find stays pinned at the top of the rail, outside the scrolling troughs.
        Column(Modifier.fillMaxHeight().width(troughWidth * 2 + 8.dp + 24.dp).background(deep).testTag("tray-rail")) {
            if (showFind) FindButton(controller::find, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(order, key = { it }) { slot -> trough(slot, Modifier.animateItem()) }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    LaunchedEffect(selected, selectedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val fullyVisible = visible != null && visible.offset >= 0 && visible.offset + visible.size <= viewportEnd
        if (!fullyVisible && selectedIndex >= 0) {
            val itemPx = with(density) { troughWidth.roundToPx() }
            listState.animateScrollToItem(selectedIndex, -(viewportEnd - itemPx) / 2)
        }
    }
    Row(Modifier.fillMaxWidth().background(deep).testTag("tray"), verticalAlignment = Alignment.CenterVertically) {
        if (showFind) FindButton(controller::find, Modifier.padding(start = 16.dp))
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = if (showFind) 8.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(order, key = { it }) { slot -> trough(slot, Modifier.animateItem()) }
        }
    }
}

/** Find (design D24): a 48dp quiet icon button labelled "Find". */
@Composable
private fun FindButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(LocalFelt.current.raised)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Find" }
            .testTag("find"),
        contentAlignment = Alignment.Center,
    ) {
        Image(GemIcons.Find, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
    }
}

@Composable
private fun Trough(
    number: Int,
    color: Color,
    remaining: Int,
    selected: Boolean,
    outline: Boolean,
    instantLift: Boolean,
    width: Dp,
    showCount: Boolean,
    nudgeDp: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = remaining == 0
    val lift by animateDpAsState(if (selected) (-3).dp else 0.dp, if (instantLift) snap() else tween(LIFT_MS), label = "lift")
    val shape = RoundedCornerShape(8.dp)
    val format = remember { NumberFormat.getIntegerInstance() }
    Column(
        modifier
            .width(width)
            .graphicsLayer {
                translationY = lift.toPx()
                translationX = nudgeDp * density
            }
            .testTag("trough-$number")
            .border(BorderStroke(2.dp, if (outline) GemColors.Lamp else Color.Transparent), shape)
            .clip(shape)
            .clickable(enabled = !finished, role = Role.Button, onClick = onClick)
            // One node that reads "Color 7, 312 left" (D19), even when the count is hidden at large font (D20).
            .clearAndSetSemantics {
                contentDescription = A11yText.trough(number, remaining, format)
                this.selected = selected
                lampOutline = outline
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Well(color, remaining)
        Spacer(Modifier.height(4.dp))
        Text(
            number.toString(),
            style = GemType.mono.copy(fontSize = 15.sp),
            color = GemColors.Bone,
            maxLines = 1,
            softWrap = false,
            textDecoration = if (finished) TextDecoration.LineThrough else null,
        )
        if (showCount) {
            Text(
                if (finished) "" else format.format(remaining),
                style = GemType.mono.copy(fontSize = 12.sp),
                color = GemColors.TextMuted,
                maxLines = 1,
            )
        }
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

/** Whether a trough wears its lamp outline right now; tests read it to see the reduced-motion blink (D21). */
internal val LampOutline = SemanticsPropertyKey<Boolean>("LampOutline")
private var SemanticsPropertyReceiver.lampOutline by LampOutline

private val HEAP_ROWS = intArrayOf(4, 3, 2)
private val TROUGH_WIDTH = 52.dp
private val TROUGH_MAX_WIDTH = 72.dp
private const val LARGE_FONT_SCALE = 1.5f
private val RAIL_MIN_WIDTH = 600.dp
private const val HINT_MS = 6_000L
private const val CELLS_PER_HEAP_GEM = 3.5
private const val NUDGE_MS = 150
private const val LIFT_MS = 150
private const val ZOOM_MS = 250
private const val CHROME_OUT_MS = 250
private const val REDUCED_FADE_MS = 150
private const val BLINK_MS = 120L
private const val ANNOUNCEMENT_MS = 3_000L
