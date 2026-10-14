package com.brewingbytes.gemmory.policy

/** Why a rewarded video didn't load. [TIMEOUT] is the 5 s "Loading video…" limit running out. */
enum class LoadFailure { NO_FILL, TIMEOUT, NETWORK, OTHER }

/** What the rewarded sheet shows when no video loaded (design system, Rewarded states). */
enum class Unavailable {
    /** "No video right now. This one's on us today." with the grace action. */
    GRACE_OFFER,

    /** "No video right now. Try again in a bit." */
    NO_VIDEO,

    /** "You're offline, connect to watch a video" (in `fault`). Never a grace (D15). */
    OFFLINE,
}

/** CEO D7 + eng D15: a grace unlock only when the network is real, the ad SDK is up and the network had nothing. */
object RewardedRules {
    /** How long the button may say "Loading video…" before the load counts as [LoadFailure.TIMEOUT]. */
    const val LOAD_TIMEOUT_MS = 5_000L

    /**
     * [online]: the device has a validated network. [sdkReady]: the ad SDK finished initializing (after consent).
     * [graceAvailable]: today's grace for this reward type is unused (DailyLedger).
     */
    fun unavailable(failure: LoadFailure, online: Boolean, sdkReady: Boolean, graceAvailable: Boolean): Unavailable = when {
        !online -> Unavailable.OFFLINE
        sdkReady && graceAvailable && (failure == LoadFailure.NO_FILL || failure == LoadFailure.TIMEOUT) ->
            Unavailable.GRACE_OFFER
        else -> Unavailable.NO_VIDEO
    }
}

/**
 * Consent Mode v2 signals for Firebase (#12; design doc R3-7), from the UMP result's IAB TCF strings. Outside GDPR
 * everything is granted. Inside it, purpose 1 (store/access) gates storage, purposes 1 + 7 ad user data, and
 * purposes 3 + 4 ad personalization, the mapping Google uses for TCF.
 */
data class ConsentModeV2(
    val analyticsStorage: Boolean,
    val adStorage: Boolean,
    val adUserData: Boolean,
    val adPersonalization: Boolean,
) {
    companion object {
        val GRANTED = ConsentModeV2(analyticsStorage = true, adStorage = true, adUserData = true, adPersonalization = true)

        /**
         * [gdprApplies]: `IABTCF_gdprApplies` (null when UMP hasn't decided). [purposeConsents]: `IABTCF_PurposeConsents`,
         * a "0"/"1" string where index 0 is purpose 1. Unknown GDPR status is treated as no consent.
         */
        fun fromTcf(gdprApplies: Boolean?, purposeConsents: String?): ConsentModeV2 {
            if (gdprApplies == false) return GRANTED
            fun purpose(n: Int) = purposeConsents?.getOrNull(n - 1) == '1'
            val storage = purpose(1)
            return ConsentModeV2(
                analyticsStorage = storage,
                adStorage = storage,
                adUserData = storage && purpose(7),
                adPersonalization = purpose(3) && purpose(4),
            )
        }
    }
}
