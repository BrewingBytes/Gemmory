package com.brewingbytes.gemmory.ads

import android.app.Activity
import com.brewingbytes.gemmory.policy.LoadFailure

/**
 * The gateway while ads are off (#203, `gemmory.ads.enabled=false`): the ad SDK is never initialized and nothing is
 * requested, so no Unity write and no ad loads. Nothing offers a video then (rewards are free), and an interstitial is
 * always skipped. UMP still runs: it is also Firebase Analytics' consent (Consent Mode v2, #12).
 */
object NoAds : AdGateway {
    override val sdkReady: Boolean get() = false

    override fun online(): Boolean = false

    override suspend fun ensureConsent(activity: Activity): Boolean = false

    override suspend fun start() = Unit

    override fun preloadRewarded() = Unit

    override suspend fun loadRewarded(): RewardedLoad = RewardedLoad.Failed(LoadFailure.OTHER)

    override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean = false

    override fun preloadInterstitial() = Unit

    override fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean = false
}
