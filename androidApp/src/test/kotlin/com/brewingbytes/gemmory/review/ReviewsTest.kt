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

    /** R1: with the flag off nothing is due and the counter stays untouched. */
    @Test
    fun theFlagOffNeverAsks() {
        flag = false
        finishes = 30
        assertNull(reviews().due(firstFinishDay = today - 40))
        assertTrue(prefs.values.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun anAttemptIsCountedAndRemembered() {
        val reviews = reviews()
        assertEquals(1, reviews.due(firstFinishDay = today - 2))
        reviews.counted(1)
        assertEquals(listOf(AnalyticsEvent("review_requested", mapOf("attempt" to 1L))), events)
        finishes = 10
        assertNull(reviews().due(firstFinishDay = today - 2), "a new start reads the counter: 14 days not passed")
        today += 14
        assertEquals(2, reviews().due(firstFinishDay = today - 16))
    }

    @Test
    fun theFirstFinishDayWaits() {
        assertNull(reviews().due(firstFinishDay = today))
    }
}
