package com.brewingbytes.gemmory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.app.LegacyCanvas
import com.brewingbytes.gemmory.app.ScreenFeedback
import com.brewingbytes.gemmory.app.ShellUi
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.play.CanvasEffects
import com.brewingbytes.gemmory.play.CanvasRenderer
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.share.ProgressShareSession
import com.brewingbytes.gemmory.share.ProgressShareHost as AndroidProgressShareHost

/**
 * Android's slots for shared screens (T64): today's share host, [Feedback] on the composition's view, and the old
 * canvas renderer while a hotfix switches the shared one off (R10-8).
 */
object AndroidShellUi : ShellUi {
    @Composable
    override fun ProgressShareHost(session: ProgressShareSession, returnFocusTo: String?) = AndroidProgressShareHost(session, returnFocusTo)

    @Composable
    override fun rememberFeedback(): ScreenFeedback {
        val context = LocalContext.current
        val view = LocalView.current
        val feedback = remember { Feedback(context, (context.applicationContext as GemmoryApp).graph.settings) }
        DisposableEffect(feedback) { onDispose { feedback.release() } }
        return remember(feedback, view) {
            object : ScreenFeedback {
                override fun gemPlaced(drag: Boolean) = feedback.gemPlaced(view, drag)

                override fun milestone() = feedback.milestone(view)

                override fun sweep(durationMs: Int) = feedback.sweep(view, durationMs)

                override fun sweepClick(step: Int) = feedback.sweepClick(step)

                override fun wrongColor() = feedback.wrongColor(view)

                override fun colorFinished() = feedback.colorFinished(view)
            }
        }
    }

    @Composable
    override fun rememberLegacyCanvas(pattern: Pattern, mystery: Boolean): LegacyCanvas? {
        if (BuildConfig.SHARED_RENDERER) return null
        val context = LocalContext.current
        return remember(pattern, mystery) {
            val renderer = CanvasRenderer(context, pattern, mystery)
            object : LegacyCanvas {
                override fun draw(canvas: Canvas, session: PlaySession, density: Float, style: HighlightStyle, effects: CanvasEffects, nowNanos: Long) =
                    renderer.draw(canvas.nativeCanvas, session, density, style, effects, nowNanos)
            }
        }
    }
}
