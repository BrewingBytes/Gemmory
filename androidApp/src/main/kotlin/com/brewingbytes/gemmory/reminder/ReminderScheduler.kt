package com.brewingbytes.gemmory.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.brewingbytes.gemmory.policy.ReminderSchedule
import kotlinx.datetime.TimeZone

/** The one alarm the reminder uses (eng D8), behind a port so the schedule is tested on the JVM. */
interface ReminderAlarm {
    fun set(triggerMillis: Long)

    fun cancel()
}

/** The reminder's receivers, enabled only while reminders are on (eng D11), so nothing else starts the process. */
fun interface ReminderComponents {
    fun setEnabled(on: Boolean)
}

/**
 * Keeps the alarm in step with the settings (T35): armed for the next chosen local time while reminders are on, with
 * the receivers enabled; cancelled, with the receivers disabled, while off. Called after every run, on boot, on an
 * app update, a clock or time-zone change, any Settings change and every app start (a force-stop clears alarms).
 */
class ReminderScheduler(
    private val prefs: ReminderPrefs,
    private val alarm: ReminderAlarm,
    private val components: ReminderComponents,
    private val nowMillis: () -> Long,
    private val zone: () -> TimeZone,
) {
    /** Arms the next run strictly after [afterMillis] (after a run: its own scheduled time, so it never fires twice). */
    @Synchronized
    fun schedule(afterMillis: Long = nowMillis()) {
        if (!prefs.on) {
            alarm.cancel()
            prefs.scheduledAt = 0L
            components.setEnabled(false)
            return
        }
        components.setEnabled(true)
        val trigger = ReminderSchedule.nextTrigger(maxOf(afterMillis, nowMillis()), prefs.minutes, zone())
        prefs.scheduledAt = trigger
        alarm.set(trigger)
    }
}

/**
 * [ReminderAlarm] on `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP)` (eng D8): no exact-alarm permission and no new
 * dependency, so it fires during Doze, usually within the hour on Android 12+, with no delivery guarantee.
 */
class SystemReminderAlarm(context: Context) : ReminderAlarm {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AlarmManager::class.java)

    /**
     * The alarm carries the time it was set for: the process it starts re-arms the next evening in onCreate, before
     * the receiver runs, so the receiver can't read it back from the preferences.
     */
    private fun intent(triggerMillis: Long = 0L): PendingIntent = PendingIntent.getBroadcast(
        app,
        REQUEST,
        Intent(app, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_EVENING)
            .putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, triggerMillis),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun set(triggerMillis: Long) {
        manager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, intent(triggerMillis))
    }

    override fun cancel() {
        manager?.cancel(intent())
    }

    private companion object {
        const val REQUEST = 35
    }
}

/** [ReminderComponents] through the package manager: the alarm and the reschedule receivers, together. */
class SystemReminderComponents(context: Context) : ReminderComponents {
    private val app = context.applicationContext

    override fun setEnabled(on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        for (receiver in listOf(ReminderReceiver::class.java, ReminderRescheduleReceiver::class.java)) {
            val name = ComponentName(app, receiver)
            if (app.packageManager.getComponentEnabledSetting(name) != state) {
                app.packageManager.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
