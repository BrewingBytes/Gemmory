package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.brewingbytes.gemmory.BuildConfig
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
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ad unit IDs from the build (T34): production units in release builds (gradle.properties, checked by
 * `:shared:validateReleaseAdIds`), Google's test units in debug builds and connected tests. The app ID is in the manifest.
 */
object AdUnits {
    const val REWARDED = BuildConfig.ADMOB_REWARDED
    const val INTERSTITIAL = BuildConfig.ADMOB_INTERSTITIAL
}

/**
 * The 1.0.7 ad spots' own units (ad-spots CEO D2: revenue per spot by `ad_unit_name`, and archiving a unit in AdMob
 * turns its spot off with no release). [rewardedInterstitial]: the unit is a rewarded interstitial, not a rewarded ad.
 */
enum class AdSlot(val unit: String, val rewardedInterstitial: Boolean = false) {
    BONUS(BuildConfig.ADMOB_BONUS),
    GIFT(BuildConfig.ADMOB_GIFT),
    AGAIN(BuildConfig.ADMOB_AGAIN),
    REWARDED_INTERSTITIAL(BuildConfig.ADMOB_REWARDED_INTERSTITIAL, rewardedInterstitial = true),
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

    /** A full-screen ad is showing, from its show until it's dismissed or fails to show: the music pauses (T42). */
    val fullScreen: StateFlow<Boolean> get() = NO_FULL_SCREEN

    /** The 1.0.7 slots whose ad is loaded and fresh now (a spot shows only then, CEO D2). */
    val slotsReady: StateFlow<Set<AdSlot>> get() = NO_SLOTS

    /**
     * Keep [slot]'s ad loaded while its moment is on screen (eng D2): it loads now if ads may be requested, else as soon
     * as the SDK has started and consent allows (eng D5); it never shows the consent form. Reloaded after a show or
     * expiry while still wanted.
     */
    fun want(slot: AdSlot) = Unit

    fun unwant(slot: AdSlot) = Unit

    /**
     * Shows [slot]'s ready ad at once, never waiting for a load (eng D4). [onEarned] on the reward; [onClosed] once
     * it's gone, `shown = false` when it failed to show. False when none is ready.
     */
    fun showSlot(activity: Activity, slot: AdSlot, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean = false
}

private val NO_FULL_SCREEN: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
private val NO_SLOTS: StateFlow<Set<AdSlot>> = MutableStateFlow(emptySet<AdSlot>()).asStateFlow()

/**
 * AdMob (design doc Ad rules, R3-10; eng D15), with Unity Ads bidding through AdMob mediation (T34). Never initializes
 * or requests anything before UMP allows ad requests. Not child-directed and not under the age of consent: the app is
 * for adults only (AGENTS.md).
 */
class AdMobGateway(
    context: Context,
    private val consent: ConsentManager,
    private val resumed: ResumedActivity,
    private val scope: CoroutineScope,
) : AdGateway {
    private val app = context.applicationContext

    private val startup = AdStartup(
        scope,
        // Unity reads the US-states choice before the mediated SDK starts (ad-mediation.md "Ordering").
        beforeInitialize = { consent.updateUnity() },
        initialize = { done ->
            // Adults only: no child or teen treatment (replaces the deprecated child-directed / under-age tags).
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setAgeRestrictedTreatment(AgeRestrictedTreatment.UNSPECIFIED).build(),
            )
            // Google recommends initializing off the main thread.
            scope.launch(Dispatchers.IO) {
                MobileAds.initialize(app) {
                    scope.launch(Dispatchers.Main) {
                        Log.i(TAG, "MobileAds initialized")
                        done()
                        // Slots wanted before the SDK was up load now (eng D5).
                        loadWanted()
                    }
                }
            }
        },
    )

    override val sdkReady: Boolean get() = startup.ready

    private var rewarded: RewardedAd? = null
    private var rewardedAt = 0L
    private var rewardedLoading: CompletableDeferred<RewardedLoad>? = null

    private var interstitial: InterstitialAd? = null
    private var interstitialAt = 0L
    private var interstitialLoading = false

    /** No Activity was on screen for an interstitial load: it runs on the next resume. */
    private var interstitialRetry = false

    init {
        resumed.onResumed = {
            if (interstitialRetry) {
                interstitialRetry = false
                preloadInterstitial()
            }
        }
        // Consent settling later (an offline first launch, the form re-asked, CEO D6) loads the wanted slots then.
        scope.launch { consent.canRequestAds.collect { if (it) loadWanted() } }
    }

    /** One cached ad per 1.0.7 slot (eng D2), with when it loaded. */
    private class Cached(val ad: Any, val at: Long)

