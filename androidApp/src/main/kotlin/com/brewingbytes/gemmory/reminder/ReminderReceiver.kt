package com.brewingbytes.gemmory.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brewingbytes.gemmory.GemmoryApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The evening alarm (T35, eng D8). Enabled only while reminders are on (eng D11). The work runs in [goAsync], all of
 * it inside [RUN_BUDGET_MS] (the catalog check gets 8 s of it), and the next evening is armed whatever happened.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EVENING) return
        val app = context.applicationContext as GemmoryApp
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        val pending = goAsync()
        app.appScope.launch {
            try {
                withTimeoutOrNull(RUN_BUDGET_MS) { app.runEveningReminder(scheduledAt) }
            } finally {
                app.reminders.rescheduleAfterRun(scheduledAt)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EVENING = "com.brewingbytes.gemmory.action.EVENING_REMINDER"

        /** The wall time the alarm was set for: the evening this run belongs to. */
        const val EXTRA_SCHEDULED_AT = "scheduled_at"

        /** Under the 10 s a `goAsync` receiver may hold before Android calls it an ANR. */
        const val RUN_BUDGET_MS = 9_500L
    }
}

/**
 * Re-arms the alarm after a reboot, an app update, a clock change or a time-zone change (T35). Enabled only while
 * reminders are on (eng D11); an app start also re-arms it, since a force-stop clears alarms.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> (context.applicationContext as GemmoryApp).reminders.reschedule()
        }
    }
}
