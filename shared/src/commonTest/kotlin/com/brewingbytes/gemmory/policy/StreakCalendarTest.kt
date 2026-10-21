package com.brewingbytes.gemmory.policy

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** #101: what each calendar and week-row day shows (the cell table, O7, G12, G13). */
class StreakCalendarTest {
    private fun day(date: String): Long = LocalDate.parse(date).toEpochDays()

    private val today = day("2026-10-14") // a Wednesday

    private fun kinds(days: LongRange, history: List<HistoryDay>, state: StreakState, cover: Cover = Cover.NONE) =
        StreakCalendar.cells(days, history, state, cover, today).map { it.kind }

    @Test
    fun loggedDaysShowHowTheyCounted() {
        val history = listOf(
            HistoryDay(today - 5, DayKind.FINISHED, "BUNDLED/a"),
            HistoryDay(today - 4, DayKind.DAY_OFF),
            HistoryDay(today - 3, DayKind.VIP_FREEZE),
            HistoryDay(today - 2, DayKind.FREEZE),
            HistoryDay(today - 1, DayKind.REPAIRED),
            HistoryDay(today, DayKind.FINISHED, "BUNDLED/b"),
        )
        val cells = StreakCalendar.cells(today - 5..today + 1, history, StreakState(length = 2, lastDay = today), Cover.NONE, today)
        assertEquals(
            listOf(CellKind.FINISHED, CellKind.DAY_OFF, CellKind.VIP_FREEZE, CellKind.FREEZE, CellKind.REPAIRED, CellKind.FINISHED, CellKind.FUTURE),
            cells.map { it.kind },
        )
        assertEquals("BUNDLED/a", cells.first().canvas)
        assertEquals(listOf(today), cells.filter { it.today }.map { it.day })
    }

    @Test
    fun unloggedDaysInsideTheStreakAreKeptAndBeforeItEmpty() {
        // 4 days in a row through yesterday, only yesterday logged (the log is newer than the streak).
        val state = StreakState(length = 4, lastDay = today - 1)
        val history = listOf(HistoryDay(today - 1, DayKind.FINISHED, "BUNDLED/a"))
        assertEquals(
            listOf(CellKind.EMPTY, CellKind.KEPT, CellKind.KEPT, CellKind.KEPT, CellKind.FINISHED, CellKind.EMPTY),
            kinds(today - 5..today, history, state),
        )
    }

    @Test
    fun aLoggedCoveredDayDoesntCountTowardTheLength() {
        val state = StreakState(length = 2, lastDay = today - 1)
        val history = listOf(HistoryDay(today - 2, DayKind.DAY_OFF), HistoryDay(today - 1, DayKind.FINISHED, "BUNDLED/a"))
        assertEquals(listOf(CellKind.EMPTY, CellKind.KEPT, CellKind.DAY_OFF, CellKind.FINISHED), kinds(today - 4..today - 1, history, state))
    }

    @Test
    fun aMissedDayAwaitingCoverIsMarked() {
        val state = StreakState(length = 3, lastDay = today - 2)
        assertEquals(listOf(CellKind.KEPT, CellKind.AWAITING, CellKind.EMPTY), kinds(today - 2..today, emptyList(), state))
    }

    @Test
    fun aBrokenStreakShowsNoKeptDays() {
        val state = StreakState(length = 5, lastDay = today - 4, restDay = today - 6)
        assertEquals(listOf(CellKind.EMPTY, CellKind.EMPTY), kinds(today - 5..today - 4, emptyList(), state))
    }

    @Test
    fun theWeekStartsOnTheLocalesFirstDay() {
        assertEquals(day("2026-10-12"), StreakCalendar.weekStart(today, DayOfWeek.MONDAY))
        assertEquals(day("2026-10-11"), StreakCalendar.weekStart(today, DayOfWeek.SUNDAY))
        assertEquals(day("2026-10-12"), StreakCalendar.weekStart(day("2026-10-12"), DayOfWeek.MONDAY))
        assertEquals(day("2026-10-12"), StreakCalendar.weekStart(day("2026-10-18"), DayOfWeek.MONDAY))
    }

    @Test
    fun monthsRunFromTheFirstLoggedOne() {
        assertEquals(day("2026-10-01")..day("2026-10-31"), StreakCalendar.monthOf(today))
        assertEquals(day("2026-12-01")..day("2026-12-31"), StreakCalendar.monthOf(day("2026-12-31")))
        assertEquals(day("2024-02-01")..day("2024-02-29"), StreakCalendar.monthOf(day("2024-02-10")))
        assertEquals(StreakCalendar.monthOf(day("2026-08-03")), StreakCalendar.firstMonth(listOf(HistoryDay(day("2026-08-03"), DayKind.FINISHED)), today))
        assertEquals(StreakCalendar.monthOf(today), StreakCalendar.firstMonth(emptyList(), today))
    }
}
