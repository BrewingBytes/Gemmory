package com.brewingbytes.gemmory.ui

import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/**
 * What TalkBack reads (design system, Accessibility, Phase 5 D19). Colors are named by their tray number (slot + 1).
 * [format] groups thousands for the device locale; tests pass a fixed one.
 */
object A11yText {
    /** "Canvas. 4,812 of 8,112 placed. Color 7 selected, 312 left." */
    fun canvas(placed: Int, total: Int, color: Int, left: Int, format: NumberFormat = NumberFormat.getIntegerInstance()): String {
        val progress = "Canvas. ${format.format(placed)} of ${format.format(total)} placed."
        return if (placed >= total) progress else "$progress Color $color selected, ${format.format(left)} left."
    }

    /** A wrong-color tap: an announcement only, nothing changes (eng D4). */
    fun notColor(color: Int): String = "Not color $color"

    /** "Color 7 done. Color 8 selected."; the last color has no next one. */
    fun colorDone(color: Int, next: Int?): String = if (next == null) "Color $color done." else "Color $color done. Color $next selected."

    /** After "Place next gem" (founder call #14): the selected color's remaining count. */
    fun left(left: Int, format: NumberFormat = NumberFormat.getIntegerInstance()): String = "${format.format(left)} left"

    /** A progress milestone's headline (#35), shown on its banner and spoken by TalkBack. */
    fun milestone(percent: Int): String = when (percent) {
        25 -> "A quarter done"
        50 -> "Halfway there"
        else -> "Three quarters done"
    }

    /** The peek (#35), turned on or off by its TalkBack canvas action. */
    fun peek(shown: Boolean): String = if (shown) "Showing the finished picture" else "Back to canvas"

    /** "Color 7, 312 left" / "Color 7, done"; TalkBack adds "selected" from the selected state. */
    fun trough(color: Int, left: Int, format: NumberFormat = NumberFormat.getIntegerInstance()): String =
        if (left == 0) "Color $color, done" else "Color $color, ${format.format(left)} left"

    /** "Sunflowers, 42% done" / "Sunflowers, done" / "Sunflowers, locked, watch a video to unlock". */
    fun tile(title: String, percent: Int, done: Boolean, locked: Boolean, damaged: Boolean): String = when {
        damaged -> "$title, can't open"
        locked -> "$title, locked, watch a video to unlock"
        done -> "$title, done"
        else -> "$title, $percent% done"
    }
}

/** Gallery columns by window width (design D22): 2 under 600dp, 3 from 600dp, 4 from 840dp. */
fun galleryColumns(widthDp: Float): Int = when {
    widthDp >= 840f -> 4
    widthDp >= 600f -> 3
    else -> 2
}

/** The allowance chip's width limits beside the gallery's floating button (design D5, founder call #14). */
val NOTE_MAX_WIDTH = 280.dp
val NOTE_MIN_WIDTH = 160.dp

/**
 * Whether the allowance chip fits beside the floating button in a row [widthDp] wide (all in dp): it needs at least
 * [NOTE_MIN_WIDTH] next to the button, or it goes on its own line above it.
 */
fun noteFitsBeside(widthDp: Float, buttonDp: Float, gapDp: Float): Boolean = widthDp - buttonDp - gapDp >= NOTE_MIN_WIDTH.value
