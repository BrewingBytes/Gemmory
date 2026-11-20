package com.brewingbytes.gemmory.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Reduced motion (design system, Motion): Android's animator scale is 0 ("Remove animations"); iOS's Reduce Motion
 * is on (design system, iOS).
 */
@Composable
expect fun rememberReducedMotion(): Boolean

/**
 * Animations at their real duration even when the system animator scale is 0 (design D21). "Remove animations" sets
 * that scale, and Compose would otherwise finish every animation at once; the reduced-motion fades and blinks must
 * still be seen.
 */
private object FullMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

suspend fun <T> withFullMotion(block: suspend CoroutineScope.() -> T): T = withContext(FullMotion, block)

/**
 * The screen reader's focus in the window of the composable that remembered it (design system, Accessibility: sheets
 * move focus to their title and back to the trigger). Nodes are found by their test tag.
 */
interface AccessibilityFocus {
    /** Moves the focus to the node tagged [tag]; false without a screen reader or such a node. */
    fun moveTo(tag: String): Boolean

    /**
     * Puts the focus on the node tagged [tag] (a sheet's title on open, its trigger on close) and asks again for a
     * short while from when the node is there, in case the screen reader moves it on its own; stops early when
     * [cancelled] says another sheet opened or this one closed.
     */
    fun holdOn(tag: String, cancelled: () -> Boolean)
}

/** The [AccessibilityFocus] of this composable's window. */
@Composable
expect fun rememberAccessibilityFocus(): AccessibilityFocus
