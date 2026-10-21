package com.brewingbytes.gemmory.streak

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The flame's two shapes on a 24-unit square (mockup v3), parsed once. */
private object FlamePaths {
    val outer: Path = PathParser().parsePathString(
        "M12 2c1 3.5-1.5 5-1.5 7.5 0 1.6 1.2 2.6 2.4 2.6 1.8 0 2.6-1.6 2.1-3.6C17.6 10.4 19 13 19 15.3 19 19 15.9 22 12 22s-7-3-7-6.7c0-4.4 4.5-6.2 7-13.3z",
    ).toPath()
    val core: Path = PathParser().parsePathString(
        "M12 13.5c.4 1.4-.8 2.2-.8 3.4 0 .9.7 1.5 1.5 1.5 1.2 0 1.9-1 1.6-2.4.9.7 1.4 1.7 1.4 2.6 0 1.9-1.6 3.4-3.6 3.4s-3.6-1.5-3.6-3.4c0-2.2 2.2-3.2 3.5-5.1z",
    ).toPath()
}

/** The lit flame's color for a streak of [length] days (G1): amber, orange from 30, red-orange from 100. */
fun flameColor(length: Int): Color = when {
    length >= 100 -> GemColors.Flame100
    length >= 30 -> GemColors.Flame30
    else -> GemColors.Flame
}

/**
 * The streak flame (#101, G1): flat, no glow. Grey ember until today's picture is finished ([lit] false), then the
 * [length]'s color. It flickers in a 1.6s loop at 40dp and up unless [still]; [ignite] plays the 800ms grey-to-lit
 * scale-up once. Reduced motion: neither, the flame is drawn as it ends. Decorative: callers describe it.
 */
@Composable
fun Flame(lit: Boolean, length: Int, size: Dp, modifier: Modifier = Modifier, still: Boolean = false, ignite: Boolean = false) {
    val reduced = rememberReducedMotion()
    val color = if (lit) flameColor(length) else GemColors.Ember
    val core = if (lit) GemColors.FlameCore else GemColors.EmberCore
    val igniting = remember { Animatable(if (ignite && lit && !reduced) 0f else 1f) }
    LaunchedEffect(ignite, lit) {
        if (ignite && lit && !reduced) igniting.animateTo(1f, tween(IGNITE_MS, easing = FastOutSlowInEasing))
    }
    val flickers = lit && !still && !reduced && size >= FLICKER_FROM
    // Flicker (mockup v3): the flame breathes taller and narrower and back, its core on its own faster beat.
    // Read while drawing only, so a flickering flame redraws without recomposing.
    val phases: Pair<State<Float>, State<Float>>? = if (flickers) {
        val t = rememberInfiniteTransition(label = "flame")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(FLICKER_MS, easing = LinearEasing)), label = "outer") to
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(CORE_MS, easing = LinearEasing)), label = "core")
    } else {
        null
    }
    Canvas(modifier.size(size).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val p = igniting.value
        // Ignite: 0.25 → 1.14 at 55%, then 1; grey becomes the color by 55%.
        val grow = if (p < 0.55f) 0.25f + (1.14f - 0.25f) * (p / 0.55f) else 1.14f - 0.14f * ((p - 0.55f) / 0.45f)
        val tint = (p / 0.55f).coerceAtMost(1f)
        val unit = this.size.minDimension / 24f
        val pivot = Offset(this.size.width / 2, this.size.height * 0.95f)
        val wave = sin(2 * PI * (phases?.first?.value ?: 0f)).toFloat()
        val pulse = (1 - cos(2 * PI * (phases?.second?.value ?: 0f)).toFloat()) / 2
        scale((1 - 0.025f * wave) * grow, (1 + 0.04f * wave) * grow, pivot) {
            scale(unit, unit, Offset.Zero) {
                drawPath(FlamePaths.outer, lerp(GemColors.Ember, color, tint))
                scale(1 - 0.12f * pulse, 1 + 0.14f * pulse, Offset(12f, 22.8f)) {
                    drawPath(FlamePaths.core, lerp(GemColors.EmberCore, core, tint))
                }
            }
        }
    }
}

private val FLICKER_FROM = 40.dp
private const val FLICKER_MS = 1_600
private const val CORE_MS = 1_100
private const val IGNITE_MS = 800
