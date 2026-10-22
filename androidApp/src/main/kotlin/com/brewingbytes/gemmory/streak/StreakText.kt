package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.CellKind
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.Inventory
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A celebration's words (#101, the celebration copy table). */
data class CelebrationText(val title: String, val line: String, val gift: String?, val announcement: String)

/**
 * Every streak string (#101, G7: true facts and weekday names, no clocks, no "!", no guilt). Pure, so the copy tables
 * are tested on the JVM.
 */
object StreakText {
    /** The milestone days (G10): a bigger celebration with the lamp edge. */
    val MILESTONES = listOf(7, 30, 50, 100, 365)

    fun isMilestone(length: Int): Boolean = length in MILESTONES

    /** "1 day" / "5 days". */
    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

    /** The weekday [day] (local epoch day) falls on: "Tuesday". */
    fun weekday(day: Long): String = LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** A milestone's title, on the celebration and the share card (#104): "A week of gems"; null on other days. */
    fun milestone(length: Int): String? = when (length) {
        7 -> "A week of gems"
        30 -> "A month of gems"
        50 -> "50 days of gems"
        100 -> "100 days of gems"
        365 -> "A year of gems"
        else -> null
    }

    /** [freezesLeft]: streak freezes still held after this day spent its own (#102), for the freeze line. */
    fun celebration(length: Int, covered: List<CoveredDay>, vip: Boolean, freezesLeft: Int = 0): CelebrationText {
        val milestone = milestone(length)
        val title = milestone ?: if (length == 1) "You started a streak" else "${days(length)} in a row"
        val line = when {
            milestone != null -> "${days(length)} in a row."
            length == 1 -> "Finish a picture tomorrow to make it 2."
            covered.isNotEmpty() -> keptBy(covered, freezesLeft)
            else -> "Finish one tomorrow to make it ${length + 1}."
        }
        val gift = "Your 5-day gift spin is in the bag.".takeIf { !vip && length % Inventory.SPIN_EVERY == 0 }
        val kept = if (covered.isEmpty()) "" else " " + keptBy(covered, freezesLeft)
        val announcement = if (length == 1) "Streak started. 1 day." else "${days(length)} in a row.$kept"
        return CelebrationText(title, line, gift, announcement)
    }

    /**
     * "Your day off on Tuesday kept it going." and the other covered-day lines; a day kept by streak freezes alone says
     * how many are [freezesLeft] (#102): "A streak freeze kept it going on Tuesday. 1 left."
     */
    fun keptBy(covered: List<CoveredDay>, freezesLeft: Int = 0): String {
        val kinds = covered.map { it.kind }.distinct()
        if (kinds.size > 1) return "${kinds.joinToString(" and ") { phrase(it) }.replaceFirstChar { it.uppercase() }} kept it going."
        val left = " " + left(freezesLeft)
        val day = weekday(covered.last().day)
        return when (kinds.single()) {
            DayKind.DAY_OFF -> "Your day off on $day kept it going."
            DayKind.VIP_FREEZE -> "Your VIP freeze kept it going."
            DayKind.FREEZE -> if (covered.size > 1) "${covered.size} streak freezes kept it going.$left" else "A streak freeze kept it going on $day.$left"
            DayKind.FINISHED, DayKind.REPAIRED -> "Your streak kept going."
        }
    }

    /** "1 left." / "None left." */
    private fun left(freezes: Int): String = if (freezes == 0) "None left." else "$freezes left."

    /** The streak screen's freeze row (#102): "Streak freezes 1" over "Covers a missed day. Hold up to 2." */
    fun freezes(held: Int): Pair<String, String> = "Streak freezes $held" to "Covers a missed day. Hold up to ${Inventory.MAX_FREEZES}."

    private fun phrase(kind: DayKind): String = when (kind) {
        DayKind.DAY_OFF -> "your day off"
        DayKind.VIP_FREEZE -> "your VIP freeze"
        DayKind.FREEZE -> "a streak freeze"
        DayKind.FINISHED, DayKind.REPAIRED -> "your streak"
    }

    /** `streak_celebration_shown`'s saved_by: `finish`, or what covered the missed days, joined by `+`. */
    fun savedBy(covered: List<CoveredDay>): String =
        if (covered.isEmpty()) "finish" else covered.map { it.kind }.distinct().joinToString("+") { it.name.lowercase() }

    /** The streak screen's line under the hero, or null when today is done (that line is "Done for today"). */
    fun todayStatus(shown: Int, doneToday: Boolean): String = when {
        doneToday -> "Done for today"
        shown == 0 -> "Finish any picture to start a streak."
        else -> "Finish a picture today to keep it"
    }

