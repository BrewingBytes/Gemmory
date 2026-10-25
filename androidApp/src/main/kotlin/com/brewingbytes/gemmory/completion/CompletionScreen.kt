package com.brewingbytes.gemmory.completion

import android.app.Application
import android.os.SystemClock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.game.FinishCelebration
import com.brewingbytes.gemmory.nav.Route
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.share.ShareIntents
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.share.ShareSheet
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.EncodeState
import com.brewingbytes.gemmory.timelapse.FrameArt
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemRenderer
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.TableCut
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Canvases whose lights-down moment already ran in this process, so a rotation mid-sequence doesn't replay it.
 * A canvas is forgotten whenever it's finished again (after Start over or Play again).
 */
class LightsDownTracker {
    private val played = mutableSetOf<CanvasRef>()

    fun hasPlayed(ref: CanvasRef): Boolean = ref in played

    fun markPlayed(ref: CanvasRef) {
        played += ref
    }

    fun forget(ref: CanvasRef) {
        played -= ref
    }
}

/**
 * The canvas finished just now in this process, so its completion stays "fresh" (lights down, the interstitial check
 * on leaving) across a rotation: the back stack restores every completion as a still, which is right only after
 * process death, where this tracker is gone too. Cleared once the player has left that completion.
 */
class FreshFinishTracker {
    private var ref: CanvasRef? = null

    fun finished(ref: CanvasRef) {
        this.ref = ref
    }

    /** [route] as the player sees it: fresh if the stack says so or [ref] was just finished. */
    fun isFresh(route: Route.Completion): Boolean = route.fresh || route.ref == ref

    /** The player left [route]: true if it was fresh, and it is a still from now on. */
    fun left(route: Route.Completion): Boolean = isFresh(route).also { if (route.ref == ref) ref = null }
}

/**
 * The finished picture and its meta line. [before] is the cropped photo (or the empty tint grid of a bundled
 * picture) for the before/after fallback sheet; [request] is what Share encodes.
 */
class Finished(
    val ref: CanvasRef,
    val image: ImageBitmap,
    val before: ImageBitmap,
    val aspect: Float,
    val meta: String,
    val request: EncodeRequest,
) {
    val cols: Int get() = request.pattern.width
    val rows: Int get() = request.pattern.height

    /** Gems per palette slot and each slot's color, for the confetti (#57). */
    val counts: IntArray = request.pattern.countsPerSlot()
    val colors: List<Color> = request.pattern.palette.map { Color(it.argb) }
}

class CompletionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store
    private val encodes = application.let { it as GemmoryApp }.encodes
    private val billing = (application as GemmoryApp).billing
    private val analytics = (application as GemmoryApp).analytics
    private val app = application as GemmoryApp

    /** "Where did you find Gemmory?" (T56) on this completion: the row, then the thanks line; null when not asked. */
    var whereFound by mutableStateOf<WhereFoundUi?>(null)
        private set

    /** T56's answer sheet is open. */
    var whereFoundSheet by mutableStateOf(false)
        private set

    /** The answer tapped, outlined while its reward lands (design D5). */
    var whereFoundSelected by mutableStateOf<WhereFoundAnswer?>(null)
        private set

    private var whereFoundRef: CanvasRef? = null
    private var whereFoundAnswering = false

    var finished by mutableStateOf<Finished?>(null)
        private set

    var damaged by mutableStateOf(false)
        private set

    /** "The video stopped when the app closed." Shown for this visit, once per killed encode (design D8). */
    var killedNotice by mutableStateOf(false)
        private set

    private val played = LightsDownTracker()

    /** Survives rotation, not process death (see [FreshFinishTracker]). */
    val fresh = FreshFinishTracker()

    fun encodeState(ref: CanvasRef): StateFlow<EncodeState> = encodes.state(ref)

    /** The completion screen was left: the killed-encode notice belonged to that visit. Kept across rotation. */
    fun onLeft() {
        killedNotice = false
    }

    fun hasPlayed(ref: CanvasRef) = played.hasPlayed(ref)

    /**
     * A fresh completion of [ref] opened (T56): decide the question once per canvas here, so a rotation keeps the same
     * answer. The first-run rule, the consent check and the save come first ([com.brewingbytes.gemmory.telemetry.FirstRun]).
     */
    fun decideWhereFound(ref: CanvasRef) {
        if (whereFoundRef == ref) return
        whereFoundRef = ref
        val granted = app.whereFoundConsentForTest ?: app.consent.consentMode.value.analyticsStorage
        val ask = app.firstRun.firstRunAsk(granted, Random.nextLong())
        if (ask is FirstRunAsk.Show) whereFound = WhereFoundUi.Row(ask.order, vip = app.monetization.entitlements.value.vip)
    }

    /** T56's row is on the screen (the text has faded in). */
    fun whereFoundShown() = app.firstRun.whereFoundShown()

    fun openWhereFound() {
        if (whereFound is WhereFoundUi.Row) whereFoundSheet = true
    }

    /**
     * An answer, or Skip, was tapped: counted once (later taps do nothing), logged, then for an answer the reward is
     * added and, once it has and the outline has shown for [WHERE_FOUND_HOLD_MS], the row becomes the thanks line.
     */
    fun answerWhereFound(answer: WhereFoundAnswer) {
        if (whereFoundAnswering || !app.firstRun.whereFoundAnswered(answer)) return
        whereFoundAnswering = true
        analytics.log(AnalyticsEvent.whereFound(answer))
        if (answer == WhereFoundAnswer.SKIP) {
            whereFoundSheet = false
            whereFound = null
            return
        }
        whereFoundSelected = answer
        viewModelScope.launch {
            val start = SystemClock.uptimeMillis()
            val paid = app.monetization.earnCanvasOnce(WHERE_FOUND_EARN_ID)
            if (paid) analytics.log(AnalyticsEvent.inventoryEarned(Item.PHOTO_CANVAS, EarnSource.WHERE_FOUND, 1))
            delay((WHERE_FOUND_HOLD_MS - (SystemClock.uptimeMillis() - start)).coerceAtLeast(0))
            whereFoundSheet = false
            whereFoundSelected = null
            whereFound = WhereFoundUi.Thanks(paid)
        }
    }

    /** Back, a swipe or the scrim closed the sheet: a skip, with no event; after an answer it only closes it. */
    fun dismissWhereFound() {
        whereFoundSheet = false
        if (whereFoundAnswering) return
        if (app.firstRun.whereFoundAnswered(WhereFoundAnswer.SKIP)) {
            whereFoundAnswering = true
            whereFound = null
        }
    }

    fun markPlayed(ref: CanvasRef) = played.markPlayed(ref)

    /** A canvas was just finished: its lights-down moment plays again, whatever ran for it before. */
    fun onFinished(ref: CanvasRef) {
        fresh.finished(ref)
        played.forget(ref)
        if (finished?.ref == ref) finished = null
    }

    /** Always re-reads, so a rename or a new play time since the last visit shows. The old picture stays until then. */
    fun load(ref: CanvasRef) {
        if (finished?.ref != ref) {
            finished = null
            killedNotice = false
        }
        damaged = false
        viewModelScope.launch {
            if (encodes.takeKilledNotice(ref)) killedNotice = true
            when (val opened = store.open(ref)) {
                is CanvasStore.Opened.Damaged -> damaged = true
                is CanvasStore.Opened.Ready -> finished = withContext(Dispatchers.Default) { finishedOf(ref, opened) }
            }
        }
    }

    private fun finishedOf(ref: CanvasRef, opened: CanvasStore.Opened.Ready): Finished {
        val pattern = opened.grid.pattern
        val cellPx = (720 / pattern.width).coerceIn(4, 24)
        val bitmap = GemRenderer.render(pattern, cellPx)
        val source = store.repository.sourceFile(ref).takeIf { ref.kind == CanvasKind.PHOTO && it.isFile }
        val before = source?.let { FrameArt.decodeSource(it, bitmap.width, bitmap.height) }
            ?: GemRenderer.render(pattern, cellPx) { false }
        val strings = getApplication<GemmoryApp>().strings
        val meta = strings.text(R.string.completion_meta, opened.title, strings.number(pattern.cells.size), playTime(strings, opened.record?.playMillis ?: 0L))
        val request = EncodeRequest(ref, pattern, opened.state.log(), opened.title, source, watermark = true)
        return Finished(ref, bitmap.asImageBitmap(), before.asImageBitmap(), pattern.width / pattern.height.toFloat(), meta, request)
    }

    /**
     * Share tap (eng D2): starts the encode, or reuses the cached MP4. VIP gets no watermark, and the cache key
     * carries the flag, so a video made before VIP isn't reused.
     */
    fun share(ref: CanvasRef) {
        val request = finished?.takeIf { it.ref == ref }?.request?.withWatermark(!billing.entitlements.value.vip) ?: return
        killedNotice = false
        analytics.log(AnalyticsEvent.shareTapped(ref, request.pattern.width, request.pattern.height, progress = false))
        encodes.share(request)
    }

    fun cancelShare(ref: CanvasRef) = encodes.cancel(ref)

    /** The share sheet was opened for the result, or the player closed the message. */
    fun shareHandled(ref: CanvasRef) = encodes.acknowledge(ref)

    /** Play again (design D3): the Start over confirm, then progress, view and play time are cleared. */
    fun playAgain(ref: CanvasRef, then: () -> Unit) {
        viewModelScope.launch {
            store.startOver(ref)
            played.forget(ref)
            finished = null
            then()
        }
    }

    companion object {
        /** T56's reward, earned once per install (Inventory's earn id). */
        const val WHERE_FOUND_EARN_ID = "where-found"

        /** The tapped answer's outline holds at least this long (design D5). */
        const val WHERE_FOUND_HOLD_MS = 400L

        /** "5 min", "1 h 12 min"; under a minute rounds up to "1 min". */
        fun playTime(s: Strings, millis: Long): String {
            val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
            return if (minutes < 60) s.text(R.string.play_time_minutes, minutes) else s.text(R.string.play_time_hours, minutes / 60, minutes % 60)
        }
    }
}

