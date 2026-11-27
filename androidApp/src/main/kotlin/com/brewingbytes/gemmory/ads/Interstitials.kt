package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.util.Log
import com.brewingbytes.gemmory.policy.AdMoment
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.InterstitialContext
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.SpotRules
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeState
import kotlinx.coroutines.CompletableDeferred

/** What leaving a fresh completion did (1.0.7). */
enum class ReturnAd {
    /** Nothing: a rule blocked it, or no ad was loaded. */
    NONE,

    /** Today's interstitial showed (the fallback when no rewarded interstitial is loaded). */
    INTERSTITIAL,

    /** The rewarded interstitial is ready: the shell shows its intro sheet ([introShown], [playIntro]). */
    INTRO,
}

/**
 * The capped interstitial (design doc Ad rules R3-9): checked only after leaving the completion screen of a picture
 * finished just now, never on the play canvas and never before or during Share. Preloaded on that completion screen
 * when the caps would allow it, so leaving shows it at once; a no-fill is skipped silently. From 1.0.7 (ad-spots.md) the
 * rewarded interstitial takes its place when loaded, under the same caps, and a completion bonus watched on that screen
 * blocks both formats on its exit (R2-1). Unity doesn't bid on rewarded interstitials, so today's interstitial is the
 * fallback, keeping fill.
 */
class Interstitials(
    private val monetization: Monetization,
    private val gateway: AdGateway,
    private val encodeState: (CanvasRef) -> EncodeState,
    private val analytics: Analytics = Analytics.None,
    private val bonusWatched: (CanvasRef) -> Boolean = { false },
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    private fun context(sharing: Boolean, bonus: Boolean = false) =
        InterstitialContext(AdMoment.LEAVE_FRESH_COMPLETION, monetization.entitlements.value, sharing, bonus)

    /** The lights-down completion is on screen: preload only if leaving it now could show an ad. No consent form here. */
    suspend fun onFreshCompletion() {
        if (monetization.interstitialBlock(context(sharing = false)) != null) return
        gateway.start()
        gateway.preloadInterstitial()
        gateway.want(AdSlot.REWARDED_INTERSTITIAL)
    }

    /** Back on the gallery from that completion screen. */
    suspend fun onLeftFreshCompletion(activity: Activity, ref: CanvasRef): ReturnAd {
        gateway.unwant(AdSlot.REWARDED_INTERSTITIAL)
        // Still encoding, or a video or picture waiting for the share sheet: the ad would land during Share.
        val sharing = when (encodeState(ref)) {
            is EncodeState.Running, is EncodeState.Video, is EncodeState.Picture -> true
            else -> false
        }
        val block = monetization.interstitialBlock(context(sharing, bonusWatched(ref)))
        if (block != null) {
            log("interstitial blocked: $block")
            return ReturnAd.NONE
        }
        if (AdSlot.REWARDED_INTERSTITIAL in gateway.slotsReady.value) return ReturnAd.INTRO
        val shown = gateway.showInterstitial(activity) {
            monetization.onInterstitialShown()
            analytics.log(AnalyticsEvent.interstitialShown)
        }
        log(if (shown) "interstitial shown" else "interstitial allowed, none loaded")
        return if (shown) ReturnAd.INTERSTITIAL else ReturnAd.NONE
    }

    /** Left a fresh completion with no interstitial check (another interruption took the return): release its state. */
    fun leftWithoutAd(ref: CanvasRef) {
        gateway.unwant(AdSlot.REWARDED_INTERSTITIAL)
        bonusWatched(ref)
    }

    /** The intro is on screen: it counts toward the gap and the daily cap whether skipped or watched. */
    fun introShown() {
        monetization.onInterstitialShown()
        analytics.log(AnalyticsEvent.rewardedIntroShown)
    }

    /**
     * Watch now, or the countdown ran out: the video plays now. Its reward is 1 fill (DR-D17); null when it didn't
     * show, or closed before its reward. A shown video starts the rewarded quiet.
     */
    suspend fun playIntro(activity: Activity): Spin? {
        analytics.log(AnalyticsEvent.rewardedIntroStarted)
        val done = CompletableDeferred<Boolean>()
        var earned = false
        val shown = gateway.showSlot(activity, AdSlot.REWARDED_INTERSTITIAL, onEarned = { earned = true }) { shown ->
            if (shown) monetization.onRewardedShown()
            done.complete(shown && earned)
        }
        if (!shown || !done.await()) return null
        monetization.earnBonusFills()
        analytics.log(AnalyticsEvent.rewardedIntroWatched)
        val reward = SpotRules.BONUS_FILLS
        analytics.log(AnalyticsEvent.inventoryEarned(reward.item, EarnSource.VIDEO, reward.amount))
        return reward
    }

    private companion object {
        const val TAG = "GemmoryAds"
    }
}
