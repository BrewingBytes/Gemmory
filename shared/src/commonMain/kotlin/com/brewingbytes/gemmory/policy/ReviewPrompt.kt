package com.brewingbytes.gemmory.policy

/**
 * When the gallery asks Play for its in-app review sheet (T39). Play rate-limits the sheet and never says whether it
 * showed, so the app calls at most [THRESHOLDS].size times per install. Attempt *n* is made at the first fresh finish
 * where all hold: the finish count is at or past the *n*th threshold, `features.review` is on in the signed catalog,
 * at least [GAP_DAYS] days have passed since the previous attempt, and it isn't the day of the player's first finish.
 * One attempt at a time: a player already past 10 finishes when the flag flips gets attempt 1, then attempt 2 at least
 * [GAP_DAYS] days later. A deferred attempt waits for the next eligible finish.
 */
object ReviewPrompt {
    val THRESHOLDS = listOf(3, 10, 25)
    const val GAP_DAYS = 14L

    /**
     * The attempt (1-3) due now, or null. [made]: attempts already made; [finishes]: every finish counted so far, this
     * one included; [today], [lastAttemptDay] and [firstFinishDay] are local epoch days ([firstFinishDay] null when no
     * finish has a recorded day, i.e. all came before T35, so never today). A clock moved back before the last
     * attempt waits until the gap has really passed.
     */
    fun attemptDue(made: Int, finishes: Int, flagOn: Boolean, today: Long, lastAttemptDay: Long?, firstFinishDay: Long?): Int? {
        if (!flagOn) return null
        val threshold = THRESHOLDS.getOrNull(made) ?: return null
        if (finishes < threshold) return null
        if (lastAttemptDay != null && today - lastAttemptDay < GAP_DAYS) return null
        if (firstFinishDay == today) return null
        return made + 1
    }
}

/** What a return to the gallery from a fresh finish shows (design system, One interruption per return to the gallery). */
enum class ReturnInterruption {
    REMINDER_ASK,
    REVIEW,
    INTERSTITIAL,
    NONE,
}

object GalleryReturn {
    /**
     * At most one, first that applies (plan-design-review D22): the reminder ask, Play's review sheet, then the
     * interstitial check (which applies its own caps). A [reward] return, one that completes a collection or earns an
     * event's keepsake (D7), shows none: a due ask or review waits for the next eligible finish.
     */
    fun choose(askDue: Boolean, reviewDue: Boolean, reward: Boolean): ReturnInterruption = when {
        reward -> ReturnInterruption.NONE
        askDue -> ReturnInterruption.REMINDER_ASK
        reviewDue -> ReturnInterruption.REVIEW
        else -> ReturnInterruption.INTERSTITIAL
    }
}
