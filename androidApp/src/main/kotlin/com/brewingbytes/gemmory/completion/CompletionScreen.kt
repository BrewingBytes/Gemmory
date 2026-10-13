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
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.storage.CanvasRef
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
import kotlinx.coroutines.delay
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

/** The finished picture and its meta line. */
class Finished(val ref: CanvasRef, val image: ImageBitmap, val aspect: Float, val meta: String)

class CompletionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store

    var finished by mutableStateOf<Finished?>(null)
        private set

    var damaged by mutableStateOf(false)
        private set

    private val played = LightsDownTracker()

    fun hasPlayed(ref: CanvasRef) = played.hasPlayed(ref)

    fun markPlayed(ref: CanvasRef) = played.markPlayed(ref)

    /** A canvas was just finished: its lights-down moment plays again, whatever ran for it before. */
    fun onFinished(ref: CanvasRef) {
        played.forget(ref)
        if (finished?.ref == ref) finished = null
    }

    /** Always re-reads, so a rename or a new play time since the last visit shows. The old picture stays until then. */
    fun load(ref: CanvasRef) {
        if (finished?.ref != ref) finished = null
        damaged = false
        viewModelScope.launch {
            when (val opened = store.open(ref)) {
                is CanvasStore.Opened.Damaged -> damaged = true
                is CanvasStore.Opened.Ready -> {
                    val pattern = opened.grid.pattern
                    val bitmap = GemRenderer.render(pattern, cellPx = (720 / pattern.width).coerceIn(4, 24))
                    val gems = NumberFormat.getIntegerInstance().format(pattern.cells.size)
                    val meta = "${opened.title} · $gems gems · ${playTime(opened.record?.playMillis ?: 0L)}"
                    finished = Finished(ref, bitmap.asImageBitmap(), pattern.width / pattern.height.toFloat(), meta)
                }
            }
        }
    }

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
 * no sweep, plus "Play again". Share waits for the time-lapse (#4), so it shows disabled with a note.
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
                PrimaryButton("Share", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                Text(
                    "Video sharing is coming soon.",
                    style = GemType.label,
                    color = GemColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
