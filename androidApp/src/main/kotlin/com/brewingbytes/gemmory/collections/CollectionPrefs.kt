package com.brewingbytes.gemmory.collections

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Completed collections (T37), in the "collections" preferences: each one's local completion day (its row stays 7
 * days), whether the player had VIP then (VIP earns the picture only, so a later lapse gives no token), and whether
 * its completion glint has played. Ids and days, no user content, so it's backed up like the unlocks.
 */
class CollectionPrefs(private val prefs: SharedPreferences) {
    /** The completion of [id], or null while it isn't complete. */
    fun completion(id: String): Completion? {
        val value = prefs.getString(DONE + id, null) ?: return null
        val day = value.substringBefore(':').toLongOrNull() ?: return null
        return Completion(day, value.substringAfter(':', "") == "vip")
    }

    /** Every completion recorded, by collection id. */
    fun completions(): Map<String, Completion> =
        prefs.all.keys.filter { it.startsWith(DONE) }.mapNotNull { key -> key.removePrefix(DONE).let { id -> completion(id)?.let { id to it } } }.toMap()

    /** Written at once (commit), before the keepsake or token, so a crash after it resumes rather than repeats. */
    fun markComplete(id: String, day: Long, vip: Boolean) {
        prefs.edit(commit = true) { putString(DONE + id, "$day:${if (vip) "vip" else "free"}") }
    }

    fun glintShown(id: String): Boolean = prefs.getBoolean(GLINT + id, false)

    fun setGlintShown(id: String) = prefs.edit { putBoolean(GLINT + id, true) }

    data class Completion(val day: Long, val vip: Boolean)

    companion object {
        /** Backed up (BackupRulesTest). */
        const val FILE = "collections"
        private const val DONE = "done:"
        private const val GLINT = "glint:"
    }
}
