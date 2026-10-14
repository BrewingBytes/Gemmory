package com.brewingbytes.gemmory.completion

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.nav.Route
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.share.ShareIntents
import com.brewingbytes.gemmory.share.ShareSheet
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.EncodeState
import com.brewingbytes.gemmory.timelapse.FrameArt
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemRenderer
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.NumberFormat

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
)

class CompletionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store
    private val encodes = application.let { it as GemmoryApp }.encodes

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
        val gems = NumberFormat.getIntegerInstance().format(pattern.cells.size)
        val meta = "${opened.title} · $gems gems · ${playTime(opened.record?.playMillis ?: 0L)}"
        val request = EncodeRequest(ref, pattern, opened.state.log(), opened.title, source)
        return Finished(ref, bitmap.asImageBitmap(), before.asImageBitmap(), pattern.width / pattern.height.toFloat(), meta, request)
    }

    /** Share tap (eng D2): starts the encode, or reuses the cached MP4. */
    fun share(ref: CanvasRef) {
        val request = finished?.takeIf { it.ref == ref }?.request ?: return
        killedNotice = false
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
        /** "5 min", "1 h 12 min"; under a minute rounds up to "1 min". */
        fun playTime(millis: Long): String {
            val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
            return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
        }
    }
}

/**
 * Completion (design system, Completion: "lights down"). Fresh: the screen dims ~30% around the picture, one specular
 * sweep crosses it over 2.4s (skipped under reduced motion), then one low chime and the finish haptic, a 2s still
 * hold, then "Finished", the meta line, Share and Done fade in. Reopened from the gallery it's the completion still:
 * no sweep, plus "Play again". Share makes the time-lapse (or reuses it) behind the progress sheet, then opens the
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
    val reducedMotion = rememberReducedMotion()
    val context = LocalContext.current
    val feedback = remember { Feedback(context, (context.applicationContext as GemmoryApp).settings) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    val sequence = fresh && !vm.hasPlayed(ref)
    val dim = remember { Animatable(if (sequence) 0f else DIM) }
    val sweep = remember { Animatable(0f) }
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
        context.startActivity(ShareIntents.chooser(context, video.file, request.medium))
        vm.shareHandled(ref)
    }

    val finished = vm.finished?.takeIf { it.ref == ref }
    LaunchedEffect(finished != null) {
        if (finished == null || !sequence) return@LaunchedEffect
        vm.markPlayed(ref)
        dim.animateTo(DIM, tween(DIM_MS, easing = FastOutSlowInEasing))
        if (!reducedMotion) sweep.animateTo(1f, tween(SWEEP_MS, easing = LinearEasing))
        feedback.pictureFinished()
        delay(HOLD_MS)
        text.animateTo(1f, tween(TEXT_MS))
    }

    Box(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .testTag(if (fresh) "completion-fresh" else "completion-still"),
    ) {
        // Lights down: the room dims, the picture stays lit.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.value)))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    finished != null -> Picture(finished, sweep.value)
                    vm.damaged -> Text(
                        "This canvas's file is damaged, so it can't be opened.",
                        style = GemType.body,
                        color = GemColors.Bone,
                    )
                }
            }
            Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().alpha(text.value).padding(top = 16.dp)) {
                Text("Finished", style = GemType.displayLarge, color = GemColors.Bone)
                Text(finished?.meta.orEmpty(), style = GemType.label, color = GemColors.TextMuted)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    "Share",
                    onClick = { vm.share(ref) },
                    enabled = finished != null,
                    held = encode is EncodeState.Running,
                    modifier = Modifier.fillMaxWidth().testTag("completion-share"),
                )
                if (vm.killedNotice) {
                    Text(
                        "The video stopped when the app closed. Tap Share to try again.",
                        style = GemType.label,
                        color = GemColors.TextMuted,
                        modifier = Modifier.padding(top = 4.dp).testTag("share-killed"),
                    )
                }
                Spacer(Modifier.height(8.dp))
                QuietButton("Done", onDone, Modifier.fillMaxWidth().testTag("completion-done"))
                if (!fresh) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        GemTextButton("Play again", { confirmPlayAgain = true })
                    }
                }
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
    ShareSheet(
        state = encode,
        before = finished?.before,
        after = finished?.image,
        aspect = finished?.aspect ?: 1f,
        onCancel = { vm.cancelShare(ref) },
        onSharePicture = {
            val picture = encode as? EncodeState.Picture
            val request = finished?.request
            if (picture != null && request != null) context.startActivity(ShareIntents.chooser(context, picture.file, request.medium))
            vm.shareHandled(ref)
        },
        onTryAgain = {
            vm.shareHandled(ref)
            vm.share(ref)
        },
        onClose = { vm.shareHandled(ref) },
    )
    if (confirmPlayAgain) {
        ConfirmSheet(
            title = "Start this picture over?",
            body = "Your progress on it is cleared.",
            confirm = "Start over",
            onConfirm = {
                confirmPlayAgain = false
                vm.playAgain(ref, onPlayAgain)
            },
            onDismiss = { confirmPlayAgain = false },
        )
    }
}

/** The finished picture, fit, with one raking specular band at [sweep] (0 → 1 crosses it corner to corner). */
@Composable
private fun Picture(finished: Finished, sweep: Float) {
    Box(Modifier.wrapContentSize().aspectRatio(finished.aspect).clipToBounds()) {
        Image(
            finished.image,
            contentDescription = "Finished picture",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize(),
        )
        if (sweep > 0f && sweep < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                // A 45° band: lines x + y = c, with c moving from before the top-left corner to past the bottom-right.
                val band = (size.width + size.height) * 0.1f
                val c = -band + sweep * (size.width + size.height + 2 * band)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.32f),
                        1f to Color.Transparent,
                        start = Offset((c - band) / 2f, (c - band) / 2f),
                        end = Offset((c + band) / 2f, (c + band) / 2f),
                    ),
                )
            }
        }
    }
}

private const val DIM = 0.3f
private const val DIM_MS = 250
private const val SWEEP_MS = 2_400
private const val HOLD_MS = 2_000L
private const val TEXT_MS = 400