    private val wanted = mutableSetOf<AdSlot>()
    private val slotAds = mutableMapOf<AdSlot, Cached>()
    private val slotLoading = mutableSetOf<AdSlot>()
    private val _slotsReady = MutableStateFlow<Set<AdSlot>>(emptySet())
    override val slotsReady: StateFlow<Set<AdSlot>> = _slotsReady.asStateFlow()

    private fun publishSlots() {
        _slotsReady.value = slotAds.keys.toSet()
    }

    override fun want(slot: AdSlot) {
        wanted += slot
        loadWanted()
    }

    override fun unwant(slot: AdSlot) {
        // A loaded ad stays until it's shown or expires: the next moment may come within the hour.
        wanted -= slot
    }

    private fun loadWanted() {
        if (wanted.isEmpty()) return
        if (!canLoad()) {
            // Consent allows ads but the SDK isn't up yet: start it; its init loads the slots.
            if (consent.canRequestAds.value && !sdkReady) scope.launch { start() }
            return
        }
        wanted.toList().forEach(::loadSlot)
    }

    private fun loadSlot(slot: AdSlot) {
        if (!canLoad() || slot in slotAds || slot in slotLoading || slot.unit.isEmpty()) return
        slotLoading += slot
        val loaded: (Any) -> Unit = { ad ->
            val at = SystemClock.elapsedRealtime()
            slotLoading -= slot
            slotAds[slot] = Cached(ad, at)
            publishSlots()
            // Dropped after an hour (AdMob's expiry), and loaded again if its moment is still on screen.
            scope.launch {
                delay(MAX_AGE_MS)
                if (slotAds[slot]?.at == at) {
                    slotAds -= slot
                    publishSlots()
                    if (slot in wanted) loadSlot(slot)
                }
            }
        }
        val failed: (LoadAdError) -> Unit = { error ->
            Log.i(TAG, "$slot failed: ${error.code} ${error.message}")
            slotLoading -= slot
        }
        if (slot.rewardedInterstitial) {
            RewardedInterstitialAd.load(app, slot.unit, AdRequest.Builder().build(), object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) = loaded(ad)

                override fun onAdFailedToLoad(error: LoadAdError) = failed(error)
            })
        } else {
            RewardedAd.load(app, slot.unit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = loaded(ad)

                override fun onAdFailedToLoad(error: LoadAdError) = failed(error)
            })
        }
    }

    override fun showSlot(activity: Activity, slot: AdSlot, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
        val cached = slotAds[slot]?.takeIf { SystemClock.elapsedRealtime() - it.at < MAX_AGE_MS }
        slotAds -= slot
        publishSlots()
        if (cached == null) {
            loadWanted()
            return false
        }
        val callback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                _fullScreen.value = false
                onClosed(true)
                if (slot in wanted) loadSlot(slot)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "$slot failed to show: ${error.code} ${error.message}")
                _fullScreen.value = false
                onClosed(false)
                if (slot in wanted) loadSlot(slot)
            }
        }
        _fullScreen.value = true
        when (val ad = cached.ad) {
            is RewardedAd -> {
                ad.fullScreenContentCallback = callback
                ad.show(activity) { onEarned() }
            }
            is RewardedInterstitialAd -> {
                ad.fullScreenContentCallback = callback
                ad.show(activity) { onEarned() }
            }
        }
        return true
    }

    private val _fullScreen = MutableStateFlow(false)
    override val fullScreen: StateFlow<Boolean> = _fullScreen.asStateFlow()

    override fun online(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override suspend fun ensureConsent(activity: Activity): Boolean = consent.ensureBeforeAd(activity)

    override suspend fun start() {
        if (sdkReady || !consent.canRequestAds.value) return
        startup.start()
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
            override fun onAdDismissedFullScreenContent() {
                _fullScreen.value = false
                onClosed(true)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "rewarded failed to show: ${error.code} ${error.message}")
                _fullScreen.value = false
                onClosed(false)
            }
        }
        _fullScreen.value = true
        ad.show(activity) { onEarned() }
        return true
    }

    override fun preloadInterstitial() {
        if (!canLoad() || freshInterstitial() != null || interstitialLoading) return
        // Interstitials load with the Activity on screen (Unity mediation); rewarded loads keep the app context.
        val activity = resumed.current() ?: run { interstitialRetry = true; return }
        interstitialLoading = true
        InterstitialAd.load(activity, AdUnits.INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
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

            override fun onAdDismissedFullScreenContent() {
                _fullScreen.value = false
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                _fullScreen.value = false
            }
        }
        _fullScreen.value = true
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
