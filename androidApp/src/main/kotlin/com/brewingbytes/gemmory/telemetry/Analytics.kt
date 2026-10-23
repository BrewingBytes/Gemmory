package com.brewingbytes.gemmory.telemetry

import android.util.Log
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.policy.ConsentModeV2
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.ReminderReason
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * One Firebase Analytics event (design doc → Analytics). Params are kinds, sizes and counts only: no titles, ids or
 * photo data. The one exception is content ids (T37/T38): a catalog collection's or weekly event's id
 * (`collection_id`, `event_id`), which names our own published content, never the player or their photos. Values are strings, or numbers (Long) where Analytics
 * should sum or average them.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    companion object {
        /**
         * [colors]: a photo canvas's color choice on Preview (#29); pictures have none. [entry]: where it was opened
         * from (T36); [daysSinceAvailable]: local days since a dated drop's date, 0 on the day (T36); [collectionId]:
         * the newest catalog collection listing the picture (T37), a content id; [eventId]: the newest weekly event
         * listing it (T38), a content id; [mystery]: a mystery picture (T43), `mystery=true`; the param is absent otherwise.
         */
        fun canvasStarted(
            ref: CanvasRef,
            width: Int,
            height: Int,
            colors: ColorChoice? = null,
            entry: CanvasEntry = CanvasEntry.GALLERY,
            daysSinceAvailable: Long? = null,
            collectionId: String? = null,
            eventId: String? = null,
            mystery: Boolean = false,
        ) = AnalyticsEvent(
            "canvas_started",
            canvas(ref, width, height) + colors(colors) + opened(entry, daysSinceAvailable) + collection(collectionId) + event(eventId) +
                (if (mystery) mapOf(MYSTERY to "true") else emptyMap()),
        )

        fun canvasFinished(
            ref: CanvasRef,
            width: Int,
            height: Int,
            entry: CanvasEntry = CanvasEntry.GALLERY,
            daysSinceAvailable: Long? = null,
            collectionId: String? = null,
            eventId: String? = null,
        ) = AnalyticsEvent("canvas_finished", canvas(ref, width, height) + opened(entry, daysSinceAvailable) + collection(collectionId) + event(eventId))

        /** Every member of a collection is finished (T37): its keepsake and token are earned. A content id. */
        fun collectionCompleted(collectionId: String) = AnalyticsEvent("collection_completed", mapOf(COLLECTION_ID to collectionId))

        /**
         * A weekly event's keepsake was earned (T38): [finishedInWindow] of its members first finished inside its
         * window (3 or more). A content id.
         */
        fun eventRewardUnlocked(eventId: String, finishedInWindow: Int) =
            AnalyticsEvent("event_reward_unlocked", mapOf(EVENT_ID to eventId, "finished_in_window" to finishedInWindow.toLong()))

        /** The first gem placed on a local day, on any canvas (T36, eng D13): one a day, no ids. */
        fun dayPlayed(entry: CanvasEntry) = AnalyticsEvent("day_played", mapOf(ENTRY to entry.param))

        /**
         * A fresh install's first launch (T46, CEO O5), logged once consent has settled: the launch funnel's denominator,
         * not Firebase's own `first_open` (which a restored phone fires again).
         */
        val firstLaunch = AnalyticsEvent("first_launch")

        /**
         * A fresh install's first gem (T46), once: [msSinceFirstLaunch] since its first launch came to the screen, the
         * canvas's [source][source] and where it was opened from ([entry]). No ids.
         */
        fun firstGem(msSinceFirstLaunch: Long, ref: CanvasRef, entry: CanvasEntry) = AnalyticsEvent(
            "first_gem",
            mapOf("ms_since_first_launch" to msSinceFirstLaunch, SOURCE to source(ref), ENTRY to entry.param),
        )

        /** The Tomorrow line on the Today card was tapped (T36): "Opens tomorrow", nothing opens. */
        val teaserTapped = AnalyticsEvent("teaser_tapped")

        /** The evening reminder's ask sheet showed (T35): [attempt] 1 after the first finish, 2 after the third. */
        fun reminderPromptShown(attempt: Int) = AnalyticsEvent("reminder_prompt_shown", mapOf("attempt" to attempt.toLong()))

        /** How the ask ended (T35): `granted` | `declined_in_app` | `denied_system`. */
        fun reminderPromptResult(result: String) = AnalyticsEvent("reminder_prompt_result", mapOf("result" to result))

        /** Reminders turned on or off, or their time changed, in Settings or the ask (T35). [minutes]: since midnight. */
        fun reminderSettingsChanged(on: Boolean, minutes: Int) =
            AnalyticsEvent("reminder_settings_changed", mapOf("on" to on.toString(), "minutes" to minutes.toLong()))

        /** A reminder was posted (T35), logged from the background job under the saved consent (eng D6). */
        fun reminderShown(reason: ReminderReason) = AnalyticsEvent("reminder_shown", mapOf("reason" to reason.param))

        /** The evening's run came more than 2 h late or on a later date (T35), so nothing was posted. */
        fun reminderLate(minutes: Long) = AnalyticsEvent("reminder_late", mapOf("minutes" to minutes))

        /** A reminder was tapped (T35). */
        fun reminderOpened(reason: ReminderReason) = AnalyticsEvent("reminder_opened", mapOf("reason" to reason.param))

        /** The in-app review prompt asked Play for its sheet (T39): [attempt] 1-3. Play never says whether it showed. */
        fun reviewRequested(attempt: Int) = AnalyticsEvent("review_requested", mapOf("attempt" to attempt.toLong()))

        fun photoCanvasStarted(colors: ColorChoice) = AnalyticsEvent("photo_canvas_started", colors(colors))

        /** [progress]: shared before the last gem (#36), from the canvas or the gallery tile. */
        fun shareTapped(ref: CanvasRef, width: Int, height: Int, progress: Boolean) =
            AnalyticsEvent("share_tapped", canvas(ref, width, height) + (KIND to kind(progress)))

        /** The player picked an app in the share sheet. [kind]: `progress` or `finished`. */
        fun shareCompleted(source: String, size: String, kind: String) =
            AnalyticsEvent("share_completed", mapOf(SOURCE to source, SIZE to size, KIND to kind))

        /** The first picture finished on a new day (#33): the daily play streak is now [length] days. */
        fun streakDay(length: Int) = AnalyticsEvent("streak_day", mapOf("length" to length.toLong()))

        /**
         * The new streak day's celebration showed (#101): [length] days, [milestone] (7, 30, 50, 100 or 365; 0 for none)
         * and what kept it across a missed day ([savedBy]: `finish`, `day_off`, `vip_freeze`, `freeze`, or several
         * joined by `+`).
         */
        fun streakCelebrationShown(length: Int, milestone: Int, savedBy: String) = AnalyticsEvent(
            "streak_celebration_shown",
            mapOf("length" to length.toLong(), "milestone" to milestone.toLong(), "saved_by" to savedBy),
        )

        /**
         * The gift wheel landed on the streak freeze (#102, E7): [paid] is `freeze`, or `fills` when 2 were already held
         * (it pays 5 fills instead).
         */
        fun streakFreezeEarned(paid: String) = AnalyticsEvent("streak_freeze_earned", mapOf("paid" to paid))

        /** A new streak day spent [count] streak freezes on the missed days before it (#102, E5). */
        fun streakFreezeUsed(count: Int) = AnalyticsEvent("streak_freeze_used", mapOf("count" to count.toLong()))

        /** The streak screen showed a repair for a broken streak of [length] days (#102), once per opening. */
        fun streakRepairOffered(length: Int) = AnalyticsEvent("streak_repair_offered", mapOf("length" to length.toLong()))

        /** A broken streak was brought back (#102); [vip]: VIP's weekly free repair, else a video or the grace. */
        fun streakRepairCompleted(vip: Boolean) = AnalyticsEvent("streak_repair_completed", mapOf("vip" to if (vip) 1L else 0L))

        /** The gallery's keep-it-alive nudge showed (#102), once a day; [broken]: the repair variant. */
        fun streakNudgeShown(broken: Boolean) = AnalyticsEvent("streak_nudge_shown", mapOf("broken" to if (broken) 1L else 0L))

        /** The nudge was tapped (#102): [target] is `today`, `canvas`, `quick`, `repair` (the broken variant) or `none`. */
        fun streakNudgeTapped(target: String) = AnalyticsEvent("streak_nudge_tapped", mapOf("target" to target))

        /** The player picked an app in the share sheet for the [milestone] streak card (7, 30, 50, 100 or 365; #104). */
        fun streakShareCompleted(milestone: Int) = AnalyticsEvent("streak_share_completed", mapOf("milestone" to milestone.toLong()))

        /** The first home-screen streak widget was placed (#103). */
        fun streakWidgetAdded() = AnalyticsEvent("streak_widget_added", emptyMap())

        /**
         * The streak screen opened (#101), from [source]: `chip`, `celebration`, `celebration_repair`, `nudge` (#102) or
         * `widget` (#103).
         */
        fun streakScreenOpened(source: String) = AnalyticsEvent("streak_screen_opened", mapOf(SOURCE to source))

        /** The first gem on today's picture (#34). [vip]: it was VIP's "Today's pick", not a free grant. */
        fun todaysPictureStarted(ref: CanvasRef, width: Int, height: Int, vip: Boolean) =
            AnalyticsEvent("todays_picture_started", mapOf(SIZE to size(ref, width, height), "tier" to if (vip) "vip" else "free"))

        /**
         * A placement tool (#20) placed gems on this canvas for the first time since it opened. [tool]: `brush`,
         * `region` or `color`; [tier] is `vip` (the only tier with tools until #38 adds earned charges).
         */
        fun fillToolUsed(ref: CanvasRef, width: Int, height: Int, tool: String, tier: String) =
            AnalyticsEvent("fill_tool_used", mapOf("tool" to tool, SIZE to size(ref, width, height), "tier" to tier))

        /** An inventory item was earned (#38): [amount] of [item] from [source] (video, grace or the streak wheel). */
        fun inventoryEarned(item: Item, source: EarnSource, amount: Int) = AnalyticsEvent(
            "inventory_earned",
            mapOf("item" to item.param, "source" to source.param, "amount" to amount.toLong()),
        )

        /** One inventory item was spent (#38). */
        fun inventorySpent(item: Item) = AnalyticsEvent("inventory_spent", mapOf("item" to item.param))

        fun rewardedOffered(type: RewardType) = AnalyticsEvent("rewarded_offered", reward(type))

        fun rewardedWatched(type: RewardType) = AnalyticsEvent("rewarded_watched", reward(type))

        fun rewardedUnavailable(type: RewardType, reason: UnavailableReason) =
            AnalyticsEvent("rewarded_unavailable", reward(type) + ("reason" to reason.param))

        fun graceUnlockGranted(type: RewardType) = AnalyticsEvent("grace_unlock_granted", reward(type))

        val interstitialShown = AnalyticsEvent("interstitial_shown")

        /** [period]: `weekly` or `yearly`. */
        fun vipPurchased(period: String) = AnalyticsEvent("vip_purchased", mapOf("period" to period))

        val removeAdsPurchased = AnalyticsEvent("remove_ads_purchased")

        /**
         * A time-lapse was encoded (not a cached re-share): how long it took on this phone and how it ended (eng
         * finding 16). [outcome] as in EncodeManager.outcomeName.
         */
        fun timelapseMade(ref: CanvasRef, width: Int, height: Int, outcome: String, wallMs: Long) =
            AnalyticsEvent("timelapse_made", canvas(ref, width, height) + mapOf("outcome" to outcome, "wall_ms" to wallMs))

        const val SOURCE = "source"
        const val SIZE = "size"
        const val KIND = "kind"
        const val ENTRY = "entry"
        const val DAYS_SINCE_AVAILABLE = "days_since_available"
        const val COLLECTION_ID = "collection_id"
        const val EVENT_ID = "event_id"
        const val MYSTERY = "mystery"

        /** The share's kind (#36): `progress` before the last gem, `finished` after it. */
        fun kind(progress: Boolean): String = if (progress) "progress" else "finished"

        fun source(ref: CanvasRef): String = when (ref.kind) {
            CanvasKind.PHOTO -> "photo"
            CanvasKind.BUNDLED -> "bundled"
            CanvasKind.REMOTE -> "remote"
        }

        /**
         * `starter` for the starter picture (never Quick, eng re-check), else the picker size whose grid this is
         * (`quick`, `medium`, `big`), else `other`.
         */
        fun size(ref: CanvasRef, width: Int, height: Int): String = when {
            ref.kind == CanvasKind.BUNDLED && ref.id == STARTER_ID -> "starter"
            else -> GridSpec.PICKER.firstOrNull { it.width == width && it.height == height }?.id?.substringBefore('-') ?: "other"
        }

        private const val STARTER_ID = "starter"

        private fun canvas(ref: CanvasRef, width: Int, height: Int) = mapOf(SOURCE to source(ref), SIZE to size(ref, width, height))

        private fun opened(entry: CanvasEntry, daysSinceAvailable: Long?): Map<String, Any> =
            mapOf<String, Any>(ENTRY to entry.param) + daysSinceAvailable?.let { mapOf(DAYS_SINCE_AVAILABLE to it) }.orEmpty()

        private fun collection(id: String?): Map<String, Any> = id?.let { mapOf(COLLECTION_ID to it) }.orEmpty()

        private fun event(id: String?): Map<String, Any> = id?.let { mapOf(EVENT_ID to it) }.orEmpty()

        private fun reward(type: RewardType) = mapOf("reward_type" to type.name.lowercase())

        /** `colors` = `fewer` | `standard` | `more` (#29): the choice, never the palette or the count. */
        private fun colors(choice: ColorChoice?): Map<String, Any> = choice?.let { mapOf("colors" to it.param) }.orEmpty()
    }
}

