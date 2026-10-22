package com.brewingbytes.gemmory.widget

import com.brewingbytes.gemmory.policy.Cover
import com.brewingbytes.gemmory.policy.StreakState
import com.brewingbytes.gemmory.streak.StreakSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/** The widget reads the same snapshot as the chip (#103, O8): the visibility table's Widget column (G18). */
class StreakWidgetStateTest {
    private val today = 20_000L

    private fun widget(state: StreakState, cover: Cover = Cover.NONE) = StreakWidgetState.of(StreakSnapshot(state, today, cover))

    @Test
    fun neverPlayedStartsAStreak() {
        val w = widget(StreakState())
        assertEquals(StreakWidgetState(0, lit = false), w)
        assertEquals("Start a streak", w.line)
        assertEquals(WidgetFlame.EMBER, w.flame)
        assertEquals("Gemmory streak, start a streak", w.description)
    }

    @Test
    fun dayOneDoneIsLit() {
        val w = widget(StreakState(length = 1, lastDay = today))
        assertEquals("Done today", w.line)
        assertEquals(WidgetFlame.LIT, w.flame)
        assertEquals("Gemmory streak, 1 day, done today", w.description)
    }

    @Test
    fun aStreakNotDoneTodayIsGrey() {
        val w = widget(StreakState(length = 6, lastDay = today - 1))
        assertEquals(StreakWidgetState(6, lit = false), w)
        assertEquals("Not yet today", w.line)
        assertEquals(WidgetFlame.EMBER, w.flame)
        assertEquals("Gemmory streak, 6 days, not done today", w.description)
    }

    @Test
    fun yesterdayAwaitingCoverStillCounts() {
        // Yesterday missed, the day off unused: covered when today's picture is finished (G13).
        val w = widget(StreakState(length = 6, lastDay = today - 2))
        assertEquals(StreakWidgetState(6, lit = false), w)
    }

    @Test
    fun aStreakDoneTodayIsLit() {
        assertEquals("Done today", widget(StreakState(length = 6, lastDay = today)).line)
    }

    @Test
    fun aBrokenStreakInItsRepairWindowStartsOver() {
        // Ended the day after it was last played; still repairable on the streak screen, never on the widget.
        val w = widget(StreakState(length = 6, lastDay = today - 3, restDay = today - 6))
        assertEquals(StreakWidgetState(0, lit = false), w)
        assertEquals("Start a streak", w.line)
    }

    @Test
    fun aStreakBrokenLongAgoStartsOver() {
        assertEquals(StreakWidgetState(0, lit = false), widget(StreakState(length = 12, lastDay = today - 40)))
    }

    @Test
    fun theVipFreezeKeepsIt() {
        val state = StreakState(length = 6, lastDay = today - 2, restDay = today - 4)
        assertEquals(0, widget(state).number)
        assertEquals(StreakWidgetState(6, lit = false), widget(state, Cover(vip = true)))
    }

    @Test
    fun theFlameTiersFollowTheLength() {
        assertEquals(WidgetFlame.LIT, StreakWidgetState(29, lit = true).flame)
        assertEquals(WidgetFlame.LIT_30, StreakWidgetState(30, lit = true).flame)
        assertEquals(WidgetFlame.LIT_100, StreakWidgetState(100, lit = true).flame)
        assertEquals(WidgetFlame.EMBER, StreakWidgetState(365, lit = false).flame)
    }
}
