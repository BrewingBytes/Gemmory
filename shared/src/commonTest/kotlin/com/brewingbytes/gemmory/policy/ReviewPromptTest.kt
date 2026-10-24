package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The in-app review gate (T39) and the one interruption per return to the gallery (D22, D7). */
class ReviewPromptTest {
    private val day = 20_000L

    private fun due(
        made: Int = 0,
        finishes: Int = 3,
        today: Long = day,
        last: Long? = null,
        first: Long? = day - 5,
    ) = ReviewPrompt.attemptDue(made, finishes, today, last, first)

    @Test
    fun eachAttemptWaitsForItsThreshold() {
        assertNull(due(finishes = 2))
        assertEquals(1, due(finishes = 3))
        assertNull(due(made = 1, finishes = 9, last = day - 30))
        assertEquals(2, due(made = 1, finishes = 10, last = day - 30))
        assertNull(due(made = 2, finishes = 24, last = day - 30))
        assertEquals(3, due(made = 2, finishes = 25, last = day - 30))
    }

    @Test
    fun neverMoreThanThreeAttempts() {
        assertNull(due(made = 3, finishes = 500, last = day - 365))
    }

    @Test
    fun attemptsAreAtLeastFourteenDaysApart() {
        assertNull(due(made = 1, finishes = 10, last = day - 13))
        assertEquals(2, due(made = 1, finishes = 10, last = day - 14))
        assertNull(due(made = 1, finishes = 10, last = day + 3), "a clock moved back waits until the gap has passed")
    }

    @Test
    fun neverOnTheDayOfTheFirstFinish() {
        assertNull(due(first = day), "three finishes on day one")
        assertEquals(1, due(first = day, today = day + 1), "the next eligible finish")
        assertEquals(1, due(first = null), "finishes from before T35 have no day: never today")
    }

    /** Code review on #106: a finish from before T35 has no day, so the first finish wasn't today. */
    @Test
    fun theFirstFinishDayCountsFinishesFromBeforeT35() {
        assertNull(ReviewPrompt.firstFinishDay(emptyList()))
        assertEquals(day, ReviewPrompt.firstFinishDay(listOf(day)))
        assertEquals(day - 3, ReviewPrompt.firstFinishDay(listOf(day, day - 3)))
        assertEquals(ReviewPrompt.BEFORE_T35, ReviewPrompt.firstFinishDay(listOf(null, day)))
        assertEquals(1, due(first = ReviewPrompt.firstFinishDay(listOf(null, day))), "older finishes plus one today: due")
        assertNull(due(first = ReviewPrompt.firstFinishDay(listOf(day))), "only today's finishes: not yet")
    }

    /** Already past 10 when T52 (review prompt always on) arrives: one attempt at a time, the second at least 14 days after the first. */
    @Test
    fun aPlayerPastAThresholdGetsOneAttemptAtATime() {
        assertEquals(1, due(finishes = 40))
        assertNull(due(made = 1, finishes = 41, today = day + 1, last = day))
        assertEquals(2, due(made = 1, finishes = 41, today = day + 14, last = day))
        assertEquals(3, due(made = 2, finishes = 42, today = day + 28, last = day + 14))
    }

    @Test
    fun oneInterruptionPerReturnInTheConfirmedOrder() {
        assertEquals(ReturnInterruption.REMINDER_ASK, GalleryReturn.choose(askDue = true, reviewDue = true, reward = false))
        assertEquals(ReturnInterruption.REVIEW, GalleryReturn.choose(askDue = false, reviewDue = true, reward = false))
        assertEquals(ReturnInterruption.INTERSTITIAL, GalleryReturn.choose(askDue = false, reviewDue = false, reward = false))
    }

    /** R1 (D7): a reward return shows nothing; the ask and the review wait for the next eligible finish. */
    @Test
    fun aRewardReturnShowsNoInterruption() {
        for (ask in listOf(true, false)) for (review in listOf(true, false)) {
            assertEquals(ReturnInterruption.NONE, GalleryReturn.choose(ask, review, reward = true))
        }
    }
}
