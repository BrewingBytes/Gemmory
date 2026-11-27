package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.app.LocalShellUi
import com.brewingbytes.gemmory.app.ShellUi
import com.brewingbytes.gemmory.copy.LocalWords
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.brewingbytes.gemmory.ads.LocalAdSpots
import com.brewingbytes.gemmory.ads.Spot
import com.brewingbytes.gemmory.ads.TapLook
import com.brewingbytes.gemmory.ads.TapSpot
import com.brewingbytes.gemmory.ads.onHalfVisible
import com.brewingbytes.gemmory.policy.Inventory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.game.FinishCelebration
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.share.ShareSheet
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.timelapse.EncodeState
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.render.TableCut
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Completion (design system, Completion: "lights down" and the finish celebration, #57). Fresh: the screen dims ~30%
 * around the picture, a sparkle sweep glints every gem over 2.4s over a rising haptic crescendo, the chime lands, gem
 * confetti in the picture's colors falls through the room during the 2s hold, then "Finished", the meta line, Share
 * and Done fade in. Reduced motion keeps the dim, the chime, the soft finish haptic and the hold only. Reopened from
 * the gallery it's the completion still: no celebration, plus "Play again". Share makes the time-lapse (or reuses it)
 * behind the progress sheet, then opens the platform's share sheet ([ShellUi.rememberFinishedShare]); a cancelled or
 * dismissed share comes back here with no message (design D8). Shared since iOS Stage 1c (T64): haptics and sounds
 * come from the shell's [ShellUi.rememberFeedback].
 */
@Composable
fun CompletionScreen(
    ref: CanvasRef,
    fresh: Boolean,
    vm: CompletionViewModel,
    onDone: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    LaunchedEffect(ref) { vm.load(ref) }
    // T56: decided before the text fades in, so the row is in the column from the start and the picture never resizes.
    LaunchedEffect(ref, fresh) { if (fresh) vm.decideWhereFound(ref) }
    val reducedMotion = rememberReducedMotion()
    val words = LocalWords.current
    val shell = LocalShellUi.current
    // Released when the screen leaves, which also stops a crescendo still rising mid-sequence.
    val feedback = shell.rememberFeedback()
    val shareSheet = shell.rememberFinishedShare()

    // The completion bonus (1.0.7, DR-D3/D4): its slot is laid out from the first frame when the rules allow it, so the
    // picture never resizes; whether it shows is decided once, when Share and Done have faded in (its video ready then).
    val spots = LocalAdSpots.current
    val bonusSlot = remember(ref, fresh) { fresh && spots.bonusAllowed(ref) }
    DisposableEffect(bonusSlot) {
        if (bonusSlot) spots.want(Spot.BONUS)
        onDispose { spots.unwant(Spot.BONUS) }
    }
    var bonusOffer by remember(ref) { mutableStateOf<Boolean?>(null) }

    val sequence = fresh && !vm.hasPlayed(ref)
    val dim = remember { Animatable(if (sequence) 0f else DIM) }
    val sweep = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    var room by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var picture by remember { mutableStateOf<Rect?>(null) }
    val text = remember { Animatable(if (sequence) 0f else 1f) }
    var confirmPlayAgain by rememberSaveable { mutableStateOf(false) }
    val encode by remember(ref) { vm.encodeState(ref) }.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // A finished video goes to the share sheet once the app is in front again (it may have finished in the background)
    // and the canvas is loaded (a recreated activity loads it again).
    val loaded = vm.finished?.ref == ref
    LaunchedEffect(encode, loaded) {
        val video = encode as? EncodeState.Video ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        val finished = vm.finished?.takeIf { it.ref == ref } ?: return@LaunchedEffect
        shareSheet.open(finished, video)
        vm.shareHandled(ref)
    }

    val finished = vm.finished?.takeIf { it.ref == ref }
    LaunchedEffect(finished != null) {
        if (finished == null || !sequence) return@LaunchedEffect
        vm.markPlayed(ref)
        // Reduced motion (D21) skips the sparkle, the crescendo and the confetti; the dim and the text are fades and
        // stay, at their real duration, and the chime and the soft finish haptic play right after the dim.
        withFullMotion { dim.animateTo(DIM, tween(DIM_MS, easing = FastOutSlowInEasing)) }
        if (!reducedMotion) {
            // The celebration (#57): the haptic rises under the sparkle and peaks on the chime.
            feedback.crescendo()
            sweep.animateTo(1f, tween(FinishCelebration.SWEEP_MS, easing = LinearEasing))
        }
        feedback.pictureFinished()
        // The confetti falls through the hold, so Share and Done come no later than before.
        if (!reducedMotion) launch { confetti.animateTo(1f, tween(FinishCelebration.CONFETTI_MS, easing = LinearEasing)) }
        delay(HOLD_MS)
        withFullMotion { text.animateTo(1f, tween(TEXT_MS)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .onGloballyPositioned { room = it }
            .testTag(if (fresh) "completion-fresh" else "completion-still"),
    ) {
        // Lights down: the room dims, the picture stays lit.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.value)))
        val bounds = picture
        if (finished != null && bounds != null && confetti.value > 0f && confetti.value < 1f) {
            // Behind the picture, so it falls through the room and never over the picture.
            Confetti(finished, bounds, roomWidth = room?.size?.width ?: 0, confetti.value * FinishCelebration.CONFETTI_MS)
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            // At 200% font the text and buttons may need more than they get: they scroll, the picture keeps the rest.
            val textMax = maxHeight * TEXT_MAX_SHARE
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        finished != null -> Picture(
                            finished,
                            sweep.value,
                            Modifier.onGloballyPositioned { coordinates ->
                                picture = room?.takeIf { it.isAttached }?.localBoundingBoxOf(coordinates)
                            },
                        )
                        vm.damaged -> Text(
                            words.text("play_damaged"),
                            style = GemType.body,
                            color = GemColors.Bone,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(max = textMax)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).alpha(text.value).padding(top = 16.dp)) {
                        Text(words.text("completion_title"), style = GemType.displayLarge, color = GemColors.Bone)
                        Text(finished?.meta.orEmpty(), style = GemType.label, color = GemColors.TextMuted)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            words.text("action_share"),
                            onClick = { vm.share(ref) },
                            enabled = finished != null,
                            held = encode is EncodeState.Running,
                            modifier = Modifier.fillMaxWidth().testTag(SHARE_TAG),
                        )
                        if (vm.killedNotice) {
                            Text(
                                words.text("completion_killed"),
                                style = GemType.label,
                                color = GemColors.TextMuted,
                                modifier = Modifier.padding(top = 4.dp).testTag("share-killed"),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        QuietButton(words.text("action_done"), onDone, Modifier.fillMaxWidth().testTag(DONE_TAG))
                        if (!fresh) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                GemTextButton(words.text("completion_play_again"), { confirmPlayAgain = true }, Modifier.testTag(PLAY_AGAIN_TAG))
                            }
                        }
                        if (bonusSlot) {
                            Box(Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = BONUS_SLOT_MIN), contentAlignment = Alignment.TopCenter) {
                                if (bonusOffer == true) {
                                    val sharing = encode is EncodeState.Running || encode is EncodeState.Video || encode is EncodeState.Picture
                                    TapSpot(
                                        words.plural("inventory_watch", Inventory.FILLS_PER_BONUS, Inventory.FILLS_PER_BONUS),
                                        TapLook.TEXT,
                                        play = { spots.playBonus(ref) },
                                        enabled = !sharing,
                                        disabledReason = words.text("spot_unavailable_sharing"),
                                        tag = BONUS_TAG,
                                        modifier = Modifier.onHalfVisible { spots.seen(Spot.BONUS) },
                                    )
                                }
                            }
                        }
                        if (fresh || vm.whereFoundOn(ref)) WhereFoundBlock(vm.whereFound, revealed = text.value >= 1f)
                    }
                    // Until they have faded in, the buttons can't be pressed unseen; system back still works.
                    if (text.value < 1f) {
                        Box(
                            Modifier.matchParentSize().pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    // Decided once (DR-D4): never inserted after the text has faded in.
    LaunchedEffect(bonusSlot, text.value >= 1f) {
        if (bonusSlot && text.value >= 1f && bonusOffer == null) bonusOffer = Spot.BONUS in spots.ready.value
    }
    val whereFound = vm.whereFound.ui
    LaunchedEffect(whereFound is WhereFoundUi.Row, text.value >= 1f) {
        if (whereFound is WhereFoundUi.Row && text.value >= 1f) vm.whereFoundShown()
    }
    if (vm.whereFound.sheet && whereFound is WhereFoundUi.Row) WhereFoundSheet(whereFound, vm.whereFound, reducedMotion)
    ShareSheet(
        state = encode,
        before = finished?.before,
        after = finished?.image,
        aspect = finished?.aspect ?: 1f,
        onCancel = { vm.cancelShare(ref) },
        onSharePicture = {
            val picture = encode as? EncodeState.Picture
            if (picture != null && finished != null) shareSheet.open(finished, picture)
            vm.shareHandled(ref)
        },
        onTryAgain = {
            vm.shareHandled(ref)
            vm.share(ref)
        },
        onClose = { vm.shareHandled(ref) },
        returnFocusTo = SHARE_TAG,
    )
    if (confirmPlayAgain) {
        ConfirmSheet(
            title = words.text("gallery_start_over_title"),
            body = words.text("gallery_start_over_body"),
            confirm = words.text("action_start_over"),
            onConfirm = {
                confirmPlayAgain = false
                vm.playAgain(ref, onPlayAgain)
            },
            onDismiss = { confirmPlayAgain = false },
            returnFocusTo = PLAY_AGAIN_TAG,
        )
    }
}

/**
 * "Where did you find Gemmory?" (T56, design D3-D12, eng R3): 24dp under Done, one quiet button with the reward line
 * under it (not for VIP); after an answer, the thanks line in its place, which TalkBack focus lands on. It's laid out
 * from the first frame, so the picture never resizes, but until the text has faded in ([revealed], eng R2) it has no
 * semantics and can't be tapped: TalkBack can't reach it and tests don't find it.
 */
@Composable
private fun WhereFoundBlock(flow: WhereFoundFlow, revealed: Boolean) {
    val words = LocalWords.current
    // The block keeps the tallest it has been, so the shorter thanks line, or nothing after Skip, doesn't resize the
    // picture above it.
    var held by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = with(density) { held.toDp() })
            .onSizeChanged { if (it.height > held) held = it.height }
            .then(if (revealed) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
    when (val wf = flow.ui) {
        is WhereFoundUi.Row -> {
            val question = words.text("where_found_question")
            val reward = words.text("where_found_reward")
            Spacer(Modifier.height(24.dp))
            QuietButton(
                question,
                { if (revealed) flow.open() },
                Modifier
                    .fillMaxWidth()
                    .testTag(WHERE_FOUND_ROW_TAG)
                    .semantics { contentDescription = if (wf.vip) question else "$question $reward" },
            )
            if (!wf.vip) {
                Text(reward, style = GemType.label, color = GemColors.TextMuted, modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { })
            }
        }
        is WhereFoundUi.Thanks -> {
            // A 150ms fade, also under reduced motion (D5).
            val fade = remember(wf) { Animatable(0f) }
            LaunchedEffect(wf) { withFullMotion { fade.animateTo(1f, tween(WHERE_FOUND_FADE_MS)) } }
            Spacer(Modifier.height(24.dp))
            Text(
                words.text(if (wf.paid) "where_found_thanks_canvas" else "where_found_thanks"),
                style = GemType.body,
                color = GemColors.Bone,
                modifier = Modifier.alpha(fade.value).testTag(WHERE_FOUND_THANKS_TAG),
            )
        }
        null -> Unit
    }
    }
}

/**
 * T56's answers (eng R3): the question as the sheet's title, the reward line, the five answers as full-width quiet
 * buttons in this install's order (labels wrap; TalkBack hears "1 of 5"), then Skip. A tapped answer keeps the lamp
 * outline while its reward lands; focus then goes to the thanks line, or to Done after Skip or a dismiss.
 */
@Composable
private fun WhereFoundSheet(row: WhereFoundUi.Row, flow: WhereFoundFlow, reducedMotion: Boolean) {
    val words = LocalWords.current
    GemSheet(
        title = words.text("where_found_question"),
        onDismiss = flow::dismiss,
        // Read at the close: with reduced motion an answer closes the sheet in the same frame it is tapped, before
        // the sheet recomposes, so a value passed in would still say Done (#215).
        returnFocusAtClose = { if (flow.answering) WHERE_FOUND_THANKS_TAG else DONE_TAG },
    ) {
        if (!row.vip) Text(words.text("where_found_reward"), style = GemType.label, color = GemColors.TextMuted)
        Column(Modifier.fillMaxWidth().semantics { collectionInfo = CollectionInfo(row.order.size, 1) }) {
            row.order.forEachIndexed { index, answer ->
                QuietButton(
                    words.text(answer.label()),
                    { flow.answer(answer, reducedMotion) },
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
                        .testTag("$WHERE_FOUND_ANSWER_TAG-${answer.value}"),
                    selected = flow.selected == answer,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GemTextButton(words.text("where_found_skip"), { flow.answer(WhereFoundAnswer.SKIP, reducedMotion) }, Modifier.testTag(WHERE_FOUND_SKIP_TAG))
        }
    }
}

/** The string name of this answer's label. */
private fun WhereFoundAnswer.label(): String = when (this) {
    WhereFoundAnswer.VIDEO -> "where_found_video"
    WhereFoundAnswer.COMMUNITY -> "where_found_community"
    WhereFoundAnswer.FRIEND -> "where_found_friend"
    WhereFoundAnswer.PLAY_SEARCH -> "where_found_play_search"
    WhereFoundAnswer.OTHER -> "where_found_other"
    WhereFoundAnswer.SKIP -> "where_found_skip"
}

/**
 * The finished picture, fit, with the sparkle sweep at [sweep] (0 → 1, #57): every gem, or every tile of gems on a
 * canvas too fine to glint one by one, catches the light in turn along the diagonal from top-left to bottom-right,
 * some with a small facet star, over a faint raking band.
 */
@Composable
private fun Picture(finished: Finished, sweep: Float, modifier: Modifier = Modifier) {
    val words = LocalWords.current
    Box(Modifier.wrapContentSize().aspectRatio(finished.aspect).clipToBounds().then(modifier)) {
        Image(
            finished.image,
            contentDescription = words.text("completion_picture"),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize(),
        )
        if (sweep > 0f && sweep < 1f) {
            Canvas(Modifier.fillMaxSize().testTag(SPARKLE_TAG)) {
                val tile = FinishCelebration.tileCells(size.width / finished.cols, MIN_GLINT_DP.dp.toPx())
                val tilePx = size.width / finished.cols * tile
                val cols = (finished.cols + tile - 1) / tile
                val rows = (finished.rows + tile - 1) / tile
                val star = 1.dp.toPx()
                for (row in 0 until rows) for (col in 0 until cols) {
                    val glint = FinishCelebration.glint(sweep, col, row, cols, rows)
                    if (glint <= 0f) continue
                    val left = col * tilePx
                    val top = row * tilePx
                    drawRect(Sparkle, Offset(left, top), Size(tilePx, tilePx), alpha = GLINT_ALPHA * glint)
                    if (FinishCelebration.jitter(row * cols + col) > FinishCelebration.STAR_JITTER) {
                        val center = Offset(left + tilePx / 2, top + tilePx / 2)
                        val reach = tilePx * STAR_REACH * glint
                        drawLine(Color.White, center - Offset(reach, 0f), center + Offset(reach, 0f), star, alpha = 0.9f * glint)
                        drawLine(Color.White, center - Offset(0f, reach), center + Offset(0f, reach), star, alpha = 0.9f * glint)
                    }
                }
                // The old sweep's band, faint, carries the eye along the diagonal.
                val band = (size.width + size.height) * 0.1f
                val c = -band + sweep * (size.width + size.height + 2 * band)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = BAND_ALPHA),
                        1f to Color.Transparent,
                        start = Offset((c - band) / 2f, (c - band) / 2f),
                        end = Offset((c + band) / 2f, (c + band) / 2f),
                    ),
                )
            }
        }
    }
}

/**
 * Gem confetti (#57) [ms] after the chime: square table-cut gems (T40) in the picture's own colors, spilling out from under
 * [picture] (its bounds in this layer, [roomWidth] px wide) and raining from above the screen, tumbling as they fall.
 */
@Composable
private fun Confetti(finished: Finished, picture: Rect, roomWidth: Int, ms: Float) {
    val density = LocalDensity.current
    val gems = remember(finished, picture, roomWidth) {
        with(density) {
            FinishCelebration.confetti(
                finished.counts,
                pictureLeft = picture.left.toDp().value,
                pictureTop = picture.top.toDp().value,
                pictureWidth = picture.width.toDp().value,
                pictureHeight = picture.height.toDp().value,
                roomWidth = roomWidth.toDp().value,
                seed = finished.ref.hashCode(),
            )
        }
    }
    val cut = remember { TableCut.UnitPaths(TableCut.NARROW_BEVEL, CONFETTI_CATCHLIGHT) }
    Canvas(Modifier.fillMaxSize().testTag(CONFETTI_TAG)) {
        val dp = density.density
        for (gem in gems) {
            val at = FinishCelebration.at(gem, ms) ?: continue
            withTransform({
                translate(at.x * dp, at.y * dp)
                rotate(at.angle * RADIANS_TO_DEGREES, pivot = Offset.Zero)
                scale(at.tumble * at.size * dp, at.size * dp, pivot = Offset.Zero)
            }) {
                drawRect(finished.colors[gem.slot], Offset(-0.5f, -0.5f), Size(1f, 1f), alpha = at.alpha)
                for ((path, argb) in cut.layers) drawPath(path, Color(argb), alpha = at.alpha)
            }
        }
    }
}

private const val DIM = 0.3f

/** The bonus offer's slot (DR-D4): a 48dp text button plus a line under it, held from the first frame. */
private val BONUS_SLOT_MIN = 72.dp

const val BONUS_TAG = "completion-bonus"
private const val DIM_MS = 250
private const val HOLD_MS = 2_000L
private const val TEXT_MS = 400
private const val TEXT_MAX_SHARE = 0.6f
private const val SHARE_TAG = "completion-share"
private const val DONE_TAG = "completion-done"
const val WHERE_FOUND_ROW_TAG = "where-found-row"
const val WHERE_FOUND_THANKS_TAG = "where-found-thanks"
const val WHERE_FOUND_ANSWER_TAG = "where-found-answer"
const val WHERE_FOUND_SKIP_TAG = "where-found-skip"
private const val WHERE_FOUND_FADE_MS = 150
private const val PLAY_AGAIN_TAG = "completion-play-again"
private const val SPARKLE_TAG = "finish-sparkle"
private const val CONFETTI_TAG = "finish-confetti"
private const val MIN_GLINT_DP = 4
private const val GLINT_ALPHA = 0.55f
private const val BAND_ALPHA = 0.14f
private const val STAR_REACH = 1.3f

/** The ≥ 20dp sprite's catchlight (0.8dp of a 20dp gem) as a share of a confetti gem's size, so its lit top shows. */
private const val CONFETTI_CATCHLIGHT = 0.04f
private const val RADIANS_TO_DEGREES = 57.29578f
private val Sparkle = Color(0xFFFFFAEB)
