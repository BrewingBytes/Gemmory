package com.brewingbytes.gemmory

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.AdMobGateway
import com.brewingbytes.gemmory.ads.ConsentManager
import com.brewingbytes.gemmory.ads.Interstitials
import com.brewingbytes.gemmory.ads.Monetization
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.billing.BillingRepository
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.EntitlementCache
import com.brewingbytes.gemmory.policy.EntitlementCodec
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.storage.FileStateStore
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.timelapse.EncodeManager
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.CrashlyticsReporter
import com.brewingbytes.gemmory.telemetry.FirebaseAnalyticsLogger
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.brewingbytes.gemmory.timelapse.MediaCodecEncoder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import java.io.File

class GemmoryApp : Application() {
    /** Crashlytics in release builds, Logcat in debug (#12). Kinds, codes and reasons only, scrubbed of paths. */
    lateinit var reporter: NonFatalReporter
        private set

    /** Firebase Analytics under UMP consent in release builds, Logcat in debug (#12). */
    lateinit var analytics: Analytics
        private set

    /** App-scoped, so a canvas's last write finishes even after its screen and ViewModel are gone. */
    lateinit var store: CanvasStore
        private set

    lateinit var settings: AppSettings
        private set

    /** App-scoped, so an encode keeps going while the process lives, whatever screen is showing (design D8). */
    lateinit var encodes: EncodeManager
        private set

    /** Play Billing: entitlements, prices, purchases and restore (#8). */
    lateinit var billing: BillingRepository
        private set

    /** The daily allowance, the interstitial caps, picture unlocks and entitlements (#7). */
    lateinit var monetization: Monetization
        private set

    lateinit var consent: ConsentManager
        private set

    lateinit var ads: AdGateway
        private set

    lateinit var interstitials: Interstitials
        private set

    private lateinit var adMob: AdGateway

    private lateinit var appScope: CoroutineScope

    private fun newMonetization(processStart: Boolean = true) = Monetization(
        filesDir = filesDir,
        clock = DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() },
        unlocks = PictureUnlocks(this),
        scope = appScope,
        policy = Dispatchers.IO.limitedParallelism(1),
        entitlements = billing.entitlements,
        reporter = reporter,
        processStart = processStart,
    )

    /**
     * Connected tests only (#32): runs the ads on [ads] (AdMob by default) and re-reads the ledger and the unlocks from
     * disk, so a test that cleared them starts with today's grace unused. Call before launching MainActivity. Not a
     * cold start: the ads ledger's session state is left as it is. The replaced instance is closed.
     */
    @VisibleForTesting
    internal fun resetMonetizationForTest(ads: AdGateway = adMob) {
        this.ads = ads
        monetization.close()
        monetization = newMonetization(processStart = false)
        interstitials = Interstitials(monetization, ads, { ref -> encodes.state(ref).value }, analytics)
    }

    override fun onCreate() {
        super.onCreate()
        reporter = NonFatalReporter.forBuild(BuildConfig.FIREBASE) {
            CrashlyticsReporter.scrubCrashes()
            CrashlyticsReporter(FirebaseCrashlytics.getInstance())
        }
        analytics = Analytics.forBuild(BuildConfig.FIREBASE) { FirebaseAnalyticsLogger(FirebaseAnalytics.getInstance(this)) }
        settings = AppSettings(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        encodes = EncodeManager(
            cacheDir = cacheDir,
            scope = scope,
            backend = { request, partialOf -> MediaCodecEncoder(this, request, partialOf) },
            canvasExists = { ref -> store.repository.dirOf(ref).isDirectory },
            reporter = reporter,
            analytics = analytics,
        )
        store = CanvasStore(
            filesDir = filesDir,
            scope = scope,
            writer = Dispatchers.IO.limitedParallelism(1),
            bundledGrid = { picture -> assets.open(picture.asset).use { it.readBytes() } },
            encodes = encodes,
            reporter = reporter,
            analytics = analytics,
        )
        billing = BillingRepository(
            backend = BillingBackends.create(this, reporter),
            cache = EntitlementCache(
                System::currentTimeMillis,
                FileStateStore(File(filesDir, StorageLayout.LEDGER_ENTITLEMENTS), EntitlementCodec::encode, EntitlementCodec::decode, reporter),
            ),
            scope = scope,
            worker = Dispatchers.IO.limitedParallelism(1),
            analytics = analytics,
        )
        appScope = scope
        monetization = newMonetization()
        consent = ConsentManager(this)
        // Consent Mode v2 from the UMP result, now and on every change (R3-7).
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { consent.consentMode.collect { analytics.consent(it) } }
        ads = AdMobGateway(this, consent, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        adMob = ads
        interstitials = Interstitials(monetization, ads, { ref -> encodes.state(ref).value }, analytics)
        // The first session ends on the first foreground after ≥ 30 min in the background (eng D6). Billing asks Play
        // on every foreground, the first one being the cold start (eng E6).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                monetization.onForeground()
                billing.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) = monetization.onBackground()
        })
    }
}
