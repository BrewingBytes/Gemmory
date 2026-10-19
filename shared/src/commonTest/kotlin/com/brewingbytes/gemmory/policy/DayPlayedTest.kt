package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `day_played` (T36, eng D13): once per local day, on the day's first gem. */
class DayPlayedTest {
    private val clock = FakeClock("2026-11-02T20:00")
    private val store = object : StateStore<Long> {
        var saved: Long? = null

        override fun load() = saved

        override fun save(value: Long) {
            saved = value
        }
    }

    @Test
    fun onceADayAcrossRelaunchesAndAgainAfterMidnight() {
        val played = DayPlayed(clock.day, store)
        assertTrue(played.gemPlaced())
        assertFalse(played.gemPlaced())
        assertFalse(DayPlayed(clock.day, store).gemPlaced(), "a relaunch the same day doesn't log again")
        clock.set("2026-11-03T00:01")
        assertTrue(played.gemPlaced(), "the next local day logs again")
        assertFalse(played.gemPlaced())
    }
}