/** T56 on the completion screen. */
sealed interface WhereFoundUi {
    /** The row under Done; [order] is the sheet's answers; no reward line for [vip]. */
    data class Row(val order: List<WhereFoundAnswer>, val vip: Boolean) : WhereFoundUi

    /** The thanks line; it names the photo canvas only when the reward was really added ([paid]). */
    data class Thanks(val paid: Boolean) : WhereFoundUi
}

/**
 * Completion (design system, Completion: "lights down" and the finish celebration, #57). Fresh: the screen dims ~30%
 * around the picture, a sparkle sweep glints every gem over 2.4s over a rising haptic crescendo, the chime lands, gem
 * confetti in the picture's colors falls through the room during the 2s hold, then "Finished", the meta line, Share
 * and Done fade in. Reduced motion keeps the dim, the chime, the soft finish haptic and the hold only. Reopened from
 * the gallery it's the completion still: no celebration, plus "Play again". Share makes the time-lapse (or reuses it) behind the progress sheet, then opens the
 * Android share sheet; a cancelled or dismissed share comes back here with no message (design D8).
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
    val context = LocalContext.current
    val feedback = remember { Feedback(context, (context.applicationContext as GemmoryApp).settings) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

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
        val request = vm.finished?.takeIf { it.ref == ref }?.request ?: return@LaunchedEffect
        context.startActivity(ShareIntents.chooser(context, video.file, request, video.feed))
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
                            stringResource(R.string.play_damaged),
                            style = GemType.body,
                            color = GemColors.Bone,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(max = textMax)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).alpha(text.value).padding(top = 16.dp)) {
                        Text(stringResource(R.string.completion_title), style = GemType.displayLarge, color = GemColors.Bone)
                        Text(finished?.meta.orEmpty(), style = GemType.label, color = GemColors.TextMuted)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(
                            stringResource(R.string.action_share),
                            onClick = { vm.share(ref) },
                            enabled = finished != null,
                            held = encode is EncodeState.Running,
                            modifier = Modifier.fillMaxWidth().testTag(SHARE_TAG),
                        )
                        if (vm.killedNotice) {
                            Text(
                                stringResource(R.string.completion_killed),
                                style = GemType.label,
                                color = GemColors.TextMuted,
                                modifier = Modifier.padding(top = 4.dp).testTag("share-killed"),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        QuietButton(stringResource(R.string.action_done), onDone, Modifier.fillMaxWidth().testTag(DONE_TAG))
                        if (!fresh) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                GemTextButton(stringResource(R.string.completion_play_again), { confirmPlayAgain = true }, Modifier.testTag(PLAY_AGAIN_TAG))
                            }
                        }
                        if (fresh) WhereFoundBlock(vm)
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
    val whereFound = vm.whereFound
    LaunchedEffect(whereFound is WhereFoundUi.Row, text.value >= 1f) {
        if (whereFound is WhereFoundUi.Row && text.value >= 1f) vm.whereFoundShown()
    }
    if (vm.whereFoundSheet && whereFound is WhereFoundUi.Row) WhereFoundSheet(whereFound, vm)
    ShareSheet(
        state = encode,
        before = finished?.before,
        after = finished?.image,
        aspect = finished?.aspect ?: 1f,
        onCancel = { vm.cancelShare(ref) },
        onSharePicture = {
            val picture = encode as? EncodeState.Picture
            val request = finished?.request
            if (picture != null && request != null) context.startActivity(ShareIntents.chooser(context, picture.file, request))
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
            title = stringResource(R.string.gallery_start_over_title),
            body = stringResource(R.string.gallery_start_over_body),
            confirm = stringResource(R.string.action_start_over),
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
 * under it (not for VIP); after an answer, the thanks line in its place, which TalkBack focus lands on.
 */
