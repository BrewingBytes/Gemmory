package com.brewingbytes.gemmory.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Canvas
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.play.CanvasEffects
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.play.PlaySession
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

    /**
     * The old Android canvas renderer for [pattern] while a hotfix build turns the shared one off (R10-8), else null
     * and the play canvas draws the shared `render.CanvasRenderer` (always on iOS). Goes with the switch.
     */
    @Composable
    fun rememberLegacyCanvas(pattern: Pattern, mystery: Boolean): LegacyCanvas?
}

/** A shared screen's haptics and sounds (design system, Haptics and sound). */
interface ScreenFeedback {
    /**
     * The gem tick (throttled to one per 40ms) and a placing click at a random pitch; while [drag] painting, a softer
     * tick. Also a wheel segment passing the pointer (#38).
     */
    fun gemPlaced(drag: Boolean = false)

    /** The milestone bell and the confirm haptic: a progress milestone (#35), the wheel landing (#38). */
    fun milestone()

    /** A tool's batch (#20) over [durationMs]: a light haptic sweep. Its clicks come from [sweepClick], timed by the caller. */
    fun sweep(durationMs: Int)

    /** The [step]th click of a batch's run (#20): a placing click, a semitone higher each time. */
    fun sweepClick(step: Int)

    /** A wrong-color tap: lighter than the tick, and silent. */
    fun wrongColor()

    /** A color finished: the confirm haptic and the color-done sound. */
    fun colorFinished()
}

/** The old canvas renderer behind [ShellUi.rememberLegacyCanvas] (R10-8): draws the open canvas as it did before T62. */
interface LegacyCanvas {
    fun draw(canvas: Canvas, session: PlaySession, density: Float, style: HighlightStyle, effects: CanvasEffects, nowNanos: Long)
}

/** The platform's [ShellUi]. A composable outside the shell fails at once instead of drawing without its slots. */
val LocalShellUi = staticCompositionLocalOf<ShellUi> { error("No ShellUi: the platform shell provides LocalShellUi") }
