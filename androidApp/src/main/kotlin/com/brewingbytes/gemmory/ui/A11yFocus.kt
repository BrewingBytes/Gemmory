package com.brewingbytes.gemmory.ui

import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Moves TalkBack's focus to the node tagged [tag] in this Compose view's window (design system, Accessibility:
 * sheets move focus to their title and back to the trigger). Compose has no API for accessibility focus, so this asks
 * the view's accessibility node provider, as TalkBack itself would. Does nothing without a screen reader, or when no
 * node has the tag (the trigger may be gone, e.g. a deleted tile).
 */
fun View.moveAccessibilityFocus(tag: String): Boolean {
    val id = semanticsIdOf(tag) ?: return false
    return accessibilityNodeProvider?.performAction(id, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) == true
}

/**
 * Gives TalkBack's focus back to the node tagged [tag] after a sheet's window closes. TalkBack moves its focus on its
 * own when that window goes away, a moment after the sheet is gone, so for a short while the focus is asked for again
 * whenever the trigger doesn't have it, or isn't there yet. Stops early when [cancelled] says another sheet opened.
 */
fun View.returnAccessibilityFocus(tag: String, cancelled: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + RETURN_WINDOW_MS
    val check = object : Runnable {
        override fun run() {
            if (cancelled()) return
            val provider = accessibilityNodeProvider ?: return
            // The node may come a moment after the sheet goes (T56's thanks line, after its 400ms hold): keep asking.
            val id = semanticsIdOf(tag)
            if (id != null && provider.createAccessibilityNodeInfo(id)?.isAccessibilityFocused != true) moveAccessibilityFocus(tag)
            if (SystemClock.uptimeMillis() < deadline) postDelayed(this, RETURN_STEP_MS)
        }
    }
    postDelayed(check, RETURN_STEP_MS)
}

private fun View.semanticsIdOf(tag: String): Int? =
    (this as? RootForTest)?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = true)
        ?.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }?.id

private const val RETURN_STEP_MS = 150L
private const val RETURN_WINDOW_MS = 1_200L

/**
 * Animations at their real duration even when the system animator scale is 0 (design D21). "Remove animations" sets
 * that scale, and Compose would otherwise finish every animation at once; the reduced-motion fades and blinks must
 * still be seen.
 */
private object FullMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

suspend fun <T> withFullMotion(block: suspend CoroutineScope.() -> T): T = withContext(FullMotion, block)
