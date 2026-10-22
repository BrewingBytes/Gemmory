package com.brewingbytes.gemmory.policy

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #104 (G14): what each day of the milestone share card shows, and that a photo canvas never does. */
class StreakShareCardTest {
    private fun day(date: String): Long = LocalDate.parse(date).toEpochDays()

    private val isPicture: (String) -> Boolean = { !it.startsWith("PHOTO/") }

    /** A Monday-Sunday week from [start] with one cell per kind (and canvas) given. */
    private fun week(start: Long, vararg cells: Pair<CellKind, String?>) =
        cells.mapIndexed { i, (kind, canvas) -> CalendarCell(start + i, kind, canvas) }

    private val monday = day("2026-10-12")
    private val thursday = monday + 3

    @Test
    fun aMilestoneOnThursdayShowsPicturesFlamesAndEmptyOutlinesAfter() {
        val cells = week(
            monday,
            CellKind.FINISHED to "BUNDLED/a",
            CellKind.FINISHED to "PHOTO/me",
            CellKind.FINISHED to "REMOTE/fox",
            CellKind.FINISHED to "BUNDLED/b",
            CellKind.FUTURE to null,
            CellKind.FUTURE to null,
            CellKind.FUTURE to null,
        )
        assertEquals(
            listOf(
                CardDay.Picture("BUNDLED/a"),
                CardDay.Flame,
                CardDay.Picture("REMOTE/fox"),
                CardDay.Picture("BUNDLED/b"),
                CardDay.Empty,
                CardDay.Empty,
                CardDay.Empty,
            ),
            StreakShareCard.days(cells, thursday, emptyMap(), isPicture),
        )
    }

    @Test
    fun aPhotoFirstThenAPictureTheSameDayShowsThePicture() {
        val cells = week(monday, CellKind.FINISHED to "PHOTO/me")
        val finishes = mapOf(monday to listOf("PHOTO/me", "PHOTO/dog", "BUNDLED/lighthouse"))
        assertEquals(listOf(CardDay.Picture("BUNDLED/lighthouse")), StreakShareCard.days(cells, monday, finishes, isPicture))
    }

    @Test
    fun aFinishedDayWithNoLoggedCanvasLooksUpTheDaysFinishes() {
        val cells = week(monday, CellKind.FINISHED to null, CellKind.FINISHED to null)
        val finishes = mapOf(monday to listOf("BUNDLED/a"))
        assertEquals(listOf(CardDay.Picture("BUNDLED/a"), CardDay.Flame), StreakShareCard.days(cells, monday + 1, finishes, isPicture))
    }

    @Test
    fun keptAndCoveredDaysShowTheFlame() {
        val cells = week(
            monday,
            CellKind.KEPT to null,
            CellKind.DAY_OFF to null,
            CellKind.FREEZE to null,
            CellKind.VIP_FREEZE to null,
            CellKind.REPAIRED to null,
            CellKind.AWAITING to null,
            CellKind.FINISHED to "BUNDLED/a",
        )
        assertEquals(List(6) { CardDay.Flame } + CardDay.Picture("BUNDLED/a"), StreakShareCard.days(cells, monday + 6, emptyMap(), isPicture))
    }

    @Test
    fun aMilestoneOnSundayFillsTheWholeWeek() {
        val cells = (0 until 7).map { CalendarCell(monday + it, CellKind.FINISHED, "BUNDLED/p$it") }
        assertTrue(StreakShareCard.days(cells, monday + 6, emptyMap(), isPicture).none { it == CardDay.Empty })
    }

    @Test
    fun aWeekAcrossTwoMonthsKeepsItsDays() {
        // A milestone on Sunday 1 November: its week starts Monday 26 October.
        val milestone = day("2026-11-01") // a Sunday
        val start = StreakCalendar.weekStart(milestone, DayOfWeek.MONDAY)
        assertEquals(day("2026-10-26"), start)
        val cells = (0 until 7).map { CalendarCell(start + it, CellKind.FINISHED, "BUNDLED/p$it") }
        assertEquals(
            (0 until 7).map { CardDay.Picture("BUNDLED/p$it") },
            StreakShareCard.days(cells, milestone, emptyMap(), isPicture),
        )
    }

    @Test
    fun aPhotoCanvasIsNeverOnTheCard() {
        // Every cell kind, with a photo logged and photos among the day's finishes: no Picture is ever a photo.
        val cells = CellKind.entries.mapIndexed { i, kind -> CalendarCell(monday + i, kind, "PHOTO/p$i") }
        val finishes = cells.associate { it.day to listOf("PHOTO/a${it.day}", "PHOTO/b${it.day}") }
        val days = StreakShareCard.days(cells, monday + cells.size, finishes, isPicture)
        assertTrue(days.none { it is CardDay.Picture }, "$days")
        val mixed = StreakShareCard.days(cells, monday + cells.size, finishes.mapValues { it.value + "BUNDLED/x" }, isPicture)
        assertTrue(mixed.filterIsInstance<CardDay.Picture>().all { isPicture(it.canvas) })
    }
}
