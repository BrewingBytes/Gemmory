package com.brewingbytes.gemmory.bundled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.TodayState
import com.brewingbytes.gemmory.policy.TodaysPicture
import com.brewingbytes.gemmory.remote.RemotePictureRules
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent

/** A picture Today's picture may offer: its id and catalog lock flag (bundled, or remote #43). */
data class TodayOption(val id: String, val locked: Boolean)

/**
 * Pictures a [TodaysPicture] may pick from, in catalog order (#34): for a free player the locked ones neither
 * unlocked nor started, for VIP every picture not started. Never the starter. When any of them is a remote picture
 * still marked "New" ([isNew]), only those (#43).
 */
internal fun todayCandidates(
    started: Set<String>,
    unlocked: Set<String>,
    vip: Boolean,
    pictures: List<TodayOption> = BundledPictures.ALL.map { TodayOption(it.id, it.locked) },
    isNew: (String) -> Boolean = { false },
): List<String> {
    val eligible = pictures
        .filter { it.id != BundledPictures.STARTER.id && it.id !in started }
        .filter { vip || (it.locked && it.id !in unlocked) }
        .map { it.id }
    return RemotePictureRules.todayCandidates(eligible, isNew)
}

/**
 * Today's picture (#34) on this phone. The day's pick lives in the "today" preferences (an id and a day, no user
 * content). A free player's pick opens without a video today, and its first gem unlocks it for good, in the same
 * unlocks a video writes (founder, 2026-10-17), so a Start over or a reinstall keeps it.
 */
class TodaysPictures(
    clock: DayClock,
    store: StateStore<TodayState>,
    /** Read on each use: a connected test replaces the app's monetization, and its unlocks with it. */
    private val unlocks: () -> PictureUnlocks,
    private val analytics: Analytics,
    /** False only in connected tests that assert which pictures are locked, so no daily pick moves them. */
    private val enabled: Boolean = true,
) {
    constructor(context: Context, clock: DayClock, unlocks: () -> PictureUnlocks, analytics: Analytics, enabled: Boolean = true) :
        this(clock, PrefsStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)), unlocks, analytics, enabled)

    private val today = TodaysPicture(clock, store)

    /**
     * Today's picture id, picking it on the day's first call. [started]: pictures with a gem placed; [pictures]: the
     * gallery's pictures in order; [isNew]: a remote picture's "New" mark (#43), preferred when one is eligible.
     */
    @Synchronized
    fun pick(
        started: Set<String>,
        vip: Boolean,
        pictures: List<TodayOption> = BundledPictures.ALL.map { TodayOption(it.id, it.locked) },
        isNew: (String) -> Boolean = { false },
    ): String? = if (enabled) today.pick(todayCandidates(started, unlocks().unlocked.value, vip, pictures, isNew), vip) else null

    /** Whether [ref] is today's free picture right now: it opens without a video. False once the day is over. */
    fun isFreeToday(ref: CanvasRef): Boolean = isToday(ref, vip = false)

    /** Whether [ref] is today's picture (or pick) for this tier right now. */
    @Synchronized
    fun isToday(ref: CanvasRef, vip: Boolean): Boolean = enabled && ref.kind != CanvasKind.PHOTO && today.picked(vip) == ref.id

    /**
     * A canvas opened with no gem placed: if it's today's picture now, the grant is decided here, so its first gem
     * still unlocks it when it lands after midnight. Null when it isn't today's.
     */
    fun opened(ref: CanvasRef, vip: Boolean): FirstGem? = if (isToday(ref, vip)) FirstGem(ref, vip) else null

    /** Today's picture, opened while it was today's. */
    inner class FirstGem internal constructor(private val ref: CanvasRef, private val vip: Boolean) {
        /** The first gem: a free player's grant becomes a permanent unlock, and Analytics hears of it. */
        fun placed(width: Int, height: Int) {
            if (!vip) unlocks().unlock(ref.id)
            analytics.log(AnalyticsEvent.todaysPictureStarted(ref, width, height, vip))
        }
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
