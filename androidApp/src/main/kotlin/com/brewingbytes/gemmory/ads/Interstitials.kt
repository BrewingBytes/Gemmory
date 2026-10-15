package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.util.Log
import com.brewingbytes.gemmory.policy.AdMoment
import com.brewingbytes.gemmory.policy.InterstitialContext
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeState

/**
 * The capped interstitial (design doc Ad rules R3-9): checked only after leaving the completion screen of a picture
 * finished just now, never on the play canvas and never before or during Share. Preloaded on that completion screen
 * when the caps would allow it, so leaving shows it at once; a no-fill is skipped silently.
 */
class Interstitials(
    private val monetization: Monetization,
    private val gateway: AdGateway,
    private val encodeState: (CanvasRef) -> EncodeState,
    private val analytics: Analytics = Analytics.None,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    private fun context(sharing: Boolean) =
        InterstitialContext(AdMoment.LEAVE_FRESH_COMPLETION, monetization.entitlements.value, sharing)

    /** The lights-down completion is on screen: preload only if leaving it now could show an ad. No consent form here. */
    suspend fun onFreshCompletion() {
        if (monetization.interstitialBlock(context(sharing = false)) != null) return
        gateway.start()
        gateway.preloadInterstitial()
    }

    /** Back on the gallery from that completion screen. */
    suspend fun onLeftFreshCompletion(activity: Activity, ref: CanvasRef) {
        // Still encoding, or a video or picture waiting for the share sheet: the ad would land during Share.
        val sharing = when (encodeState(ref)) {
            is EncodeState.Running, is EncodeState.Video, is EncodeState.Picture -> true
            else -> false
        }
        val block = monetization.interstitialBlock(context(sharing))
        if (block != null) {
            log("interstitial blocked: $block")
            return
        }
        val shown = gateway.showInterstitial(activity) {
            monetization.onInterstitialShown()
            analytics.log(AnalyticsEvent.interstitialShown)
        }
        log(if (shown) "interstitial shown" else "interstitial allowed, none loaded")
    }

    private companion object {
        const val TAG = "GemmoryAds"
    }
}
