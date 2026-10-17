package com.brewingbytes.gemmory.bundled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.TodayState
import com.brewingbytes.gemmory.policy.TodaysPicture
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent

/**
 * Pictures a [TodaysPicture] may pick from, in catalog order (#34): for a free player the locked ones neither
 * unlocked nor started, for VIP every picture not started. Never the starter.
 */
internal fun todayCandidates(started: Set<String>, unlocked: Set<String>, vip: Boolean): List<String> =
    BundledPictures.ALL
        .filter { it.id != BundledPictures.STARTER.id && it.id !in started }
        .filter { vip || (it.locked && it.id !in unlocked) }
        .map { it.id }

/**
 * Today's picture (#34) on this phone. The day's pick lives in the "today" preferences (an id and a day, no user
 * content). A free player's pick opens without a video today, and its first gem unlocks it for good, in the same
 * unlocks a video writes (founder, 2026-10-17), so a Start over or a reinstall keeps it.
 */
class TodaysPictures(
    context: Context,
    clock: DayClock,
    /** Read on each use: a connected test replaces the app's monetization, and its unlocks with it. */
    private val unlocks: () -> PictureUnlocks,
    private val analytics: Analytics,
    /** False only in connected tests that assert which pictures are locked, so no daily pick moves them. */
    private val enabled: Boolean = true,
) {
    private val today = TodaysPicture(clock, PrefsStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)))

    /** Today's picture id, picking it on the day's first call. [started]: bundled pictures with a gem placed. */
    @Synchronized
    fun pick(started: Set<String>, vip: Boolean): String? =
        if (enabled) today.pick(todayCandidates(started, unlocks().unlocked.value, vip), vip) else null

    /** Whether [ref] is today's free picture right now: it opens without a video. False once the day is over. */
    @Synchronized
    fun isFreeToday(ref: CanvasRef): Boolean = isToday(ref, vip = false)

    /** Whether [ref] is today's picture (or pick) for this tier right now. */
    @Synchronized
    fun isToday(ref: CanvasRef, vip: Boolean): Boolean = enabled && ref.kind == CanvasKind.BUNDLED && today.picked(vip) == ref.id

    /** The first gem on today's picture: a free player's grant becomes a permanent unlock, and Analytics hears of it. */
    fun started(ref: CanvasRef, width: Int, height: Int, vip: Boolean) {
        if (!isToday(ref, vip)) return
        if (!vip) unlocks().unlock(ref.id)
        analytics.log(AnalyticsEvent.todaysPictureStarted(ref, width, height, vip))
    }

    private class PrefsStore(private val prefs: SharedPreferences) : StateStore<TodayState> {
        override fun load(): TodayState? {
            if (!prefs.contains(DAY)) return null
            return TodayState(
                day = prefs.getLong(DAY, 0),
                pictureId = prefs.getString(PICTURE, null),
                vip = prefs.getBoolean(VIP, false),
                before = prefs.getString(BEFORE, null),
            )
        }

        override fun save(value: TodayState) = prefs.edit {
            putLong(DAY, value.day)
            putString(PICTURE, value.pictureId)
            putBoolean(VIP, value.vip)
            putString(BEFORE, value.before)
        }
    }

    companion object {
        internal const val FILE = "today"
        private const val DAY = "day"
        private const val PICTURE = "picture"
        private const val VIP = "vip"
        private const val BEFORE = "before"
    }
}
