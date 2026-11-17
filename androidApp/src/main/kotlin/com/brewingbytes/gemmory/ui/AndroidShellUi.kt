package com.brewingbytes.gemmory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.app.ScreenFeedback
import com.brewingbytes.gemmory.app.ShellUi
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.share.ProgressShareSession
import com.brewingbytes.gemmory.share.ProgressShareHost as AndroidProgressShareHost

/** Android's slots for shared screens (T64): today's share host, and [Feedback] on the composition's view. */
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
                override fun gemPlaced() = feedback.gemPlaced(view)

                override fun milestone() = feedback.milestone(view)
            }
        }
    }
}
