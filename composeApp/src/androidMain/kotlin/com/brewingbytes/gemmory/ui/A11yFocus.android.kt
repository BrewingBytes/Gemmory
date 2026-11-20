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
 * Puts TalkBack's focus on the node tagged [tag] and keeps it there for a short while: a sheet's title when it opens,
 * the trigger when it closes. TalkBack moves its focus on its own when a window comes or goes, a moment later (later
 * still on a slow phone), so the focus is asked for again whenever the node doesn't have it, until it has stayed there
 * for [HOLD_WINDOW_MS]. The node may come late (T56's thanks line, after its 400ms hold), and the whole wait is capped
 * at [GIVE_UP_MS]. Stops early when [cancelled] says another sheet opened or this one closed.
 */
fun View.holdAccessibilityFocus(tag: String, cancelled: () -> Boolean) {
    val start = SystemClock.uptimeMillis()
    var lastMove = start
    val check = object : Runnable {
        override fun run() {
            if (cancelled()) return
            val provider = accessibilityNodeProvider ?: return
            val now = SystemClock.uptimeMillis()
            val id = semanticsIdOf(tag)
            if (id == null || provider.createAccessibilityNodeInfo(id)?.isAccessibilityFocused != true) {
                if (id != null) moveAccessibilityFocus(tag)
                lastMove = now
            }
            if (now - lastMove < HOLD_WINDOW_MS && now - start < GIVE_UP_MS) postDelayed(this, FOCUS_STEP_MS)
        }
    }
    post(check)
}

private fun View.semanticsIdOf(tag: String): Int? =
    (this as? RootForTest)?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = true)
        ?.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }?.id

private const val FOCUS_STEP_MS = 150L
private const val HOLD_WINDOW_MS = 1_200L
private const val GIVE_UP_MS = 5_000L

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
            override fun holdOn(tag: String, cancelled: () -> Boolean) = view.holdAccessibilityFocus(tag, cancelled)
        }
    }
}
