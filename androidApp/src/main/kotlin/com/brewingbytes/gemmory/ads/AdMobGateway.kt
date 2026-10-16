package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.RewardedRules
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Ad unit IDs. Google's test units only, until production IDs are set up (#7 scope; the app ID is in the manifest). */
object AdUnits {
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
}

/** How a rewarded load ended. */
sealed interface RewardedLoad {
    data object Loaded : RewardedLoad

    data class Failed(val failure: LoadFailure) : RewardedLoad
}

/** The ad SDK as the app uses it, so the sheet and the interstitial check can run against a fake. Main thread. */
interface AdGateway {
    /** MobileAds finished initializing (after consent). Part of the grace rule (eng D15). */
    val sdkReady: Boolean

    /** The device has a validated network connection, not just a network (eng D15). */
    fun online(): Boolean

    /**
     * Before the player's first ad: the UMP form when consent is still needed (ConsentManager.ensureBeforeAd). True
     * when ads may be requested.
     */
    suspend fun ensureConsent(activity: Activity): Boolean

    /** Initializes the SDK once consent allows ad requests; a no-op before that. */
    suspend fun start()

    /** Starts loading a rewarded video if none is loaded or loading (ads are preloaded, design doc Ad rules). */
    fun preloadRewarded()

    /** Waits for the rewarded video, at most [RewardedRules.LOAD_TIMEOUT_MS] ("Loading video…"). */
    suspend fun loadRewarded(): RewardedLoad

    /**
     * Shows the loaded video. [onEarned] runs when the reward callback fires; [onClosed] once the ad is gone, with
     * `shown = false` when it failed to show (the player saw no video). False when there is no loaded video.
     */
    fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean

    fun preloadInterstitial()

    /** Shows a loaded interstitial; false (skipped silently) when none is loaded, e.g. no fill. */
    fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean
}

/**
 * AdMob (design doc Ad rules, R3-10; eng D15). Never initializes or requests anything before UMP allows ad requests.
 * Not child-directed and not under the age of consent: the app is for adults only (AGENTS.md).
 */
class AdMobGateway(context: Context, private val consent: ConsentManager, private val scope: CoroutineScope) : AdGateway {
    private val app = context.applicationContext

    override var sdkReady = false
        private set
    private var starting: CompletableDeferred<Unit>? = null

    private var rewarded: RewardedAd? = null
    private var rewardedAt = 0L
    private var rewardedLoading: CompletableDeferred<RewardedLoad>? = null

    private var interstitial: InterstitialAd? = null
    private var interstitialAt = 0L
    private var interstitialLoading = false

    override fun online(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override suspend fun ensureConsent(activity: Activity): Boolean = consent.ensureBeforeAd(activity)

    override suspend fun start() {
        if (sdkReady || !consent.canRequestAds.value) return
        val pending = starting ?: CompletableDeferred<Unit>().also { done ->
            starting = done
            // Adults only: no child or teen treatment (replaces the deprecated child-directed / under-age tags).
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setAgeRestrictedTreatment(AgeRestrictedTreatment.UNSPECIFIED).build(),
            )
            // Google recommends initializing off the main thread.
            scope.launch(Dispatchers.IO) {
                MobileAds.initialize(app) {
                    scope.launch(Dispatchers.Main) {
                        sdkReady = true
                        Log.i(TAG, "MobileAds initialized")
                        done.complete(Unit)
                    }
                }
            }
        }
        pending.await()
    }

    private fun canLoad(): Boolean = sdkReady && consent.canRequestAds.value

    override fun preloadRewarded() {
        if (!canLoad() || freshRewarded() != null || rewardedLoading != null) return
        val result = CompletableDeferred<RewardedLoad>()
        rewardedLoading = result
        RewardedAd.load(app, AdUnits.REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewarded = ad
                rewardedAt = SystemClock.elapsedRealtime()
                rewardedLoading = null
                result.complete(RewardedLoad.Loaded)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.i(TAG, "rewarded failed: ${error.code} ${error.message}")
                rewardedLoading = null
                result.complete(RewardedLoad.Failed(failureOf(error.code)))
            }
        })
    }

    override suspend fun loadRewarded(): RewardedLoad = withContext(Dispatchers.Main) {
        if (freshRewarded() != null) return@withContext RewardedLoad.Loaded
        if (!canLoad()) return@withContext RewardedLoad.Failed(LoadFailure.OTHER)
        preloadRewarded()
        val loading = rewardedLoading ?: return@withContext RewardedLoad.Failed(LoadFailure.OTHER)
        // A late video still lands in the cache for the next try.
        withTimeoutOrNull(RewardedRules.LOAD_TIMEOUT_MS) { loading.await() } ?: RewardedLoad.Failed(LoadFailure.TIMEOUT)
    }

    override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
        val ad = freshRewarded() ?: return false
        rewarded = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = onClosed(true)

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "rewarded failed to show: ${error.code} ${error.message}")
                onClosed(false)
            }
        }
        ad.show(activity) { onEarned() }
        return true
    }

    override fun preloadInterstitial() {
        if (!canLoad() || freshInterstitial() != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(app, AdUnits.INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                interstitialAt = SystemClock.elapsedRealtime()
                interstitialLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.i(TAG, "interstitial failed: ${error.code} ${error.message}")
                interstitialLoading = false
            }
        })
    }

    override fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean {
        val ad = freshInterstitial() ?: return false
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = onShown()
        }
        ad.show(activity)
        return true
    }

    /** Loaded ads expire after an hour; one older than [MAX_AGE_MS] is dropped rather than shown. */
    private fun freshRewarded(): RewardedAd? =
        rewarded?.takeIf { SystemClock.elapsedRealtime() - rewardedAt < MAX_AGE_MS } ?: run { rewarded = null; null }

    private fun freshInterstitial(): InterstitialAd? =
        interstitial?.takeIf { SystemClock.elapsedRealtime() - interstitialAt < MAX_AGE_MS } ?: run { interstitial = null; null }

    private companion object {
        const val TAG = "GemmoryAds"
        const val MAX_AGE_MS = 55 * 60_000L

        fun failureOf(code: Int): LoadFailure = when (code) {
            AdRequest.ERROR_CODE_NO_FILL, AdRequest.ERROR_CODE_MEDIATION_NO_FILL -> LoadFailure.NO_FILL
            AdRequest.ERROR_CODE_NETWORK_ERROR -> LoadFailure.NETWORK
            else -> LoadFailure.OTHER
        }
    }
}
