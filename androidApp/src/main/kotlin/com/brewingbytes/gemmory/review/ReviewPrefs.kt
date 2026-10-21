package com.brewingbytes.gemmory.review

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The review prompt's counter (T39), in the "review" preferences: the attempts made, the local epoch day of the last
 * one and the player's first-finish day, stored once. Numbers, no user content, so they may be backed up: a restored phone doesn't start the three over.
 */
class ReviewPrefs(private val prefs: SharedPreferences) {
    var made: Int
        get() = prefs.getInt(MADE, 0)
        set(value) = prefs.edit { putInt(MADE, value) }

    var lastAttemptDay: Long?
        get() = if (prefs.contains(LAST_DAY)) prefs.getLong(LAST_DAY, 0) else null
        set(value) = prefs.edit { if (value == null) remove(LAST_DAY) else putLong(LAST_DAY, value) }

    /** [com.brewingbytes.gemmory.policy.ReviewPrompt.firstFinishDay], set once; null until anything is finished. */
    var firstFinishDay: Long?
        get() = if (prefs.contains(FIRST_DAY)) prefs.getLong(FIRST_DAY, 0) else null
        set(value) = prefs.edit { if (value == null) remove(FIRST_DAY) else putLong(FIRST_DAY, value) }

    companion object {
        const val FILE = "review"
        private const val MADE = "made"
        private const val LAST_DAY = "last_attempt_day"
        private const val FIRST_DAY = "first_finish_day"
    }
}
