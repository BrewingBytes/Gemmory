package com.brewingbytes.gemmory.telemetry

import android.util.Log
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.policy.ConsentModeV2
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * One Firebase Analytics event (design doc → Analytics). Params are kinds, sizes and counts only: no titles, ids or
 * photo data. Values are strings, or numbers (Long) where Analytics should sum or average them.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    companion object {
        fun canvasStarted(ref: CanvasRef, width: Int, height: Int) = AnalyticsEvent("canvas_started", canvas(ref, width, height))

        fun canvasFinished(ref: CanvasRef, width: Int, height: Int) = AnalyticsEvent("canvas_finished", canvas(ref, width, height))

        val photoCanvasStarted = AnalyticsEvent("photo_canvas_started")

        /** [progress]: shared before the last gem (#36), from the canvas or the gallery tile. */
        fun shareTapped(ref: CanvasRef, width: Int, height: Int, progress: Boolean) =
            AnalyticsEvent("share_tapped", canvas(ref, width, height) + (KIND to kind(progress)))

        /** The player picked an app in the share sheet. [kind]: `progress` or `finished`. */
        fun shareCompleted(source: String, size: String, kind: String) =
            AnalyticsEvent("share_completed", mapOf(SOURCE to source, SIZE to size, KIND to kind))

        /** The first gem of a new day (#33): the daily play streak is now [length] days. */
        fun streakDay(length: Int) = AnalyticsEvent("streak_day", mapOf("length" to length.toLong()))

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

        /** The share's kind (#36): `progress` before the last gem, `finished` after it. */
        fun kind(progress: Boolean): String = if (progress) "progress" else "finished"

        fun source(ref: CanvasRef): String = if (ref.kind == CanvasKind.PHOTO) "photo" else "bundled"

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

        private fun reward(type: RewardType) = mapOf("reward_type" to type.name.lowercase())
    }
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
 * How a report names a canvas: a bundled picture by its id (`bundled/starter`), a photo canvas only by its kind,
 * since its id is the moment the player made it.
 */
val CanvasRef.reportLabel: String get() = if (kind == CanvasKind.PHOTO) "photo" else "bundled/$id"
