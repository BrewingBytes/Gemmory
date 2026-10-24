package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.CellKind
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** #101: the streak copy tables (celebration, streak screen, TalkBack), word for word. */
class StreakTextTest {
    /** The real English resources (T48): these lines are the copy, word for word. */
    private val text = StreakText(XmlStrings.en)

    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    private val tuesday = day("2026-10-20")

    @Test
    fun theCelebrationCopyTable() {
        assertEquals(
            CelebrationText("You started a streak", "Finish a picture tomorrow to make it 2.", null, "Streak started. 1 day."),
            text.celebration(1, emptyList(), vip = false),
        )
        assertEquals(CelebrationText("4 days in a row", "Finish one tomorrow to make it 5.", null, "4 days in a row."), text.celebration(4, emptyList(), false))
        val dayOff = listOf(CoveredDay(tuesday, DayKind.DAY_OFF))
        assertEquals("Your day off on Tuesday kept it going.", text.celebration(8, dayOff, false).line)
        assertEquals("8 days in a row. Your day off on Tuesday kept it going.", text.celebration(8, dayOff, false).announcement)
        assertEquals("Your VIP freeze kept it going.", text.celebration(8, listOf(CoveredDay(tuesday, DayKind.VIP_FREEZE)), true).line)
        assertEquals("A streak freeze kept it going on Tuesday. 1 left.", text.celebration(8, listOf(CoveredDay(tuesday, DayKind.FREEZE)), false, freezesLeft = 1).line)
        assertEquals(
            "2 streak freezes kept it going. None left.",
            text.celebration(8, listOf(CoveredDay(tuesday - 1, DayKind.FREEZE), CoveredDay(tuesday, DayKind.FREEZE)), false).line,
        )
        assertEquals("Streak freezes 2" to "Covers a missed day. Hold up to 2.", text.freezes(2))
        assertEquals(
            "Your day off and a streak freeze kept it going.",
            text.celebration(9, dayOff + CoveredDay(tuesday + 1, DayKind.FREEZE), false).line,
        )
    }

    @Test
    fun milestonesAndTheGiftLine() {
        assertEquals(listOf("A week of gems", "A month of gems", "50 days of gems", "100 days of gems", "A year of gems"), StreakText.MILESTONES.map { text.celebration(it, emptyList(), false).title })
        assertEquals("7 days in a row.", text.celebration(7, emptyList(), false).line)
        assertEquals("Your 5-day gift spin is in the bag.", text.celebration(5, emptyList(), vip = false).gift)
        assertNull("VIP has no gift wheel", text.celebration(5, emptyList(), vip = true).gift)
        assertNull(text.celebration(6, emptyList(), vip = false).gift)
        assertEquals("finish", StreakText.savedBy(emptyList()))
        assertEquals("day_off+vip_freeze", StreakText.savedBy(listOf(CoveredDay(1, DayKind.DAY_OFF), CoveredDay(2, DayKind.VIP_FREEZE))))
    }

    @Test
    fun theStreakScreenCopy() {
        assertEquals("Done for today", text.todayStatus(3, doneToday = true))
        assertEquals("Finish a picture today to keep it", text.todayStatus(3, doneToday = false))
        assertEquals("Finish any picture to start a streak.", text.todayStatus(0, doneToday = false))
        val today = tuesday + 1
        assertEquals("Yesterday is covered by your day off when you finish today.", text.awaiting(listOf(CoveredDay(tuesday, DayKind.DAY_OFF)), today))
        assertEquals("Yesterday is covered by your VIP freeze when you finish today.", text.awaiting(listOf(CoveredDay(tuesday, DayKind.VIP_FREEZE)), today))
        assertNull(text.awaiting(emptyList(), today))
        assertEquals("Day off ready" to null, text.weekly("Day off", Long.MIN_VALUE, today))
        val saturday = day("2026-10-17")
        assertEquals("Day off used Saturday" to "Ready again next Saturday", text.weekly("Day off", saturday, today))
        assertEquals("Day off ready" to null, text.weekly("Day off", saturday, saturday + 7))
    }

    @Test
    fun talkBack() {
        assertEquals("6 days in a row, not done today, opens your streak", text.chip(6, doneToday = false))
        assertEquals("1 day in a row, done today, opens your streak", text.chip(1, doneToday = true))
        assertEquals("0 days in a row, your 12 days can still be restored, opens your streak", text.chipBroken(12))
        assertEquals("Tuesday 20, finished Lighthouse", text.cell(tuesday, tuesday + 2, "finished Lighthouse"))
        assertEquals("Today, not finished yet", text.cell(tuesday, tuesday, "not finished yet"))
        val monday = day("2026-10-19")
        val week = listOf(
            CalendarCell(monday, CellKind.FINISHED),
            CalendarCell(tuesday, CellKind.DAY_OFF),
            CalendarCell(tuesday + 1, CellKind.FINISHED, today = true),
            CalendarCell(tuesday + 2, CellKind.FUTURE),
        )
        assertEquals("This week: Monday done, Tuesday day off, Wednesday today, done", text.week(week))
    }

    /** #102: the repair panel's copy (streak-screen.md, Streak B decisions). */
    @Test
    fun repairCopy() {
        assertEquals("Bring back your 12 days", text.repairTitle(12))
        assertEquals("Your streak ended on Tuesday. Watch a short video to restore it.", text.repairBody(tuesday, vipReady = false))
        assertEquals("Your streak ended on Tuesday. Your weekly VIP repair can bring it back.", text.repairBody(tuesday, vipReady = true))
        assertEquals("You can do this until Thursday.", text.repairUntil(tuesday + 2))
        assertEquals("VIP repair used Monday · ready again next Monday", text.vipRepairUsed(tuesday - 1))
        assertEquals("Streak back: 12 days", text.restored(12))
        assertEquals("Monday and Tuesday repaired", text.repaired(listOf(tuesday - 1, tuesday)))
        assertEquals("Tuesday repaired", text.repaired(listOf(tuesday)))
        assertNull(text.repaired(emptyList()))
        assertEquals("Your 12 days can still be restored", text.stillRestorable(12))
    }
}
