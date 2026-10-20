package com.brewingbytes.gemmory.reminder

import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.policy.ReminderInputs
import com.brewingbytes.gemmory.policy.ReminderOutcome
import com.brewingbytes.gemmory.policy.ReminderReasons
import com.brewingbytes.gemmory.policy.ReminderSchedule
import com.brewingbytes.gemmory.policy.StreakState
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.datetime.TimeZone

/**
 * One evening's run (T35), inside the receiver's 10 s (eng D8). A late run stops before the network. Otherwise the
 * catalog check when due (≤ 8 s, eng D5), then one read of what's on the phone (eng D12), Today's pick through the
 * gallery's own shared pick, which stores it (eng D3), [ReminderReasons], and the post. Analytics runs under the
 * consent the app applied in onCreate (eng D6).
 */
class ReminderJob(
    private val clock: DayClock,
    private val zone: () -> TimeZone,
    /** The guarded catalog check ([com.brewingbytes.gemmory.remote.RemotePictures.checkForReminder]). */
    private val check: suspend () -> Unit,
    private val canvases: suspend () -> List<ReminderCanvas>,
    /** Today's drops, stored and verified, in catalog order. */
    private val todaysDrops: () -> List<String>,
    /** Today's picture (or VIP's pick) for these started ids, picking and storing it if the day has none yet. */
    private val pickToday: (started: Set<String>) -> String?,
    private val streak: () -> StreakState,
    private val foreground: () -> Boolean,
    /** Posts the notification; false when the system wouldn't (notifications blocked). */
    private val notify: (ReminderOutcome.Post) -> Boolean,
    private val analytics: Analytics,
) {
    suspend fun run(scheduledMillis: Long): ReminderOutcome {
        val scheduledDay = ReminderSchedule.dayOf(scheduledMillis, zone())
        early(scheduledMillis, scheduledDay)?.let { return it }
        check()
        val started = canvases()
        val inputs = ReminderInputs(
            scheduledMillis = scheduledMillis,
            scheduledDay = scheduledDay,
            nowMillis = clock.nowMillis(),
            today = clock.today(),
            foreground = foreground(),
            todaysDrops = todaysDrops(),
            todaysPick = pickToday(started.mapNotNullTo(HashSet()) { it.pictureId }),
            started = started,
            streak = streak(),
        )
        val outcome = ReminderReasons.decide(inputs)
        when (outcome) {
            is ReminderOutcome.Post -> if (notify(outcome)) analytics.log(AnalyticsEvent.reminderShown(outcome.reason))
            is ReminderOutcome.Late -> analytics.log(AnalyticsEvent.reminderLate(outcome.minutes))
            is ReminderOutcome.Quiet -> Unit
        }
        return outcome
    }

    /** Late already (the alarm came hours after its time, or the next day): logged, and nothing else runs. */
    private fun early(scheduledMillis: Long, scheduledDay: Long): ReminderOutcome.Late? {
        val minutes = ReminderReasons.lateMinutes(scheduledMillis, scheduledDay, clock.nowMillis(), clock.today()) ?: return null
        analytics.log(AnalyticsEvent.reminderLate(minutes))
        return ReminderOutcome.Late(minutes)
    }
}
