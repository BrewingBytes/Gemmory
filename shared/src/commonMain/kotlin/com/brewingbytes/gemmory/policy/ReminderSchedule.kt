package com.brewingbytes.gemmory.policy

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When the evening reminder (T35) is due: a local time of day from 17:00 to 22:00 in 15-minute steps (default 20:30),
 * as minutes since midnight. Pure, so DST and time-zone moves are tested on the JVM.
 */
object ReminderSchedule {
    const val EARLIEST = 17 * 60
    const val LATEST = 22 * 60
    const val STEP = 15
    const val DEFAULT = 20 * 60 + 30

    /** Every time the Reminder time sheet offers, earliest first (21 of them). */
    val CHOICES: List<Int> = (EARLIEST..LATEST step STEP).toList()

    /** [minutes] if the sheet offers it, else the default (a stored value from a future range, say). */
    fun valid(minutes: Int): Int = if (minutes in CHOICES) minutes else DEFAULT

    /**
     * The first moment strictly after [afterMillis] when the local clock in [zone] reads [minutes]. A time that a DST
     * jump skips on that day lands just after the jump.
     */
    fun nextTrigger(afterMillis: Long, minutes: Int, zone: TimeZone): Long {
        val time = LocalTime(minutes / 60, minutes % 60)
        val date = Instant.fromEpochMilliseconds(afterMillis).toLocalDateTime(zone).date
        val today = LocalDateTime(date, time).toInstant(zone).toEpochMilliseconds()
        if (today > afterMillis) return today
        return LocalDateTime(date.plus(DatePeriod(days = 1)), time).toInstant(zone).toEpochMilliseconds()
    }

    /** The local date (epoch days) that [millis] falls on in [zone]: the evening a scheduled run belongs to. */
    fun dayOf(millis: Long, zone: TimeZone): Long = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date.toEpochDays()
}
