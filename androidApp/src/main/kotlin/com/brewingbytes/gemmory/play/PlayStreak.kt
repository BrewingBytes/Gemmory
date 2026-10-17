package com.brewingbytes.gemmory.play

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.policy.DailyStreak
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.StreakState
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent

/**
 * The daily play streak (#33) on this phone. Kept in the "streak" preferences, which may be backed up: it's no photo
 * data, and a restore can't add days. Every 5th day earns a gift spin (#38) through [onDay]. Main thread only.
 */
class PlayStreak(
    context: Context,
    clock: DayClock,
    private val analytics: Analytics,
    /** A new streak day of this length (#38: every 5th earns a wheel spin). */
    private val onDay: (length: Int) -> Unit = {},
) {
    private val streak = DailyStreak(clock, PrefsStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)))

    /** A picture was finished: the first one of a new day extends the streak, and says so to Analytics. */
    fun played() {
        streak.played()?.let {
            analytics.log(AnalyticsEvent.streakDay(it))
            onDay(it)
        }
    }

    /** Days in a row as of today; 0 once broken. The gallery shows it from [DailyStreak.SHOWN_FROM]. */
    fun current(): Int = streak.current()

    private class PrefsStore(private val prefs: SharedPreferences) : StateStore<StreakState> {
        override fun load(): StreakState? {
            if (!prefs.contains(LAST_DAY)) return null
            return StreakState(
                length = prefs.getInt(LENGTH, 0),
                lastDay = prefs.getLong(LAST_DAY, StreakState.NEVER),
                restDay = prefs.getLong(REST_DAY, StreakState.NEVER),
            )
        }

        override fun save(value: StreakState) = prefs.edit {
            putInt(LENGTH, value.length)
            putLong(LAST_DAY, value.lastDay)
            putLong(REST_DAY, value.restDay)
        }
    }

    private companion object {
        const val FILE = "streak"
        const val LENGTH = "length"
        const val LAST_DAY = "lastDay"
        const val REST_DAY = "restDay"
    }
}
