package com.brewingbytes.gemmory.reminder

import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.policy.ReminderSchedule

/**
 * The evening reminder's settings and ask state (T35), in the "reminder" preferences: on/off, the time, when the
 * pending alarm is due, the fresh finishes counted since T35 arrived and where the ask stands. A time and a few
 * flags, no user content, so they may be backed up with the other settings; a restored phone re-arms the alarm at its
 * first app start.
 */
class ReminderPrefs(private val prefs: SharedPreferences) {
    var on: Boolean
        get() = prefs.getBoolean(ON, false)
        set(value) = prefs.edit { putBoolean(ON, value) }

    /** Minutes since local midnight, one of [ReminderSchedule.CHOICES]. */
    var minutes: Int
        get() = ReminderSchedule.valid(prefs.getInt(MINUTES, ReminderSchedule.DEFAULT))
        set(value) = prefs.edit { putInt(MINUTES, ReminderSchedule.valid(value)) }

    /** The wall time the pending alarm was set for, or 0 when none is. */
    var scheduledAt: Long
        get() = prefs.getLong(SCHEDULED_AT, 0L)
        set(value) = prefs.edit { putLong(SCHEDULED_AT, value) }

    var finishes: Int
        get() = prefs.getInt(FINISHES, 0)
        set(value) = prefs.edit { putInt(FINISHES, value) }

    var ask: ReminderAskState
        get() = ReminderAskState.entries.firstOrNull { it.name == prefs.getString(ASK, null) } ?: ReminderAskState.NOT_ASKED
        set(value) = prefs.edit { putString(ASK, value.name) }

    /** Android 13+'s permission dialog was shown once, so a later "no" from it means it won't show again. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(PERMISSION_ASKED, false)
        set(value) = prefs.edit { putBoolean(PERMISSION_ASKED, value) }

    /** The gallery's one "Reminders on" line (plan-design-review D12) is waiting to show. */
    var onLinePending: Boolean
        get() = prefs.getBoolean(ON_LINE_PENDING, false)
        set(value) = prefs.edit { putBoolean(ON_LINE_PENDING, value) }

    companion object {
        const val FILE = "reminder"
        private const val ON = "on"
        private const val MINUTES = "minutes"
        private const val SCHEDULED_AT = "scheduled_at"
        private const val FINISHES = "finishes"
        private const val ASK = "ask"
        private const val PERMISSION_ASKED = "permission_asked"
        private const val ON_LINE_PENDING = "on_line_pending"
    }
}