/**
 * Where a canvas was opened from (T36): the `entry` of `canvas_started`, `canvas_finished` and `day_played`. The
 * gallery's grid (and My Photos, and Preview's Start) is [GALLERY], the Today's picture card [TODAY_CARD]; the rest
 * come with the reminder (T35), collections (T37), the weekly event (T38), the streak nudge (#102) and a share link's
 * landing on a fresh install (T50).
 */
enum class CanvasEntry(val param: String) {
    GALLERY("gallery"),
    TODAY_CARD("today_card"),
    REMINDER("reminder"),
    COLLECTION("collection"),
    EVENT("event"),
    STREAK_NUDGE("streak_nudge"),
    SHARE_LINK("share_link"),
}

/** Why no rewarded video was there (design doc: no_fill | offline | timeout), plus `error` for anything else. */
enum class UnavailableReason(val param: String) {
    NO_FILL("no_fill"),
    OFFLINE("offline"),
    TIMEOUT("timeout"),
    ERROR("error"),
    ;

    companion object {
        fun of(failure: LoadFailure, online: Boolean): UnavailableReason = when {
            !online || failure == LoadFailure.NETWORK -> OFFLINE
            failure == LoadFailure.NO_FILL -> NO_FILL
            failure == LoadFailure.TIMEOUT -> TIMEOUT
            else -> ERROR
        }
    }
}

