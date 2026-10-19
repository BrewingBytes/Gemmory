package com.brewingbytes.gemmory.telemetry

import android.content.Context
import androidx.core.content.edit
import com.brewingbytes.gemmory.policy.StateStore

/** `day_played`'s preferences (T36): the local day the event was last logged, nothing else. */
const val PLAYED_FILE = "played"

/** A local day (epoch days) in its own preferences file, e.g. for [com.brewingbytes.gemmory.policy.DayPlayed]. */
class PrefsDayStore(context: Context, file: String) : StateStore<Long> {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    override fun load(): Long? = if (prefs.contains(DAY)) prefs.getLong(DAY, 0) else null

    override fun save(value: Long) = prefs.edit { putLong(DAY, value) }

    private companion object {
        const val DAY = "day"
    }
}
