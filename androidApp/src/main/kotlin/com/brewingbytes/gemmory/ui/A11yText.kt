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

    /** After a TalkBack fill action (#20): "214 placed. 98 left". */
    fun batch(placed: Int, left: Int, format: NumberFormat = NumberFormat.getIntegerInstance()): String =
        "${format.format(placed)} placed. ${left(left, format)}"

    /** The tool well (#20): "Tool: Fill region"; without VIP (#38) with the fills left, "Tool: Fill region, 4 fills". */
    fun tool(name: String, fills: Int? = null): String = when (fills) {
        null -> "Tool: $name"
        1 -> "Tool: $name, 1 fill"
        else -> "Tool: $name, $fills fills"
    }

    /** Fill color's TalkBack action and button label (#20): "Fill the rest of color 7". */
    fun fillColor(color: Int): String = "Fill the rest of color $color"

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

    /** The Tomorrow line (T36): one node, no name. */
    const val TOMORROW = "Tomorrow's picture, opens tomorrow"

    /**
     * Today's picture card (#34), one node: "Today's picture: Sunflowers, Medium, free to start today" / "Today's pick:
     * Sunflowers, Medium" (VIP) / "Today's picture: Sunflowers, 12% done" / "…, done".
     */

    fun today(title: String, size: String, vip: Boolean, free: Boolean, started: Boolean, percent: Int, done: Boolean, drop: Boolean = false): String {
        // Today's drop (T36) folds its "New today" chip in: "Today's picture, new today: Lighthouse, …".
        val head = "${if (vip) "Today's pick" else "Today's picture"}${if (drop) ", new today" else ""}: $title"
        return when {
            done -> "$head, done"
            started -> "$head, $percent% done"
            free -> "$head, $size, free to start today"
            else -> "$head, $size"
        }
    }

    /**
     * The gallery's Finished heading (#89): "Finished, 12 pictures" / "Finished, 1 photo canvas"; "collapsed" or
     * "expanded" is its state description, and "button" its role.
     */
    fun finished(count: Int, photos: Boolean): String {
        val noun = when {
            photos -> if (count == 1) "photo canvas" else "photo canvases"
            else -> if (count == 1) "picture" else "pictures"
        }
        return "Finished, $count $noun"
    }

    /**
     * A collection row's heading (T37), with the well folded in: "Seaside, 3 of 7 finished, 2 more coming" /
     * "Seaside, 7 of 7 finished".
     */
    fun collection(title: String, finished: Int, size: Int, coming: Int): String =
        "$title, $finished of $size finished" + if (coming > 0) ", $coming more coming" else ""

    /** The polite announcement when a completed collection's row first shows (T37). */
    fun collectionComplete(title: String, keepsake: Boolean): String =
        if (keepsake) "$title complete. Keepsake picture added." else "$title complete."

    /**
     * "Sunflowers, 42% done" / "Sunflowers, done" / "Sunflowers, locked, watch a video to unlock"; a remote picture
     * that just arrived (#43) reads "Fox in Snow, new, 0% done" / "…, new, locked, watch a video to unlock".
     */
    fun tile(title: String, percent: Int, done: Boolean, locked: Boolean, damaged: Boolean, new: Boolean = false): String {
        val head = if (new && !damaged) "$title, new" else title
        return when {
            damaged -> "$title, can't open"
            locked -> "$head, locked, watch a video to unlock"
            done -> "$head, done"
            else -> "$head, $percent% done"
        }
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