/** Firebase Analytics, or Logcat in debug builds and tests. */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** Consent Mode v2 from the UMP result (R3-7). Collection runs only while analytics storage is granted. */
    fun consent(mode: ConsentModeV2) = Unit

    companion object {
        val Logcat = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                Log.i("Gemmory", "analytics ${event.name} ${event.params}")
            }
        }

        val None = object : Analytics {
            override fun log(event: AnalyticsEvent) = Unit
        }

        fun forBuild(sendsToFirebase: Boolean, firebase: () -> Analytics): Analytics = if (sendsToFirebase) firebase() else Logcat

        /** Firebase's consent types for [mode], by their Consent Mode v2 names. */
        fun consentFlags(mode: ConsentModeV2): Map<String, Boolean> = mapOf(
            "analytics_storage" to mode.analyticsStorage,
            "ad_storage" to mode.adStorage,
            "ad_user_data" to mode.adUserData,
            "ad_personalization" to mode.adPersonalization,
        )
    }
}

/**
 * Analytics that drops every event until the first [consent] (T35, eng D6): a reminder, reboot or update can start the
 * app in the background and log at once, so nothing may reach Firebase before the saved consent does. The app
 * applies it first thing in onCreate; [applied] is what it last applied, for the connected test.
 */
class ConsentFirst(private val analytics: Analytics) : Analytics {
    @Volatile
    var applied: ConsentModeV2? = null
        private set

    override fun log(event: AnalyticsEvent) {
        if (applied != null) analytics.log(event)
    }

    override fun consent(mode: ConsentModeV2) {
        analytics.consent(mode)
        applied = mode
    }
}

/**
 * How a report names a canvas: a bundled picture by its id (`bundled/starter`), a photo canvas only by its kind,
 * since its id is the moment the player made it.
 */
val CanvasRef.reportLabel: String get() = when (kind) {
    CanvasKind.PHOTO -> "photo"
    CanvasKind.BUNDLED -> "bundled/$id"
    CanvasKind.REMOTE -> "remote/$id"
}
