package com.brewingbytes.gemmory.bundled

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.TodayState
import com.brewingbytes.gemmory.policy.TodaysPicture
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.remote.RemotePictureRules
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.datetime.LocalDate

/** A picture Today's picture may offer: its id and catalog lock flag (bundled, or remote #43). */
data class TodayOption(val id: String, val locked: Boolean)

/**
 * Pictures a [TodaysPicture] may pick from, in catalog order (#34): for a free player the locked ones neither
 * unlocked nor started, for VIP every picture not started. Never the starter. When any of them is today's drop
 * ([isDrop], T36), only those; else when any is a remote picture still marked "New" ([isNew]), only those (#43).
 */
internal fun todayCandidates(
    started: Set<String>,
    unlocked: Set<String>,
    vip: Boolean,
    pictures: List<TodayOption> = BundledPictures.ALL.map { TodayOption(it.id, it.locked) },
    isDrop: (String) -> Boolean = { false },
    isNew: (String) -> Boolean = { false },
): List<String> {
    val eligible = pictures
        .filter { it.id != BundledPictures.STARTER.id && it.id !in started }
        .filter { vip || (it.locked && it.id !in unlocked) }
        .map { it.id }
    return RemotePictureRules.todayCandidates(eligible, isDrop, isNew)
}

/** What Today's pick reads from the pictures (eng D3): [started] ids, the [options] in order, and the "New" ids. */
data class TodayChoices(val started: Set<String>, val options: List<TodayOption>, val newIds: Set<String>)

/**
 * Today's choices, shared by the gallery's card and the evening reminder (T35, eng D3) so both name the same picture:
 * the bundled pictures with the [listed] remote ones merged in, in the gallery's order (#43), minus the [started]
 * ones. Neither caller renders anything for it. A dated drop ([drops]) is preferred on its own date only, never as a
 * "New" picture the days after, so it isn't free again through Today's picture (T36, eng D4).
 */
internal fun todayChoices(started: Set<String>, listed: List<RemotePicture>, drops: Set<String>, isNew: (String) -> Boolean): TodayChoices {
    class Option(val id: String, val size: CanvasSize?, val locked: Boolean, val addedAt: LocalDate?)
    val bundled = BundledPictures.ALL.map { Option(it.id, BundledPictures.canvasSizeOf(it.id), it.locked, null) }
    val remote = listed.map { Option(it.id, it.size, it.locked, it.addedAt) }
    val options = RemotePictureRules.merge(bundled, remote, { it.size }, { it.addedAt }, { it.id })
        .filter { it.size != null }
        .map { TodayOption(it.id, it.locked) }
    val newIds = listed.filter { it.id !in drops && isNew(it.id) }.map { it.id }.toSet()
    return TodayChoices(started, options, newIds)
}

/**
 * Today's picture (#34) on this phone. The day's pick lives in the "today" preferences (an id and a day, no user
 * content). A free player's pick opens without a video today, and its first gem unlocks it for good, in the same
 * unlocks a video writes (founder, 2026-10-17), so a Start over or a reinstall keeps it.
 *
 * Today's drop (T36, eng D9) has its own grant beside the pick: free to start on its date, unlocked by its first gem
 * the same way, while the pick never moves. [todaysDrops] is read on each use, so the grant ends at local midnight.
 */
class TodaysPictures(
    clock: DayClock,
    store: StateStore<TodayState>,
    /** Read on each use: a connected test replaces the app's monetization, and its unlocks with it. */
    private val unlocks: () -> PictureUnlocks,
    private val analytics: Analytics,
    /** False only in connected tests that assert which pictures are locked, so no daily pick moves them. */
    private val enabled: Boolean = true,
    /** Remote pictures whose drop date is today (T36). */
    private val todaysDrops: () -> Set<String> = { emptySet() },
) {
    constructor(
        context: Context,
        clock: DayClock,
        unlocks: () -> PictureUnlocks,
        analytics: Analytics,
        enabled: Boolean = true,
        todaysDrops: () -> Set<String> = { emptySet() },
    ) : this(clock, PrefsStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)), unlocks, analytics, enabled, todaysDrops)

    private val today = TodaysPicture(clock, store)

    /**
     * Today's picture id, picking it on the day's first call. [started]: pictures with a gem placed; [pictures]: the
     * gallery's pictures in order; [isNew]: a remote picture's "New" mark (#43), preferred when one is eligible, after
     * today's drop (T36).
     */
    @Synchronized
    fun pick(
        started: Set<String>,
        vip: Boolean,
        pictures: List<TodayOption> = BundledPictures.ALL.map { TodayOption(it.id, it.locked) },
        isNew: (String) -> Boolean = { false },
    ): String? {
        if (!enabled) return null
        val drops = todaysDrops()
        return today.pick(todayCandidates(started, unlocks().unlocked.value, vip, pictures, { it in drops }, isNew), vip)
    }

    /**
     * Today's picture id, picking it on the day's first call: the gallery's card and the evening reminder both come here
     * (T35, eng D3). [started]: pictures with a gem placed; [listed], [drops] and [isNew]: the remote pictures shown,
     * the dated drops among them and their "New" mark.
     */
    fun pick(started: Set<String>, vip: Boolean, listed: List<RemotePicture>, drops: Set<String>, isNew: (String) -> Boolean): String? {
        val choices = todayChoices(started, listed, drops, isNew)
        return pick(choices.started, vip, choices.options) { it in choices.newIds }
    }

    /**
     * Whether [ref] is free to start today without a video: today's picture, or today's drop (T36). False once the
     * day is over.
     */
    fun isFreeToday(ref: CanvasRef): Boolean = isToday(ref, vip = false) || isTodaysDrop(ref)

    /** The ids [isFreeToday] grants now, for the gallery's order and locked labels. */
    @Synchronized
    fun freeToday(): Set<String> = if (enabled) setOfNotNull(today.picked(vip = false)) + todaysDrops() else emptySet()

    /** Whether [ref] is today's picture (or pick) for this tier right now. */
    @Synchronized
    fun isToday(ref: CanvasRef, vip: Boolean): Boolean = enabled && ref.kind != CanvasKind.PHOTO && today.picked(vip) == ref.id

    /** Whether [ref] is a remote picture whose drop date is today (T36). */
    fun isTodaysDrop(ref: CanvasRef): Boolean = enabled && ref.kind == CanvasKind.REMOTE && ref.id in todaysDrops()

    /**
     * A canvas opened with no gem placed: if it's today's picture or today's drop now, the grant is decided here, so
     * its first gem still unlocks it when it lands after midnight. Null when it's neither.
     */
    fun opened(ref: CanvasRef, vip: Boolean): FirstGem? = if (isToday(ref, vip) || isTodaysDrop(ref)) FirstGem(ref, vip) else null

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
