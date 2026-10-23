package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.CellKind
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.Inventory
import java.time.LocalDate

/** A celebration's words (#101, the celebration copy table). */
data class CelebrationText(val title: String, val line: String, val gift: String?, val announcement: String)

/**
 * Every streak string (#101, G7: true facts and weekday names, no clocks, no "!", no guilt). The words are string
 * resources (T48); pure over [Strings], so the copy tables are tested on the JVM against the real English.
 */
class StreakText(private val s: Strings) {
    /** "1 day" / "5 days". */
    fun days(n: Int): String = s.plural(R.plurals.streak_days, n, n)

    /** "5 days in a row". */
    private fun inARow(n: Int): String = s.plural(R.plurals.streak_in_a_row, n, n)

    /** The weekday [day] (local epoch day) falls on, in the app's language: "Tuesday". */
    fun weekday(day: Long): String = Messages.weekday(s, day)

    /** A milestone's title, on the celebration and the share card (#104): "A week of gems"; null on other days. */
    fun milestone(length: Int): String? = when (length) {
        7 -> s.text(R.string.streak_milestone_7)
        30 -> s.text(R.string.streak_milestone_30)
        50 -> s.text(R.string.streak_milestone_50)
        100 -> s.text(R.string.streak_milestone_100)
        365 -> s.text(R.string.streak_milestone_365)
        else -> null
    }

    /** [freezesLeft]: streak freezes still held after this day spent its own (#102), for the freeze line. */
    fun celebration(length: Int, covered: List<CoveredDay>, vip: Boolean, freezesLeft: Int = 0): CelebrationText {
        val milestone = milestone(length)
        val title = milestone ?: if (length == 1) s.text(R.string.streak_started_title) else inARow(length)
        val line = when {
            milestone != null -> s.plural(R.plurals.streak_in_a_row_sentence, length, length)
            length == 1 -> s.text(R.string.streak_line_day_one)
            covered.isNotEmpty() -> keptBy(covered, freezesLeft)
            else -> s.text(R.string.streak_line_next, length + 1)
        }
        val gift = s.text(R.string.streak_gift_line).takeIf { !vip && length % Inventory.SPIN_EVERY == 0 }
        val announcement = if (length == 1) {
            s.text(R.string.streak_started_announcement)
        } else {
            val head = s.plural(R.plurals.streak_in_a_row_sentence, length, length)
            if (covered.isEmpty()) head else s.text(R.string.sentences, head, keptBy(covered, freezesLeft))
        }
        return CelebrationText(title, line, gift, announcement)
    }

    /**
     * "Your day off on Tuesday kept it going." and the other covered-day lines; a day kept by streak freezes alone says
     * how many are [freezesLeft] (#102): "A streak freeze kept it going on Tuesday. 1 left."
     */
    fun keptBy(covered: List<CoveredDay>, freezesLeft: Int = 0): String {
        val kinds = covered.map { it.kind }.distinct()
        if (kinds.size > 1) {
            val joined = kinds.map { phrase(it) }.reduce { a, b -> s.text(R.string.list_and, a, b) }
            return s.text(R.string.streak_kept_joined, joined.replaceFirstChar { it.titlecase(s.locale) })
        }
        val day = weekday(covered.last().day)
        return when (kinds.single()) {
            DayKind.DAY_OFF -> s.text(R.string.streak_kept_day_off, day)
            DayKind.VIP_FREEZE -> s.text(R.string.streak_kept_vip_freeze)
            DayKind.FREEZE -> {
                val kept = if (covered.size > 1) s.text(R.string.streak_kept_freezes, covered.size) else s.text(R.string.streak_kept_freeze, day)
                s.text(R.string.sentences, kept, left(freezesLeft))
            }
            DayKind.FINISHED, DayKind.REPAIRED -> s.text(R.string.streak_kept_going)
        }
    }

    /** "1 left." / "None left." */
    private fun left(freezes: Int): String = if (freezes == 0) s.text(R.string.streak_none_left) else s.text(R.string.streak_left, freezes)

    /** The streak screen's freeze row (#102): "Streak freezes 1" over "Covers a missed day. Hold up to 2." */
    fun freezes(held: Int): Pair<String, String> =
        s.text(R.string.streak_freezes_row, held) to s.text(R.string.streak_freezes_sub, Inventory.MAX_FREEZES)

    private fun phrase(kind: DayKind): String = when (kind) {
        DayKind.DAY_OFF -> s.text(R.string.streak_by_day_off)
        DayKind.VIP_FREEZE -> s.text(R.string.streak_by_vip_freeze)
        DayKind.FREEZE -> s.text(R.string.streak_by_freeze)
        DayKind.FINISHED, DayKind.REPAIRED -> s.text(R.string.streak_by_streak)
    }

    /** The streak screen's line under the hero, or null when today is done (that line is "Done for today"). */
    fun todayStatus(shown: Int, doneToday: Boolean): String = when {
        doneToday -> s.text(R.string.streak_done_today)
        shown == 0 -> s.text(R.string.streak_status_start)
        else -> s.text(R.string.streak_status_keep)
    }

    /** G13: the days a finish today would cover. */
    fun awaiting(days: List<CoveredDay>, today: Long): String? {
        val day = days.singleOrNull() ?: return s.text(R.string.streak_awaiting_several).takeIf { days.isNotEmpty() }
        val name = if (day.day == today - 1) s.text(R.string.day_yesterday) else weekday(day.day)
        val by = when (day.kind) {
            DayKind.DAY_OFF -> s.text(R.string.streak_by_day_off)
            DayKind.VIP_FREEZE -> s.text(R.string.streak_by_vip_freeze)
            DayKind.FREEZE -> s.text(R.string.streak_by_freeze)
            DayKind.FINISHED, DayKind.REPAIRED -> return null
        }
        return s.text(R.string.streak_awaiting_one, name, by)
    }

