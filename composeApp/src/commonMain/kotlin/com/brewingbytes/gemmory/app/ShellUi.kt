package com.brewingbytes.gemmory.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.brewingbytes.gemmory.share.ProgressShareSession

/**
 * What a shared screen draws or plays through its platform shell (iOS Stage 1c, T64): the parts that stay native, as
 * slots. Android's is provided by its `GemmoryTheme {}` (so connected tests that draw a screen get it too); iOS builds
 * its own in Stage 2 (share sheet, `UIImpactFeedbackGenerator`, design system iOS section).
 */
interface ShellUi {
    /**
     * The Share progress sheet over the screen that owns [session] (#36): the encode's progress, then the platform's
     * share sheet. [returnFocusTo] is the test tag the screen reader's focus goes back to when it closes.
     */
    @Composable
    fun ProgressShareHost(session: ProgressShareSession, returnFocusTo: String?)

    /** Haptics and sounds for one screen, following the Settings toggles; released when the screen leaves. */
    @Composable
    fun rememberFeedback(): ScreenFeedback
}

/** A shared screen's haptics and sounds (design system, Haptics and sound). */
interface ScreenFeedback {
    /** The gem tick (throttled) and a placing click: here, a wheel segment passing the pointer (#38). */
    fun gemPlaced()

    /** The milestone bell and the confirm haptic: here, the wheel landing (#38). */
    fun milestone()
}

/** The platform's [ShellUi]. A composable outside the shell fails at once instead of drawing without its slots. */
val LocalShellUi = staticCompositionLocalOf<ShellUi> { error("No ShellUi: the platform shell provides LocalShellUi") }
