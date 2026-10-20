package com.brewingbytes.gemmory.reminder

import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.policy.ReminderSchedule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The evening alarm (T35, eng D8): the next local time, across DST and time-zone moves, and only while on. */
class ReminderSchedulerTest {
    private var zone = TimeZone.of("Europe/Bucharest")
    private var now = at("2026-10-20T10:00")
    private val prefs = ReminderPrefs(FakePrefs())
    private var armed: Long? = null
    private var receivers: Boolean? = null
    private val scheduler = ReminderScheduler(
        prefs,
        object : ReminderAlarm {
            override fun set(triggerMillis: Long) {
                armed = triggerMillis
            }

            override fun cancel() {
                armed = null
            }
        },
        { receivers = it },
        { now },
        { zone },
    )

    private fun at(local: String, z: TimeZone = zone) = LocalDateTime.parse(local).toInstant(z).toEpochMilliseconds()

    private val hour = 60 * 60 * 1000L

    @Test
    fun offCancelsTheAlarmAndDisablesTheReceivers() {
        prefs.on = true
        scheduler.schedule()
        prefs.on = false
        scheduler.schedule()
        assertNull(armed)
        assertEquals(false, receivers)
        assertEquals(0L, prefs.scheduledAt)
    }

    @Test
    fun onArmsTonightOrTomorrowAtTheChosenTime() {
        prefs.on = true
        scheduler.schedule()
        assertEquals(at("2026-10-20T20:30"), armed)
        assertEquals(true, receivers)
        assertEquals(armed, prefs.scheduledAt)
        now = at("2026-10-20T21:00")
        scheduler.schedule()
        assertEquals(at("2026-10-21T20:30"), armed)
        prefs.minutes = 17 * 60
        scheduler.schedule()
        assertEquals(at("2026-10-21T17:00"), armed)
    }

    /** After a run, never the same evening again, even if the alarm came a moment before its time. */
    @Test
    fun afterARunTheNextIsTomorrow() {
        prefs.on = true
        scheduler.schedule()
        now = at("2026-10-20T20:29:59")
        scheduler.schedule(afterMillis = prefs.scheduledAt)
        assertEquals(at("2026-10-21T20:30"), armed)
    }

    /** Bucharest leaves summer time on 2026-10-25 and enters it on 2027-03-28: the evening stays at 20:30 local. */
    @Test
    fun theTimeHoldsAcrossDaylightSavingChanges() {
        prefs.on = true
        now = at("2026-10-24T21:00")
        scheduler.schedule()
        assertEquals(at("2026-10-25T20:30"), armed)
        assertEquals(25 * hour, armed!! - at("2026-10-24T20:30"))
        now = at("2027-03-27T21:00")
        scheduler.schedule()
        assertEquals(at("2027-03-28T20:30"), armed)
        assertEquals(23 * hour, armed!! - at("2027-03-27T20:30"))
    }

    /** A flight to Tokyo: the time-zone broadcast re-arms it for 20:30 there. */
    @Test
    fun aTimeZoneChangeMovesItToTheNewLocalEvening() {
        prefs.on = true
        scheduler.schedule()
        zone = TimeZone.of("Asia/Tokyo")
        scheduler.schedule()
        assertEquals(at("2026-10-20T20:30", TimeZone.of("Asia/Tokyo")), armed)
    }

    @Test
    fun aStoredTimeOutsideTheChoicesFallsBackToTheDefault() {
        prefs.minutes = 3 * 60
        assertEquals(ReminderSchedule.DEFAULT, prefs.minutes)
        assertEquals(21, ReminderSchedule.CHOICES.size)
        assertEquals(17 * 60, ReminderSchedule.CHOICES.first())
        assertEquals(22 * 60, ReminderSchedule.CHOICES.last())
    }
}