@Composable
private fun WhereFoundBlock(vm: CompletionViewModel) {
    when (val wf = vm.whereFound) {
        is WhereFoundUi.Row -> {
            val question = stringResource(R.string.where_found_question)
            val reward = stringResource(R.string.where_found_reward)
            Spacer(Modifier.height(24.dp))
            QuietButton(
                question,
                vm::openWhereFound,
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
                stringResource(if (wf.paid) R.string.where_found_thanks_canvas else R.string.where_found_thanks),
                style = GemType.body,
                color = GemColors.Bone,
                modifier = Modifier.alpha(fade.value).testTag(WHERE_FOUND_THANKS_TAG),
            )
        }
        null -> Unit
    }
}

/**
 * T56's answers (eng R3): the question as the sheet's title, the reward line, the five answers as full-width quiet
 * buttons in this install's order (labels wrap; TalkBack hears "1 of 5"), then Skip. A tapped answer keeps the lamp
 * outline while its reward lands; focus then goes to the thanks line, or to Done after Skip or a dismiss.
 */
@Composable
private fun WhereFoundSheet(row: WhereFoundUi.Row, vm: CompletionViewModel) {
    GemSheet(
        title = stringResource(R.string.where_found_question),
        onDismiss = vm::dismissWhereFound,
        returnFocusTo = if (vm.whereFoundSelected != null) WHERE_FOUND_THANKS_TAG else DONE_TAG,
    ) {
        if (!row.vip) Text(stringResource(R.string.where_found_reward), style = GemType.label, color = GemColors.TextMuted)
        Column(Modifier.fillMaxWidth().semantics { collectionInfo = CollectionInfo(row.order.size, 1) }) {
            row.order.forEachIndexed { index, answer ->
                QuietButton(
                    stringResource(answer.label()),
                    { vm.answerWhereFound(answer) },
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
                        .testTag("$WHERE_FOUND_ANSWER_TAG-${answer.value}"),
                    selected = vm.whereFoundSelected == answer,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GemTextButton(stringResource(R.string.where_found_skip), { vm.answerWhereFound(WhereFoundAnswer.SKIP) }, Modifier.testTag(WHERE_FOUND_SKIP_TAG))
        }
    }
}

private fun WhereFoundAnswer.label(): Int = when (this) {
    WhereFoundAnswer.VIDEO -> R.string.where_found_video
    WhereFoundAnswer.COMMUNITY -> R.string.where_found_community
    WhereFoundAnswer.FRIEND -> R.string.where_found_friend
    WhereFoundAnswer.PLAY_SEARCH -> R.string.where_found_play_search
    WhereFoundAnswer.OTHER -> R.string.where_found_other
    WhereFoundAnswer.SKIP -> R.string.where_found_skip
}

/**
 * The finished picture, fit, with the sparkle sweep at [sweep] (0 → 1, #57): every gem, or every tile of gems on a
 * canvas too fine to glint one by one, catches the light in turn along the diagonal from top-left to bottom-right,
 * some with a small facet star, over a faint raking band.
 */
@Composable
private fun Picture(finished: Finished, sweep: Float, modifier: Modifier = Modifier) {
    Box(Modifier.wrapContentSize().aspectRatio(finished.aspect).clipToBounds().then(modifier)) {
        Image(
            finished.image,
            contentDescription = stringResource(R.string.completion_picture),
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
