package com.brewingbytes.gemmory.policy

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyStreakTest {
    private val clock = FakeClock("2026-10-14T10:00")
    private val store = object : StateStore<StreakState> {
        var saved: StreakState? = null

        override fun load() = saved

        override fun save(value: StreakState) {
            saved = value
        }
    }

    private fun streak() = DailyStreak(clock.day, store)

    private fun DailyStreak.playOn(day: String): Int? {
        clock.set("${day}T12:00")
        return played()
    }

    @Test
    fun aFreshInstallHasNoStreak() {
        assertEquals(0, streak().current())
    }

    @Test
    fun daysInARowCountOncePerDay() {
        val s = streak()
        assertEquals(1, s.playOn("2026-10-14"))
        clock.set("2026-10-14T23:59")
        assertNull(s.played(), "the same day counts once")
        assertEquals(2, s.playOn("2026-10-15"))
        assertEquals(3, s.playOn("2026-10-16"))
        assertEquals(3, s.current())
    }

    @Test
    fun theDayTurnsAtLocalMidnight() {
        val s = streak()
        clock.set("2026-10-14T23:59")
        s.played()
        clock.set("2026-10-15T00:01")
        assertEquals(2, s.played(), "two minutes later, but a new local day")
    }

    @Test
    fun oneDayOffAWeekIsForgiven() {
        val s = streak()
        s.playOn("2026-10-14")
        s.playOn("2026-10-15")
        clock.set("2026-10-17T09:00")
        assertEquals(2, s.current(), "the 16th was missed, but the streak still stands on the 17th")
        assertEquals(3, s.playOn("2026-10-17"), "the day off is skipped, not counted")
        clock.set("2026-10-19T09:00")
        assertEquals(0, s.current(), "a second day off within the week isn't forgiven")
        assertEquals(1, s.playOn("2026-10-19"), "so it starts over")
    }

    @Test
    fun aDayOffIsForgivenAgainAWeekLater() {
        val s = streak()
        s.playOn("2026-10-01")
        s.playOn("2026-10-03") // the 2nd forgiven
        for (d in 4..9) s.playOn("2026-10-${d.toString().padStart(2, '0')}")
        assertEquals(8, s.current())
        assertEquals(9, s.playOn("2026-10-11"), "the 10th is 8 days after the 2nd: forgiven")
    }

    @Test
    fun twoDaysOffBreakIt() {
        val s = streak()
        s.playOn("2026-10-14")
        s.playOn("2026-10-15")
        clock.set("2026-10-18T09:00")
        assertEquals(0, s.current(), "a broken streak isn't shown")
        assertEquals(1, s.playOn("2026-10-18"))
    }

    @Test
    fun aClockMovedBackChangesNothing() {
        val s = streak()
        s.playOn("2026-10-14")
        s.playOn("2026-10-15")
        assertNull(s.playOn("2026-10-10"), "an earlier day isn't a new one")
        assertEquals(2, s.current())
        assertEquals(3, s.playOn("2026-10-16"))
    }

    @Test
    fun aTimeZoneChangeMovesToday() {
        val s = streak()
        clock.set("2026-10-14T23:30")
        s.played()
        clock.zone = TimeZone.of("Asia/Tokyo") // already the 15th there
        assertEquals(2, s.played())
    }

    @Test
    fun itSurvivesARestart() {
        streak().playOn("2026-10-14")
        streak().playOn("2026-10-15")
        clock.set("2026-10-16T08:00")
        assertEquals(2, streak().current())
    }
}
