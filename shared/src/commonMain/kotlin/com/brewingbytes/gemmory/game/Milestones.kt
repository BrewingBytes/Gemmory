package com.brewingbytes.gemmory.game

/**
 * Progress milestones (#35): 25, 50 and 75% of a canvas's gems. They are derived from the placement count alone, so
 * the save log, the save format and the time-lapse know nothing of them. A milestone plays only when a placement
 * crosses it, so a canvas reopened past one never replays it. 100% is the completion screen's moment, not a milestone.
 */
object Milestones {
    val PERCENTS = intArrayOf(25, 50, 75)

    /** Placements needed to reach [percent] of [total] gems, rounded up: 25% of 10 gems is reached at the 3rd. */
    fun threshold(percent: Int, total: Int): Int = ((total.toLong() * percent + 99) / 100).toInt()

    /**
     * The milestone a placement crossed going from [before] to [after] placed gems of [total], or null. On a tiny
     * canvas where one placement crosses several, the highest wins; the placement that finishes the picture crosses
     * none.
     */
    fun crossed(before: Int, after: Int, total: Int): Int? {
        if (after <= before || after >= total) return null
        return PERCENTS.lastOrNull { threshold(it, total) in (before + 1)..after }
    }
}
