package com.brewingbytes.gemmory.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.glance.appwidget.updateAll
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.streak.StreakView
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the streak widget current (#103). It follows the StreakView (O8), so a finish, a repair, a freeze or a VIP
 * change reaches it on the same signals as the chip; just after local midnight an inexact alarm re-reads the day, and a
 * clock or time-zone change, a reboot or an app update does too. The alarm and both receivers are on only while a
 * widget is placed.
 */
class StreakWidgets(
    private val context: Context,
    private val scope: CoroutineScope,
    private val analytics: Analytics,
    /** Millis to the next local midnight (the app's DayClock). */
    private val untilMidnight: () -> Long,
) {
    private val _state = MutableStateFlow(StreakWidgetState(0, lit = false))

    /** What every placed widget shows. */
    val state: StateFlow<StreakWidgetState> = _state.asStateFlow()

    /** Connected tests only: the last state sent to the placed widgets. */
    @VisibleForTesting
    val pushed = MutableStateFlow<StreakWidgetState?>(null)

    private var following: Job? = null

    /** Follows [view] (a new instance replaces the last one). */
    fun follow(view: StreakView) {
        following?.cancel()
        _state.value = StreakWidgetState.of(view.snapshot.value)
        following = scope.launch {
            view.snapshot.map(StreakWidgetState::of).distinctUntilChanged().collect { push(it) }
        }
    }

    /** The day may have changed (midnight, a clock or time-zone change, a reboot): re-reads it, then re-arms. */
    suspend fun refresh(view: StreakView) {
        push(StreakWidgetState.of(view.refresh()))
        arm()
    }

    /** The app came to the screen: a force-stop clears alarms, so a placed widget's midnight is re-armed. */
    fun onForeground() {
        if (placed()) arm()
    }

    /** The first widget was placed. */
    fun onAdded() {
        analytics.log(AnalyticsEvent.streakWidgetAdded())
        setReceiverEnabled(true)
        arm()
    }

    /** The last widget was removed. */
    fun onRemoved() {
        setReceiverEnabled(false)
        alarms()?.cancel(midnightIntent())
    }

    private suspend fun push(state: StreakWidgetState) {
        _state.value = state
        pushed.value = state
        if (placed()) StreakWidget().updateAll(context)
    }

    private fun placed(): Boolean =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, StreakWidgetReceiver::class.java))?.isNotEmpty() == true

    /**
     * Just after the next local midnight. Inexact, so no exact-alarm permission: Android stretches the window to at least
     * 10 minutes for apps targeting 12+, and Doze can hold it until the phone wakes. It doesn't wake the phone.
     */
    private fun arm() {
        val at = System.currentTimeMillis() + untilMidnight() + MIDNIGHT_SLACK_MS
        alarms()?.setWindow(AlarmManager.RTC, at, WINDOW_MS, midnightIntent())
    }

    private fun alarms(): AlarmManager? = context.getSystemService(AlarmManager::class.java)

    private fun midnightIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, StreakWidgetMidnightReceiver::class.java).setAction(StreakWidgetMidnightReceiver.ACTION_MIDNIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setReceiverEnabled(on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        for (receiver in listOf(StreakWidgetMidnightReceiver::class.java, StreakWidgetRefreshReceiver::class.java)) {
            val name = ComponentName(context, receiver)
            if (context.packageManager.getComponentEnabledSetting(name) != state) {
                context.packageManager.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private companion object {
        const val REQUEST = 103

        /** So the new day is certainly "today" when it fires. */
        const val MIDNIGHT_SLACK_MS = 5_000L

        /** The least window Android gives an inexact alarm from Android 12 (a shorter one is stretched to it). */
        const val WINDOW_MS = 10 * 60_000L
    }
}

/** The midnight alarm (#103): the app's own explicit, immutable intent, so this receiver isn't exported. */
class StreakWidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MIDNIGHT) refreshAsync(context)
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.brewingbytes.gemmory.action.STREAK_WIDGET_MIDNIGHT"
    }
}

/**
 * Brings a placed widget to the new day after a clock or time-zone change, a reboot (which clears the alarm) or an app
 * update (#103). Exported for those system broadcasts only. Enabled only while a widget is placed.
 */
class StreakWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> refreshAsync(context)
        }
    }
}

/** Under the 10 s a `goAsync` receiver may hold before Android calls it an ANR. */
private const val RUN_BUDGET_MS = 9_500L

private fun BroadcastReceiver.refreshAsync(context: Context) {
    val app = context.applicationContext as GemmoryApp
    val pending = goAsync()
    app.appScope.launch {
        try {
            withTimeoutOrNull(RUN_BUDGET_MS) { app.streakWidgets.refresh(app.streakView) }
        } finally {
            pending.finish()
        }
    }
}
