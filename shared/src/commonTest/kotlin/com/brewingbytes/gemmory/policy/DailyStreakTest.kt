package com.brewingbytes.gemmory.policy

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // #101: cover order, the VIP weekly freeze, streak freezes, awaiting cover and repair (pure rules, day numbers).

    private val d = 20_000L
    private val alive = StreakState(length = 6, lastDay = d)

    @Test
    fun theDayOffCoversFirstThenTheVipFreezeThenFreezes() {
        val r = DailyStreak.played(alive, d + 4, Cover(vip = true, freezes = 2))!!
        assertEquals(7, r.length)
        assertEquals(
            listOf(CoveredDay(d + 1, DayKind.DAY_OFF), CoveredDay(d + 2, DayKind.VIP_FREEZE), CoveredDay(d + 3, DayKind.FREEZE)),
            r.covered,
        )
        assertEquals(1, r.freezesUsed)
        assertEquals(d + 1, r.state.restDay)
        assertEquals(d + 2, r.state.vipFreezeDay)
    }

    @Test
    fun theVipFreezeCoversOneDayInSevenNeverTwo() {
        val vip = Cover(vip = true)
        val first = DailyStreak.played(alive.copy(restDay = d - 1), d + 2, vip)!!
        assertEquals(listOf(CoveredDay(d + 1, DayKind.VIP_FREEZE)), first.covered, "the day off was used yesterday")
        val again = DailyStreak.played(first.state.copy(restDay = d + 1), d + 4, vip)!!
        assertEquals(1, again.length, "a second missed day within 7 days of the VIP freeze breaks it")
        val weekLater = DailyStreak.played(first.state.copy(restDay = d + 4, lastDay = d + 8), d + 10, vip)!!
        assertEquals(listOf(CoveredDay(d + 9, DayKind.VIP_FREEZE)), weekLater.covered, "8 days after the last one: ready again")
        assertEquals(1, DailyStreak.played(alive.copy(restDay = d - 1), d + 2, Cover.NONE)!!.length, "without VIP it breaks")
    }

    @Test
    fun twoFreezesCoverTwoMissedDaysAndAThirdBreaksIt() {
        val used = alive.copy(restDay = d - 1)
        val two = DailyStreak.played(used, d + 3, Cover(freezes = 2))!!
        assertEquals(7, two.length)
        assertEquals(2, two.freezesUsed)
        assertEquals(1, DailyStreak.played(used, d + 4, Cover(freezes = 2))!!.length)
    }

    @Test
    fun aBreakKeepsTheEndedStreakForARepair() {
        val r = DailyStreak.played(alive.copy(restDay = d - 1), d + 3, Cover.NONE)!!
        assertEquals(StreakState(1, d + 3, d - 1, brokenLength = 6, brokenLastDay = d), r.state)
        assertTrue(r.covered.isEmpty())
    }

    @Test
    fun shownCountsTheCoverForTomorrow() {
        val usedDayOff = alive.copy(restDay = d - 2)
        assertEquals(0, DailyStreak.shown(usedDayOff, d + 2), "tomorrow it would be broken")
        assertEquals(6, DailyStreak.shown(usedDayOff, d + 2, Cover(vip = true)), "the VIP freeze is ready")
        assertEquals(6, DailyStreak.shown(usedDayOff, d + 2, Cover(freezes = 1)), "a freeze is held")
        assertEquals(6, DailyStreak.shown(alive, d + 2), "the day off is ready")
    }

    @Test
    fun awaitingCoverListsWhatAFinishTodayWouldCover() {
        assertEquals(listOf(CoveredDay(d + 1, DayKind.DAY_OFF)), DailyStreak.awaitingCover(alive, d + 2))
        assertTrue(DailyStreak.awaitingCover(alive, d + 1).isEmpty(), "nothing missed")
        assertTrue(DailyStreak.awaitingCover(alive, d + 4).isEmpty(), "broken: nothing to cover")
    }

    @Test
    fun aRepairIsOfferedForTwoDaysAfterTheStreakEnded() {
        val used = alive.copy(restDay = d - 1)
        // Missed d+1 with no cover: the streak ended on d+1.
        assertEquals(BrokenStreak(6, d + 1), DailyStreak.repairOffer(used, d + 2, Cover.NONE, StreakState.NEVER))
        assertEquals(BrokenStreak(6, d + 1), DailyStreak.repairOffer(used, d + 3, Cover.NONE, StreakState.NEVER))
        assertNull(DailyStreak.repairOffer(used, d + 4, Cover.NONE, StreakState.NEVER), "back after 3 days: too late")
        assertNull(DailyStreak.repairOffer(used, d + 60, Cover.NONE, StreakState.NEVER), "back after 60 days")
        assertNull(DailyStreak.repairOffer(used, d + 2, Cover.NONE, lastRepairDay = d - 10), "a repair 12 days ago")
        assertEquals(BrokenStreak(6, d + 1), DailyStreak.repairOffer(used, d + 2, Cover.NONE, lastRepairDay = d - 28))
        assertNull(DailyStreak.repairOffer(used.copy(length = 1), d + 2, Cover.NONE, StreakState.NEVER), "a 1-day streak isn't")
        assertNull(DailyStreak.repairOffer(alive, d + 1, Cover.NONE, StreakState.NEVER), "alive: nothing to repair")
    }

    @Test
    fun theDayOffMovesTheEndOneDayLater() {
        // d+1 covered by the day off, d+2 not: it ended on d+2.
        assertEquals(BrokenStreak(6, d + 2), DailyStreak.broken(alive, d + 3))
    }

    @Test
    fun aRepairBeforeFinishingTodayStandsThroughYesterday() {
        val used = alive.copy(restDay = d - 1)
        val r = DailyStreak.repair(used, d + 3, Cover.NONE)!!
        assertEquals(6, r.length)
        assertEquals(d + 2, r.state.lastDay)
        assertEquals(listOf(CoveredDay(d + 1, DayKind.REPAIRED), CoveredDay(d + 2, DayKind.REPAIRED)), r.covered)
        assertEquals(7, DailyStreak.played(r.state, d + 3, Cover.NONE)!!.length, "finishing today extends it")
    }

    @Test
    fun aRepairAfterFinishingTodayKeepsToday() {
        val restarted = DailyStreak.played(alive.copy(restDay = d - 1), d + 3, Cover.NONE)!!.state
        assertEquals(BrokenStreak(6, d + 1), DailyStreak.repairOffer(restarted, d + 3, Cover.NONE, StreakState.NEVER))
        val r = DailyStreak.repair(restarted, d + 3, Cover.NONE)!!
        assertEquals(7, r.length)
        assertEquals(d + 3, r.state.lastDay)
        assertEquals(listOf(CoveredDay(d + 1, DayKind.REPAIRED), CoveredDay(d + 2, DayKind.REPAIRED)), r.covered)
        assertNull(DailyStreak.played(r.state, d + 3, Cover.NONE), "today was already counted")
        assertNull(DailyStreak.broken(r.state, d + 3), "nothing left to repair")
    }
}
