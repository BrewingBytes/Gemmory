package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemotePicture
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/** The drop calendar (T44, CEO C4): warn below 14 days, fail below 7, pass while nothing is dated. */
class DropCalendarTest {
    private val today = LocalDate(2026, 11, 1)

    private fun picture(id: String, from: LocalDate?) = RemotePicture(
        id, id, AspectRatio.SQUARE, CanvasSize.QUICK, 36, 36, 16, "grids/$id.gmrg", "0".repeat(64), 0, true, 13, from ?: today, from,
    )

    private fun days(from: Int, count: Int) = (from until from + count).map { picture("d$it", today.plus(DatePeriod(days = it))) }

    @Test
    fun theThresholds() {
        assertEquals(DropCalendar.Result.Ok(14), DropCalendar.check(days(0, 14), today))
        assertEquals(DropCalendar.Result.Warn(13), DropCalendar.check(days(0, 13), today))
        assertEquals(DropCalendar.Result.Warn(7), DropCalendar.check(days(0, 7), today))
        assertEquals(DropCalendar.Result.Fail(6), DropCalendar.check(days(0, 6), today))
        assertEquals(DropCalendar.Result.Fail(0), DropCalendar.check(days(-10, 5), today), "everything already opened")
    }

    @Test
    fun noDatedPictureMeansNothingToKeepUp() {
        assertEquals(DropCalendar.Result.NoDrops, DropCalendar.check(emptyList(), today))
        assertEquals(DropCalendar.Result.NoDrops, DropCalendar.check(listOf(picture("plain", null)), today))
    }

    @Test
    fun aMissedDateEndsTheRunway() {
        assertEquals(DropCalendar.Result.Fail(3), DropCalendar.check(days(0, 3) + days(4, 20), today))
        assertEquals(DropCalendar.Result.Fail(0), DropCalendar.check(days(1, 30), today), "nothing opens today")
    }

    @Test
    fun todayIsWhereADateOpensFirst() {
        // 2026-11-01T10:00Z is already 2026-11-02 in UTC+14.
        assertEquals(LocalDate(2026, 11, 2), DropCalendar.today(1_793_527_200_000))
        assertEquals(LocalDate(2026, 11, 1), DropCalendar.today(1_793_527_200_000 - 11 * 3_600_000))
    }
}
