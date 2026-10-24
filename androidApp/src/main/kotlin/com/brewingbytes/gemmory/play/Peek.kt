package com.brewingbytes.gemmory.play

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemRenderer
import com.brewingbytes.gemmory.ui.LocalFelt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The peek at the finished picture (#35). Holding the top bar's eye shows it until release; a tap shows it for
 * 1.5s; the TalkBack canvas action turns it on until the action is used again. Nothing about it is saved.
 */
@Stable
class Peek(private val scope: CoroutineScope) {
    var shown by mutableStateOf(false)
        private set
    private var timer: Job? = null
    private var pressedAt = 0L

    fun press(now: Long = SystemClock.uptimeMillis()) {
        timer?.cancel()
        pressedAt = now
        shown = true
    }

    /** A press shorter than a tap counts as a tap: the picture stays for 1.5s. */
    fun release(now: Long = SystemClock.uptimeMillis()) = if (now - pressedAt < TAP_MS) tap() else hide()

    fun tap() {
        timer?.cancel()
        shown = true
        timer = scope.launch {
            delay(TAP_SHOW_MS)
            shown = false
        }
    }

    /** The TalkBack action; returns whether the picture now shows. */
    fun toggle(): Boolean {
        if (shown) hide() else {
            timer?.cancel()
            shown = true
        }
        return shown
    }

    fun hide() {
        timer?.cancel()
        shown = false
    }

    private companion object {
        const val TAP_MS = 250L
        const val TAP_SHOW_MS = 1_500L
    }
}

/** The top bar's 48dp eye, labelled "Peek": hold to see the finished picture, or tap for 1.5s. */
@Composable
internal fun PeekButton(peek: Peek, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.action_peek)
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(peek) {
                detectTapGestures(
                    onPress = {
                        peek.press()
                        tryAwaitRelease()
                        peek.release()
                    },
                )
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    peek.tap()
                    true
                }
            }
            .testTag("peek"),
        contentAlignment = Alignment.Center,
    ) {
        Image(GemIcons.Peek, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
    }
}

/**
 * The whole finished picture fitted over the canvas on felt-deep, fading in and out over 150ms (instant under reduced
 * motion, where Compose skips animations). It hides on the first touch, so a tap during a 1.5s peek never places a
 * gem the player can't see. TalkBack doesn't see it: the canvas node and its actions stay underneath.
 */
@Composable
internal fun PeekOverlay(pattern: Pattern, peek: Peek, size: IntSize, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (peek.shown) 1f else 0f, tween(PEEK_FADE_MS), label = "peek")
    // Rendered once per canvas size, off the main thread, in whole pixels per cell so the gems stay crisp.
    val picture by produceState<ImageBitmap?>(null, pattern, size) {
        if (size.width == 0 || size.height == 0) return@produceState
        val cellPx = minOf(size.width / pattern.width, size.height / pattern.height).coerceAtLeast(1)
        value = withContext(Dispatchers.Default) { GemRenderer.render(pattern, cellPx).asImageBitmap() }
    }
    if (alpha == 0f) return
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(LocalFelt.current.deep)
            .pointerInput(peek) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    peek.hide()
                }
            }
            // The tag before the clear: clearAndSetSemantics also drops semantics set after it.
            .testTag("peek-picture")
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        picture?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Inside, filterQuality = FilterQuality.None)
        }
    }
}

private const val PEEK_FADE_MS = 150