    /** G13: the days a finish today would cover. */
    fun awaiting(days: List<CoveredDay>, today: Long): String? {
        val day = days.singleOrNull() ?: return "Your missed days are covered when you finish today.".takeIf { days.isNotEmpty() }
        val name = if (day.day == today - 1) "Yesterday" else weekday(day.day)
        val by = when (day.kind) {
            DayKind.DAY_OFF -> "your day off"
            DayKind.VIP_FREEZE -> "your VIP freeze"
            DayKind.FREEZE -> "a streak freeze"
            DayKind.FINISHED, DayKind.REPAIRED -> return null
        }
        return "$name is covered by $by when you finish today."
    }

    /** A weekly cover's row: "Day off ready", or the day it was used and when it's ready again. */
    fun weekly(name: String, lastUsed: Long, today: Long): Pair<String, String?> {
        val ready = lastUsed == Long.MIN_VALUE || today - lastUsed >= 7
        if (ready) return "$name ready" to null
        return "$name used ${weekday(lastUsed)}" to "Ready again next ${weekday(lastUsed + 7)}"
    }

    /** The repair panel's title (#102): "Bring back your 12 days". */
    fun repairTitle(length: Int): String = "Bring back your ${days(length)}"

    /** The repair panel's line: by video, or by VIP's weekly repair while it's [vipReady]. */
    fun repairBody(endedOn: Long, vipReady: Boolean): String =
        "Your streak ended on ${weekday(endedOn)}. " +
            if (vipReady) "Your weekly VIP repair can bring it back." else "Watch a short video to restore it."

    /** "You can do this until Thursday." */
    fun repairUntil(day: Long): String = "You can do this until ${weekday(day)}."

    /** VIP's weekly repair was used on [lastUsed]: "VIP repair used Monday · ready again next Monday". */
    fun vipRepairUsed(lastUsed: Long): String = "VIP repair used ${weekday(lastUsed)} · ready again next ${weekday(lastUsed + 7)}"

    /** "Streak back: 12 days" (announced politely). */
    fun restored(length: Int): String = "Streak back: ${days(length)}"

    /** The days a repair brought back: "Tuesday repaired", "Monday and Tuesday repaired", "3 days repaired". */
    fun repaired(days: List<Long>): String? = when (days.size) {
        0 -> null
        1 -> "${weekday(days.single())} repaired"
        2 -> "${weekday(days[0])} and ${weekday(days[1])} repaired"
        else -> "${days.size} days repaired"
    }

    /** The celebration's text button on day 1 while the old streak can come back (#102): "Your 12 days can still be restored". */
    fun stillRestorable(length: Int): String = "Your ${days(length)} can still be restored"

    /** TalkBack for the chip (design system, #101 accessibility). */
    fun chip(shown: Int, doneToday: Boolean): String =
        "${days(shown)} in a row, ${if (doneToday) "done today" else "not done today"}, opens your streak"

    /** TalkBack for the chip's "0 ›" while a repair is offered (#102, G4). */
    fun chipBroken(length: Int): String = "0 days in a row, your ${days(length)} can still be restored, opens your streak"

    /** TalkBack for the week row, one node: "This week: Monday, Tuesday done, Wednesday day off, Thursday today". */
    fun week(cells: List<CalendarCell>): String = "This week: " + cells.filter { it.kind != CellKind.FUTURE }.joinToString(", ") { cell ->
        val name = weekday(cell.day)
        when {
            cell.today && cell.kind == CellKind.FINISHED -> "$name today, done"
            cell.today -> "$name today"
            else -> "$name ${what(cell.kind) ?: "not counted"}"
        }
    }

    /** How a day counted, for TalkBack ("done", "day off"); null for a day that didn't. */
    fun what(kind: CellKind): String? = when (kind) {
        CellKind.FINISHED, CellKind.KEPT -> "done"
        CellKind.DAY_OFF -> "day off"
        CellKind.VIP_FREEZE -> "kept by your VIP freeze"
        CellKind.FREEZE -> "kept by a streak freeze"
        CellKind.REPAIRED -> "repaired"
        CellKind.AWAITING -> "covered when you finish today"
        CellKind.EMPTY, CellKind.FUTURE -> null
    }

    /** TalkBack for a calendar day: "Tuesday 14, finished Lighthouse". */
    fun cell(day: Long, today: Long, what: String?): String {
        val date = LocalDate.ofEpochDay(day)
        val name = if (day == today) "Today" else "${weekday(day)} ${date.dayOfMonth}"
        return if (what == null) name else "$name, $what"
    }
}
