package com.brewingbytes.gemmory.ui

import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings

/**
 * What TalkBack reads (design system, Accessibility, Phase 5 D19). Colors are named by their tray number (slot + 1).
 * Words come from string resources (T48); counts are grouped in the app language's locale.
 */
class A11yText(private val s: Strings) {
    /** "Canvas. 4,812 of 8,112 placed. Color 7 selected, 312 left." */
    fun canvas(placed: Int, total: Int, color: Int, left: Int): String {
        val progress = s.text(R.string.a11y_canvas_progress, s.number(placed), s.number(total))
        return if (placed >= total) progress else s.plural(R.plurals.a11y_canvas_selected, left, progress, color, s.number(left))
    }

    /** A wrong-color tap: an announcement only, nothing changes (eng D4). */
    fun notColor(color: Int): String = s.text(R.string.a11y_not_color, color)

    /** "Color 7 done. Color 8 selected."; the last color has no next one. */
    fun colorDone(color: Int, next: Int?): String =
        if (next == null) s.plural(R.plurals.a11y_color_done, color, color) else s.plural(R.plurals.a11y_color_done_next, color, color, next)

    /** After "Place next gem" (founder call #14): the selected color's remaining count. */
    fun left(left: Int): String = s.text(R.string.a11y_left, s.number(left))

    /** After a TalkBack fill action (#20): "214 placed. 98 left". */
    fun batch(placed: Int, left: Int): String = s.text(R.string.a11y_batch, s.number(placed), left(left))

    /** "98 left. Halfway there": the count after a gem, with a milestone crossed by it. */
    fun leftWithMilestone(left: Int, milestone: Int?): String =
        if (milestone == null) left(left) else s.text(R.string.a11y_join, left(left), milestone(milestone))

    /** "214 placed. 98 left. Halfway there": a fill's count with the milestone it crossed. */
    fun batchWithMilestone(batch: String, milestone: Int): String = s.text(R.string.a11y_join, batch, milestone(milestone))

    /** "Color 7 done. Color 8 selected. Halfway there.": a line with the milestone crossed by the same gems. */
    fun withMilestone(line: String, milestone: Int): String = s.text(R.string.a11y_with_milestone, line, milestone(milestone))

    /** The tool well (#20): "Tool: Fill region"; without VIP (#38) with the fills left, "Tool: Fill region, 4 fills". */
    fun tool(name: String, fills: Int? = null): String =
        if (fills == null) s.text(R.string.a11y_tool, name) else s.plural(R.plurals.a11y_tool_fills, fills, name, fills)

    /** Fill color's TalkBack action and button label (#20): "Fill the rest of color 7". */
    fun fillColor(color: Int): String = s.text(R.string.a11y_fill_color, color)

    /** The Music volume slider's value (T42): "30 percent", read after "Music volume". */
    fun musicVolume(percent: Int): String = s.plural(R.plurals.a11y_music_volume, percent, percent)

    /** A progress milestone's headline (#35), shown on its banner and spoken by TalkBack. */
    fun milestone(percent: Int): String = when (percent) {
        25 -> s.text(R.string.milestone_quarter)
        50 -> s.text(R.string.milestone_half)
        else -> s.text(R.string.milestone_three_quarters)
    }

    /** The peek (#35), turned on or off by its TalkBack canvas action. */
    fun peek(shown: Boolean): String = s.text(if (shown) R.string.a11y_peek_shown else R.string.a11y_peek_hidden)

    /** "Color 7, 312 left" / "Color 7, done"; TalkBack adds "selected" from the selected state. */
    fun trough(color: Int, left: Int): String =
        if (left == 0) s.text(R.string.a11y_trough_done, color) else s.text(R.string.a11y_trough_left, color, s.number(left))

    /** The Tomorrow line (T36): one node, no name. */
    val tomorrow: String get() = s.text(R.string.a11y_tomorrow)

