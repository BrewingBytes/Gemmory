package com.brewingbytes.gemmory.review

import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The review prompt's counter (T39): the flag, the finish count and the attempts made, kept across app starts. */
class ReviewsTest {
    private val prefs = FakePrefs()
    private var flag = true
    private var finishes = 3
    private var today = 20_000L
    private val events = mutableListOf<AnalyticsEvent>()

    private fun reviews() = Reviews(
        ReviewPrefs(prefs),
        { error("Play is never asked here") },
        object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        },
        { flag },
        { finishes },
        { today },
    )

    /** R1: with the flag off nothing is due, nothing is counted and nothing is logged. */
    @Test
    fun theFlagOffNeverAsks() {
        flag = false
        finishes = 30
        val reviews = reviews()
        reviews.onFreshFinish { listOf(today - 40, today) }
        assertNull(reviews.due())
        assertEquals(0, ReviewPrefs(prefs).made)
        assertTrue(events.isEmpty())
    }

    @Test
    fun anAttemptIsCountedAndRemembered() {
        val reviews = reviews()
        reviews.onFreshFinish { listOf(today - 2, today) }
        assertEquals(1, reviews.due())
        reviews.counted(1)
        assertEquals(listOf(AnalyticsEvent("review_requested", mapOf("attempt" to 1L))), events)
        finishes = 10
        assertNull(reviews().due(), "a new start reads the counter: 14 days not passed")
        today += 14
        assertEquals(2, reviews().due())
    }

    @Test
    fun theFirstFinishDayWaits() {
        val reviews = reviews()
        reviews.onFreshFinish { listOf(today) }
        assertNull(reviews.due())
        today += 1
        assertEquals(1, reviews.due())
    }

    /**
     * Code review on #106: the first-finish day is stored once, so a Start over or a deleted photo canvas (its day
     * gone from the phone) can't move it to today; finishes from before T35 mean it wasn't today.
     */
    @Test
    fun theFirstFinishDayIsStoredOnceAndCountsOlderFinishes() {
        val reviews = reviews()
        reviews.onFreshFinish { listOf(today - 5) }
        reviews.onFreshFinish { listOf(today) }
        assertEquals(today - 5, ReviewPrefs(prefs).firstFinishDay)
        assertEquals(1, reviews.due())

        prefs.values.clear()
        val upgraded = reviews()
        upgraded.onFreshFinish { listOf(null, null, today) }
        assertEquals(1, upgraded.due(), "a tester with finishes from older builds is due at their first finish here")
    }
}
