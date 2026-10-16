package com.brewingbytes.gemmory.play

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.game.Milestones
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.withFullMotion
import java.text.NumberFormat

/**
 * The progress milestone banner (#35, founder pick "B3", design system Play canvas and Motion): a felt-deep band
 * across the middle of the canvas with lamp edges. The bone "50%" tag snaps in from the left (300ms), then the
 * headline wipes open beside it (360ms); the band holds and fades out, 3s in all. Under reduced motion the band
 * fades in and out over 150ms, 2.5s in all. Touches pass through it, and TalkBack hears it from the live region,
 * not from the band. The chime and haptic play as it starts.
 */
@Composable
internal fun MilestoneBanner(session: PlaySession, feedback: Feedback, view: View, reducedMotion: Boolean) {
    val milestone = session.milestone
    var shown by remember { mutableStateOf<Milestone?>(null) }
    val clock = remember { Animatable(0f) }
    val total = if (reducedMotion) REDUCED_TOTAL_MS else TOTAL_MS
    LaunchedEffect(milestone) {
        // A milestone already cued (a rotation mid-banner) doesn't start again.
        if (milestone == null || !session.cue(milestone)) return@LaunchedEffect
        feedback.milestone(view)
        shown = milestone
        clock.snapTo(0f)
        withFullMotion { clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) }
        shown = null
    }
    val current = shown ?: return
    val format = remember { NumberFormat.getIntegerInstance() }
    val count = "${format.format(Milestones.threshold(current.percent, session.cellCount))} / ${format.format(session.cellCount)} gems"

    fun t() = clock.value
    fun phase(from: Int, to: Int): Float = ((t() - from) / (to - from)).coerceIn(0f, 1f)
    val deep = LocalFelt.current.deep
    // The tag before the clear: clearAndSetSemantics also drops semantics set after it, and everything inside.
    Box(Modifier.fillMaxSize().testTag("milestone-banner").clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .graphicsLayer {
                    alpha = if (reducedMotion) {
                        minOf(phase(0, REDUCED_FADE_MS), 1f - phase(total - REDUCED_FADE_MS, total))
                    } else {
                        1f - phase(total - FADE_OUT_MS, total)
                    }
                }
                .shadow(8.dp, RectangleShape)
                .background(deep)
                .drawBehind {
                    val edge = EDGE.toPx()
                    drawLine(GemColors.Lamp, Offset(0f, edge / 2), Offset(size.width, edge / 2), edge)
                    drawLine(GemColors.Lamp, Offset(0f, size.height - edge / 2), Offset(size.width, size.height - edge / 2), edge)
                }
                .padding(vertical = EDGE),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .graphicsLayer {
                        if (!reducedMotion) {
                            translationX = -(1f - LinearOutSlowInEasing.transform(phase(0, TAG_IN_MS))) * size.width * 1.2f
                        }
                    }
                    .background(GemColors.Bone)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${current.percent}%", style = GemType.mono.copy(fontSize = 20.sp), color = GemColors.OnBone)
            }
            Column(
                Modifier
                    .weight(1f)
                    .drawWithContent {
                        val open = if (reducedMotion) 1f else FastOutSlowInEasing.transform(phase(TAG_IN_MS, WIPE_END_MS))
                        clipRect(right = size.width * open) { this@drawWithContent.drawContent() }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(A11yText.milestone(current.percent), style = GemType.display, color = GemColors.Bone)
                Text(count, style = GemType.mono.copy(fontSize = 12.sp), color = GemColors.TextMuted)
            }
        }
    }
}

private val EDGE = 2.dp
private const val TOTAL_MS = 3_000
private const val TAG_IN_MS = 300
private const val WIPE_END_MS = 660
private const val FADE_OUT_MS = 300
private const val REDUCED_TOTAL_MS = 2_500
private const val REDUCED_FADE_MS = 150
