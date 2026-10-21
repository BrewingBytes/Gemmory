package com.brewingbytes.gemmory.policy

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** How a calendar or week-row day is drawn (#101, the calendar cell table). */
enum class CellKind {
    /** A picture was finished that day ([CalendarCell.canvas], when known). */
    FINISHED,

    /** Inside the current streak but not in the log (O7): kept, with no picture to show. */
    KEPT,
    DAY_OFF,
    VIP_FREEZE,
    FREEZE,
    REPAIRED,

    /** Missed, and covered once a picture is finished today (G13). */
    AWAITING,

    /** A past day the streak didn't count, or today before a finish. */
    EMPTY,
    FUTURE,
}

/** One [day] of the calendar; [today] gets the lamp outline. */
data class CalendarCell(val day: Long, val kind: CellKind, val canvas: String? = null, val today: Boolean = false)

/** The streak calendar and week row (#101): what each day shows, from the log and the streak as it stands. */
object StreakCalendar {
    /**
     * The cells for [days] on [today]: logged days as they counted ([history], already reconciled), the days a finish
     * today would cover as awaiting, unlogged days inside the current streak as kept (O7), and every other past day
     * empty, never "missed".
     */
    fun cells(days: LongRange, history: List<HistoryDay>, state: StreakState, cover: Cover, today: Long): List<CalendarCell> {
        val logged = history.associateBy { it.day }
        val awaiting = DailyStreak.awaitingCover(state, today, cover).mapTo(HashSet()) { it.day }
        val kept = streakSpan(history, state, cover, today)
        return days.map { day ->
            val entry = logged[day]
            val kind = when {
                day > today -> CellKind.FUTURE
                entry != null -> entry.kind.cell
                day in awaiting -> CellKind.AWAITING
                kept != null && day in kept -> CellKind.KEPT
                else -> CellKind.EMPTY
            }
            CalendarCell(day, kind, entry?.canvas, today = day == today)
        }
    }

    /**
     * The days the current streak covers, if it stands on [today]: back from its last day, logged covered days don't
     * count toward its length, and finished or unlogged days do.
     */
    private fun streakSpan(history: List<HistoryDay>, state: StreakState, cover: Cover, today: Long): LongRange? {
        if (DailyStreak.shown(state, today, cover) == 0) return null
        val logged = history.associateBy { it.day }
        var counted = 0
        var day = state.lastDay
        while (true) {
            val kind = logged[day]?.kind
            if (kind == null || kind == DayKind.FINISHED) counted++
            if (counted >= state.length) return day..state.lastDay
            day--
        }
    }

    /** The first day of the week holding [day], for a week starting on [firstDay] (G12: the locale's). */
    fun weekStart(day: Long, firstDay: DayOfWeek): Long {
        val dow = LocalDate.fromEpochDays(day.toInt()).dayOfWeek
        return day - (dow.ordinal - firstDay.ordinal + 7) % 7
    }

    /** The days of the month holding [day]. */
    fun monthOf(day: Long): LongRange {
        val date = LocalDate.fromEpochDays(day.toInt())
        val first = LocalDate(date.year, date.month, 1)
        val next = if (date.month.ordinal == 11) LocalDate(date.year + 1, 1, 1) else LocalDate(date.year, date.month.ordinal + 2, 1)
        return first.toEpochDays().toLong() until next.toEpochDays().toLong()
    }

    /** The earliest month the calendar opens on: the first logged day's, or today's when nothing is logged. */
    fun firstMonth(history: List<HistoryDay>, today: Long): LongRange = monthOf(history.firstOrNull()?.day ?: today)

    private val DayKind.cell: CellKind
        get() = when (this) {
            DayKind.FINISHED -> CellKind.FINISHED
            DayKind.DAY_OFF -> CellKind.DAY_OFF
            DayKind.VIP_FREEZE -> CellKind.VIP_FREEZE
            DayKind.FREEZE -> CellKind.FREEZE
            DayKind.REPAIRED -> CellKind.REPAIRED
        }
}