    /**
     * Today's picture card (#34), one node: "Today's picture: Sunflowers, Medium, free to start today" / "Today's pick:
     * Sunflowers, Medium" (VIP) / "Today's picture: Sunflowers, 12% done" / "…, done".
     */
    fun today(
        title: String,
        size: String,
        vip: Boolean,
        free: Boolean,
        started: Boolean,
        percent: Int,
        done: Boolean,
        drop: Boolean = false,
        mystery: Boolean = false,
    ): String {
        // Today's drop (T36) folds its "New today" chip in: "Today's picture, new today: Lighthouse, …". A mystery
        // (T43) is read by its neutral name alone: "Today's mystery picture, new today, hidden until finished, …".
        val head = if (mystery) {
            s.text(if (drop) R.string.a11y_today_mystery_drop else R.string.a11y_today_mystery, title)
        } else {
            val label = s.text(if (vip) R.string.today_label_pick else R.string.today_label_picture)
            s.text(if (drop) R.string.a11y_today_head_drop else R.string.a11y_today_head, label, title)
        }
        return when {
            done -> s.text(R.string.a11y_done, head)
            started -> s.text(R.string.a11y_percent_done, head, percent)
            free -> s.text(R.string.a11y_today_free, head, size)
            else -> s.text(R.string.a11y_today_sized, head, size)
        }
    }

    /**
     * The gallery's Finished heading (#89): "Finished, 12 pictures" / "Finished, 1 photo canvas"; "collapsed" or
     * "expanded" is its state description, and "button" its role.
     */
    fun finished(count: Int, photos: Boolean): String =
        s.plural(if (photos) R.plurals.a11y_finished_photos else R.plurals.a11y_finished_pictures, count, count)

    /**
     * A collection row's heading (T37), with the well folded in: "Seaside, 3 of 7 finished, 2 more coming" /
     * "Seaside, 7 of 7 finished". Each part is counted by its own number (#163).
     */
    fun collection(title: String, finished: Int, size: Int, coming: Int): String {
        val count = s.plural(R.plurals.a11y_collection, finished, title, finished, size)
        if (coming <= 0) return count
        // The well's words start a sentence on screen (pl "Jeszcze", bg, hu); here they follow a comma.
        val well = s.plural(R.plurals.collection_coming, coming, coming).replaceFirstChar { it.lowercase(s.locale) }
        return s.text(R.string.a11y_collection_coming, count, well)
    }

    /** The polite announcement when a completed collection's row first shows (T37). */
    fun collectionComplete(title: String, keepsake: Boolean): String =
        s.text(if (keepsake) R.string.a11y_collection_complete_keepsake else R.string.a11y_collection_complete, title)

    /** The polite announcement when an event's row first shows after its 3rd finish in the window (T38). */
    fun eventEarned(title: String, added: Boolean): String =
        s.text(if (added) R.string.a11y_event_added else R.string.a11y_event_earned, title)

    /**
     * "Sunflowers, 42% done" / "Sunflowers, done" / "Sunflowers, locked, watch a video to unlock"; a remote picture
     * that just arrived (#43) reads "Fox in Snow, new, 0% done" / "…, new, locked, watch a video to unlock". An
     * unfinished mystery picture (T43) reads its neutral name: "Mystery picture, hidden until finished, 12% done".
     */
    fun tile(title: String, percent: Int, done: Boolean, locked: Boolean, damaged: Boolean, new: Boolean = false, mystery: Boolean = false): String {
        val head = when {
            mystery -> s.text(R.string.a11y_tile_mystery, title)
            new && !damaged -> s.text(R.string.a11y_tile_new, title)
            else -> title
        }
        return when {
            damaged -> s.text(R.string.a11y_tile_damaged, title)
            locked -> s.text(R.string.a11y_tile_locked, head)
            done -> s.text(R.string.a11y_done, head)
            else -> s.text(R.string.a11y_percent_done, head, percent)
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