    /** A weekly cover's row: "Day off ready", or the day it was used and when it's ready again. */
    fun weekly(name: String, lastUsed: Long, today: Long): Pair<String, String?> {
        val ready = lastUsed == Long.MIN_VALUE || today - lastUsed >= 7
        if (ready) return s.text(R.string.streak_weekly_ready, name) to null
        return s.text(R.string.streak_weekly_used, name, weekday(lastUsed)) to s.text(R.string.streak_weekly_again, weekday(lastUsed + 7))
    }

    /** The repair panel's title (#102): "Bring back your 12 days". */
    fun repairTitle(length: Int): String = s.plural(R.plurals.streak_repair_title, length, length)

    /** The repair panel's line: by video, or by VIP's weekly repair while it's [vipReady]. */
    fun repairBody(endedOn: Long, vipReady: Boolean): String =
        s.text(if (vipReady) R.string.streak_repair_body_vip else R.string.streak_repair_body_video, weekday(endedOn))

    /** "You can do this until Thursday." */
    fun repairUntil(day: Long): String = s.text(R.string.streak_repair_until, weekday(day))

    /** VIP's weekly repair was used on [lastUsed]: "VIP repair used Monday · ready again next Monday". */
    fun vipRepairUsed(lastUsed: Long): String = s.text(R.string.streak_vip_repair_used, weekday(lastUsed), weekday(lastUsed + 7))

    /** "Streak back: 12 days" (announced politely). */
    fun restored(length: Int): String = s.plural(R.plurals.streak_restored, length, length)

    /** The days a repair brought back: "Tuesday repaired", "Monday and Tuesday repaired", "3 days repaired". */
    fun repaired(days: List<Long>): String? = when (days.size) {
        0 -> null
        1 -> s.text(R.string.streak_repaired, weekday(days.single()))
        2 -> s.text(R.string.streak_repaired, s.text(R.string.list_and, weekday(days[0]), weekday(days[1])))
        else -> s.plural(R.plurals.streak_days_repaired, days.size, days.size)
    }

    /** The celebration's text button on day 1 while the old streak can come back (#102): "Your 12 days can still be restored". */
    fun stillRestorable(length: Int): String = s.plural(R.plurals.streak_still_restorable, length, length)

    /** TalkBack for the chip (design system, #101 accessibility). */
    fun chip(shown: Int, doneToday: Boolean): String =
        s.plural(if (doneToday) R.plurals.streak_chip_done else R.plurals.streak_chip_not_done, shown, shown)

    /** TalkBack for the chip's "0 ›" while a repair is offered (#102, G4). */
    fun chipBroken(length: Int): String = s.plural(R.plurals.streak_chip_broken, length, length)

    /** TalkBack for the week row, one node: "This week: Monday, Tuesday done, Wednesday day off, Thursday today". */
    fun week(cells: List<CalendarCell>): String {
        val days = cells.filter { it.kind != CellKind.FUTURE }.map { cell ->
            val name = weekday(cell.day)
            when {
                cell.today && cell.kind == CellKind.FINISHED -> s.text(R.string.streak_week_today_done, name)
                cell.today -> s.text(R.string.streak_week_today, name)
                else -> s.text(R.string.streak_week_day, name, what(cell.kind) ?: s.text(R.string.streak_what_not_counted))
            }
        }
        return s.text(R.string.streak_week, days.joinToString(s.text(R.string.list_separator)))
    }

    /** How a day counted, for TalkBack ("done", "day off"); null for a day that didn't. */
    fun what(kind: CellKind): String? = when (kind) {
        CellKind.FINISHED, CellKind.KEPT -> s.text(R.string.streak_what_done)
        CellKind.DAY_OFF -> s.text(R.string.streak_what_day_off)
        CellKind.VIP_FREEZE -> s.text(R.string.streak_what_vip_freeze)
        CellKind.FREEZE -> s.text(R.string.streak_what_freeze)
        CellKind.REPAIRED -> s.text(R.string.streak_what_repaired)
        CellKind.AWAITING -> s.text(R.string.streak_what_awaiting)
        CellKind.EMPTY, CellKind.FUTURE -> null
    }

    /** TalkBack for a calendar day: "Tuesday 14, finished Lighthouse". */
    fun cell(day: Long, today: Long, what: String?): String {
        val date = LocalDate.ofEpochDay(day)
        val name = if (day == today) s.text(R.string.day_today) else s.text(R.string.streak_cell_day, weekday(day), date.dayOfMonth)
        return if (what == null) name else s.text(R.string.streak_cell_what, name, what)
    }

    companion object {
        /** The milestone days (G10): a bigger celebration with the lamp edge. */
        val MILESTONES = listOf(7, 30, 50, 100, 365)

        fun isMilestone(length: Int): Boolean = length in MILESTONES

        /** `streak_celebration_shown`'s saved_by: `finish`, or what covered the missed days, joined by `+`. */
        fun savedBy(covered: List<CoveredDay>): String =
            if (covered.isEmpty()) "finish" else covered.map { it.kind }.distinct().joinToString("+") { it.name.lowercase() }
    }
}
