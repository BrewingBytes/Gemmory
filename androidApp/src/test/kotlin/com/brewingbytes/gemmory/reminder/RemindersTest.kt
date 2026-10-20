package com.brewingbytes.gemmory.reminder

import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The reminder ask's answers (T35): what turns reminders on, what's never asked again, and when the gallery line shows. */
class RemindersTest {
    private val zone = TimeZone.of("Europe/Bucharest")
    private val prefs = ReminderPrefs(FakePrefs())
    private var allowed = true
    private var armed: Long? = null
    private val events = mutableListOf<AnalyticsEvent>()
    private val reminders = Reminders(
        prefs,
        ReminderScheduler(
            prefs,
            object : ReminderAlarm {
                override fun set(triggerMillis: Long) {
                    armed = triggerMillis
                }

                override fun cancel() {
                    armed = null
                }
            },
            {},
            { LocalDateTime.parse("2026-10-20T10:00").toInstant(zone).toEpochMilliseconds() },
            { zone },
        ),
        object : ReminderChannel {
            override fun allowed() = allowed

            override fun ensureChannel() = Unit
        },
        object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        },
    )

    @Test
    fun yesWithNotificationsAllowedTurnsThemOnAndShowsTheLineOnce() {
        assertEquals(1, reminders.onLeftFreshFinish())
        reminders.askAnswered(AskResult.GRANTED)
        assertTrue(reminders.on.value)
        assertTrue(armed != null)
        assertTrue(reminders.onLine.value)
        assertEquals(ReminderAskState.DONE, prefs.ask)
        reminders.onLineShown()
        assertFalse(reminders.onLine.value)
        assertNull(reminders.onLeftFreshFinish(), "never asked again")
    }

    /** Founder, 2026-10-20: Yes with Gemmory's notifications off turns them on (the system page opens), with no line. */
    @Test
    fun yesWithNotificationsBlockedTurnsThemOnWithoutTheLine() {
        allowed = false
        reminders.onLeftFreshFinish()
        reminders.askAnswered(AskResult.BLOCKED)
        assertTrue(reminders.on.value, "on, so reminders resume once Android allows them")
        assertFalse(reminders.onLine.value, "no \"Reminders on\" while nothing can post")
        assertEquals(ReminderAskState.DONE, prefs.ask)
        assertEquals(AnalyticsEvent("reminder_prompt_result", mapOf("result" to "blocked")), events.first { it.name == "reminder_prompt_result" })
        assertNull(reminders.onLeftFreshFinish())
    }

    @Test
    fun notNowAsksOnceMoreAndASystemDenialNever() {
        reminders.onLeftFreshFinish()
        reminders.askAnswered(AskResult.DECLINED_IN_APP)
        assertFalse(reminders.on.value)
        assertNull(reminders.onLeftFreshFinish())
        assertEquals(2, reminders.onLeftFreshFinish(), "the 3rd finish asks again")
        reminders.askAnswered(AskResult.DENIED_SYSTEM)
        assertFalse(reminders.on.value)
        assertFalse(reminders.onLine.value)
        assertNull(reminders.onLeftFreshFinish())
    }
}
