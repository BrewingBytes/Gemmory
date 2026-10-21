package com.brewingbytes.gemmory.review

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The review prompt's counter (T39), in the "review" preferences: the attempts made and the local epoch day of the
 * last one. Two numbers, no user content, so they may be backed up: a restored phone doesn't start the three over.
 */
class ReviewPrefs(private val prefs: SharedPreferences) {
    var made: Int
        get() = prefs.getInt(MADE, 0)
        set(value) = prefs.edit { putInt(MADE, value) }

    var lastAttemptDay: Long?
        get() = if (prefs.contains(LAST_DAY)) prefs.getLong(LAST_DAY, 0) else null
        set(value) = prefs.edit { if (value == null) remove(LAST_DAY) else putLong(LAST_DAY, value) }

    companion object {
        const val FILE = "review"
        private const val MADE = "made"
        private const val LAST_DAY = "last_attempt_day"
    }
}
