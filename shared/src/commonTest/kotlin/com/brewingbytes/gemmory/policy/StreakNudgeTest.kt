package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreakNudgeTest {
    private val today = 20_000L

    private fun canvas(key: String, placed: Int, total: Int = 100, lastGem: Long? = 1_000L, photo: Boolean = false) =
        ReminderCanvas(key, if (photo) null else key, placed, total, lastGem)

    @Test
    fun todaysPictureWhenItIsQuickAndUnfinished() {
        val started = listOf(canvas("harbour", placed = 82))
        assertEquals(NudgeTarget.Today("lighthouse"), StreakNudge.target("lighthouse", listOf("huts", "lighthouse"), started))
        // Today's picture is Big (not in the Quick list), or finished: the canvas closest to done.
        assertEquals(NudgeTarget.Canvas(started[0]), StreakNudge.target("old-town", listOf("huts"), started))
    }

    @Test
    fun theCanvasClosestToDoneAnyAgePhotoIncluded() {
        val old = canvas("old", placed = 60, lastGem = null)
        val photo = canvas("photo/abc", placed = 82, photo = true, lastGem = 5L)
        val done = canvas("done", placed = 100)
        assertEquals(NudgeTarget.Canvas(photo), StreakNudge.target(null, listOf("huts"), listOf(old, done, photo)))
        assertEquals(NudgeTarget.Canvas(old), StreakNudge.target(null, emptyList(), listOf(old, done)))
        // A tie goes to the one played most recently.
        val a = canvas("a", placed = 50, lastGem = 10L)
        val b = canvas("b", placed = 50, lastGem = 20L)
        assertEquals(NudgeTarget.Canvas(b), StreakNudge.target(null, emptyList(), listOf(a, b)))
    }

    @Test
    fun aQuickPictureWhenNothingIsStarted() {
        assertEquals(NudgeTarget.Quick("huts"), StreakNudge.target("old-town", listOf("huts", "lighthouse"), listOf(canvas("done", placed = 100))))
        assertNull(StreakNudge.target("old-town", emptyList(), emptyList()))
    }

    @Test
    fun showsForTwoDaysOrMoreUntilAPictureIsFinishedToday() {
        val six = StreakState(length = 6, lastDay = today - 1)
        assertTrue(StreakNudge.show(six, today, Cover.NONE))
        assertFalse(StreakNudge.show(six.copy(lastDay = today), today, Cover.NONE), "finished today")
        assertFalse(StreakNudge.show(six.copy(length = 1), today, Cover.NONE), "a one-day streak")
        assertFalse(StreakNudge.show(StreakState(), today, Cover.NONE), "never played")
        // Two days missed, the day off used: broken without a freeze, alive with one.
        val gap = six.copy(lastDay = today - 3, restDay = today - 4)
        assertFalse(StreakNudge.show(gap, today, Cover.NONE))
        assertTrue(StreakNudge.show(gap, today, Cover(freezes = 2)))
    }

    @Test
    fun theBrokenVariantLastsTheRepairWindow() {
        // 12 days ended yesterday (last played 2 days ago, the day off used last week).
        val broken = StreakState(length = 12, lastDay = today - 2, restDay = today - 5)
        assertEquals(BrokenStreak(12, today - 1), StreakNudge.broken(broken, today, Cover.NONE))
        assertFalse(StreakNudge.show(broken, today, Cover.NONE))
        assertNull(StreakNudge.broken(broken, today + 2, Cover.NONE), "window closed")
        assertNull(StreakNudge.broken(broken.copy(length = 1), today, Cover.NONE), "too short to repair")
    }

    @Test
    fun copyStatesTrueFactsOnly() {
        assertEquals("Finish a picture today to keep your 6 days", StreakNudge.line(6))
        assertEquals("Today's picture is Quick", StreakNudge.hint(NudgeTarget.Today("lighthouse"), "Lighthouse", todayLabel = "Today's picture"))
        assertEquals("Today's pick is Quick", StreakNudge.hint(NudgeTarget.Today("lighthouse"), "Lighthouse", todayLabel = "Today's pick"))
        // In the reminder, or when the Today card shows a drop instead: named.
        assertEquals("Lighthouse is Quick", StreakNudge.hint(NudgeTarget.Today("lighthouse"), "Lighthouse"))
        assertEquals("Beach Huts is Quick", StreakNudge.hint(NudgeTarget.Quick("huts"), "Beach Huts"))
        assertEquals("Harbour is 82% done", StreakNudge.hint(NudgeTarget.Canvas(canvas("harbour", placed = 829, total = 1000)), "Harbour"))
        assertEquals("Your photo canvas is 82% done", StreakNudge.hint(NudgeTarget.Canvas(canvas("photo/x", placed = 82, photo = true)), "Grandma"))
        assertEquals("Your mystery picture is 40% done", StreakNudge.hint(NudgeTarget.Canvas(canvas("lantern", placed = 40)), "Lantern", mystery = true))
        assertNull(StreakNudge.hint(null, null))
    }

    @Test
    fun theAwaitingLineNamesTheDay() {
        val weekday = { _: Long -> "Tuesday" }
        assertEquals("Yesterday is covered when you finish", StreakNudge.awaiting(listOf(CoveredDay(today - 1, DayKind.DAY_OFF)), today, weekday))
        assertEquals("Tuesday is covered when you finish", StreakNudge.awaiting(listOf(CoveredDay(today - 2, DayKind.FREEZE)), today, weekday))
        assertEquals(
            "Your missed days are covered when you finish",
            StreakNudge.awaiting(listOf(CoveredDay(today - 2, DayKind.DAY_OFF), CoveredDay(today - 1, DayKind.FREEZE)), today, weekday),
        )
        assertNull(StreakNudge.awaiting(emptyList(), today, weekday))
    }
}
