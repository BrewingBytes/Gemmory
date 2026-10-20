package com.brewingbytes.gemmory.reminder

import android.content.Context
import android.text.format.DateFormat
import com.brewingbytes.gemmory.policy.ReminderAsk
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/** How the reminder ask ended (`reminder_prompt_result`). */
enum class AskResult(val param: String) {
    GRANTED("granted"),
    DECLINED_IN_APP("declined_in_app"),
    DENIED_SYSTEM("denied_system"),
}

/**
 * The evening reminder (T35) as the screens see it: on/off and the time for Settings, the ask after a fresh finish,
 * and the gallery's one "Reminders on" line. Every change re-arms the alarm through [scheduler].
 */
class Reminders(
    private val prefs: ReminderPrefs,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val analytics: Analytics,
) {
    private val _on = MutableStateFlow(prefs.on)

    /** The player wants reminders. Settings shows the switch on only while [notificationsAllowed] too. */
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _minutes = MutableStateFlow(prefs.minutes)

    /** The reminder time, minutes since local midnight. */
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    private val _onLine = MutableStateFlow(prefs.onLinePending)

    /** The gallery's one "Reminders on" line is waiting (plan-design-review D12). */
    val onLine: StateFlow<Boolean> = _onLine.asStateFlow()

    /** Android 13+'s dialog was shown before, so a request now may not show it again. */
    val permissionAsked: Boolean get() = prefs.permissionAsked

    fun notificationsAllowed(): Boolean = notifier.allowed()

    fun setOn(on: Boolean) {
        if (on == prefs.on) return
        prefs.on = on
        _on.value = on
        if (on) notifier.ensureChannel()
        scheduler.schedule()
        analytics.log(AnalyticsEvent.reminderSettingsChanged(on, prefs.minutes))
    }

    fun setMinutes(minutes: Int) {
        if (minutes == prefs.minutes) return
        prefs.minutes = minutes
        _minutes.value = prefs.minutes
        scheduler.schedule()
        analytics.log(AnalyticsEvent.reminderSettingsChanged(prefs.on, prefs.minutes))
    }

    /** App start, boot, update, clock or time-zone change. */
    fun reschedule() = scheduler.schedule()

    /** When the pending alarm is due, or 0 while reminders are off. */
    val scheduledAt: Long get() = prefs.scheduledAt

    /** After the run for [scheduledAt]: the next evening, never the same one again even if the alarm came a moment early. */
    fun rescheduleAfterRun(scheduledAt: Long) = scheduler.schedule(afterMillis = scheduledAt)

    fun permissionRequested() {
        prefs.permissionAsked = true
    }

    /**
     * Back on the gallery from a fresh finish's completion screen: counts the finish and returns the ask's attempt (1
     * or 2) when it's due now, else null, and the interstitial check runs as before (one interruption per return).
     */
    fun onLeftFreshFinish(): Int? {
        prefs.finishes = prefs.finishes + 1
        return ReminderAsk.attemptDue(prefs.ask, prefs.finishes, prefs.on)
    }

    fun askShown(attempt: Int) = analytics.log(AnalyticsEvent.reminderPromptShown(attempt))

    fun askAnswered(result: AskResult) {
        prefs.ask = when (result) {
            AskResult.DECLINED_IN_APP -> ReminderAsk.afterNotNow(prefs.ask)
            AskResult.GRANTED, AskResult.DENIED_SYSTEM -> ReminderAskState.DONE
        }
        analytics.log(AnalyticsEvent.reminderPromptResult(result.param))
        if (result == AskResult.GRANTED) {
            setOn(true)
            prefs.onLinePending = true
            _onLine.value = true
        }
    }

    fun onLineShown() {
        prefs.onLinePending = false
        _onLine.value = false
    }

    companion object {
        /** "20:30", or "8:30 PM" on a phone set to 12-hour time (design system, Reminder time sheet). */
        fun timeLabel(context: Context, minutes: Int): String {
            val at = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
            }
            return DateFormat.getTimeFormat(context).format(at.time)
        }
    }
}
