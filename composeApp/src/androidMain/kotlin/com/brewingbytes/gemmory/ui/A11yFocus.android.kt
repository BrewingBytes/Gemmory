package com.brewingbytes.gemmory.ui

import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull

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

@Composable
actual fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** TalkBack's focus, through the Compose view's accessibility node provider ([moveAccessibilityFocus]). */
@Composable
actual fun rememberAccessibilityFocus(): AccessibilityFocus {
    val view = LocalView.current
    return remember(view) {
        object : AccessibilityFocus {
            override fun moveTo(tag: String): Boolean = view.moveAccessibilityFocus(tag)
            override fun returnTo(tag: String, cancelled: () -> Boolean) = view.returnAccessibilityFocus(tag, cancelled)
        }
    }
}
