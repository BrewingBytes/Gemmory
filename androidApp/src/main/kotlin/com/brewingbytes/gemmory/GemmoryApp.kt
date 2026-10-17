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
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.PlayStreak
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.EntitlementCache
import com.brewingbytes.gemmory.policy.EntitlementCodec
import com.brewingbytes.gemmory.remote.HttpRemoteSource
import com.brewingbytes.gemmory.remote.RemoteKeys
import com.brewingbytes.gemmory.remote.RemotePictures
import com.brewingbytes.gemmory.remote.RemoteSource
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.storage.FileStateStore
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.CrashlyticsReporter
import com.brewingbytes.gemmory.telemetry.FirebaseAnalyticsLogger
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.brewingbytes.gemmory.timelapse.EncodeManager
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

    /** The daily play streak (#33). */
    lateinit var streak: PlayStreak
        private set

    /** Today's picture (#34). */
    lateinit var todays: TodaysPictures
        private set

    /** Remote pictures (#43): the signed catalog in the repo's `pictures/v1/`, checked once a day from the Gallery. */
    lateinit var remote: RemotePictures
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

    lateinit var appScope: CoroutineScope
        private set

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

    /** Connected tests only (#33): re-reads the streak from its preferences, which a test seeded. */
    @VisibleForTesting
    internal fun resetStreakForTest() {
        streak = newStreak()
    }

    /**
     * Connected tests only (#34): re-reads today's pick from its preferences, which a test cleared or seeded.
     * [enabled] false offers no picture of the day, for tests that assert which pictures are locked.
     */
    @VisibleForTesting
    internal fun resetTodaysForTest(enabled: Boolean = true) {
        todays = newTodays(enabled)
    }

    private fun newTodays(enabled: Boolean = true) = TodaysPictures(
        this,
        DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() },
        { monetization.unlocks },
        analytics,
        enabled,
    )

    private fun newRemote(source: RemoteSource = HttpRemoteSource(BuildConfig.REMOTE_PICTURES_URL), keys: List<ByteArray> = RemoteKeys.PRODUCTION) =
        RemotePictures(
            filesDir = filesDir,
            source = source,
            keys = keys,
            appVersion = BuildConfig.VERSION_CODE,
            reservedIds = BundledPictures.ALL.map { it.id }.toSet(),
            clock = DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() },
            scope = appScope,
            io = Dispatchers.IO.limitedParallelism(1),
            reporter = reporter,
        )

    /**
     * Connected tests only (#43): remote pictures from [source] signed by [keys] (a test key, never trusted in a
     * real build), re-read from disk, which the test cleared or seeded. Call before launching MainActivity. The
     * canvas store is rebuilt to read them. No argument puts GitHub and the production key back.
     */
    @VisibleForTesting
    internal fun resetRemoteForTest(source: RemoteSource? = null, keys: List<ByteArray> = RemoteKeys.PRODUCTION) {
        remote.pause()
        remote = if (source == null) newRemote() else newRemote(source, keys)
        store = newStore()
    }

    /** A picture's canvas by id: ids are unique across bundled and remote pictures (the catalog check refuses a clash). */
    fun pictureRef(id: String): CanvasRef =
        CanvasRef(if (BundledPictures.byId(id) == null && remote.listed().any { it.id == id }) CanvasKind.REMOTE else CanvasKind.BUNDLED, id)

    private fun newStore() = CanvasStore(
        filesDir = filesDir,
        scope = appScope,
        writer = storeWriter,
        bundledGrid = { picture -> assets.open(picture.asset).use { it.readBytes() } },
        encodes = encodes,
        reporter = reporter,
        analytics = analytics,
        remote = remote,
    )

    private val storeWriter = Dispatchers.IO.limitedParallelism(1)

    private fun newStreak() =
        PlayStreak(this, DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() }, analytics) { monetization.onStreakDay(it) }

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
        streak = newStreak()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        encodes = EncodeManager(
            cacheDir = cacheDir,
            scope = scope,
            backend = { request, partialOf -> MediaCodecEncoder(this, request, partialOf) },
            canvasExists = { ref -> store.repository.dirOf(ref).isDirectory },
            reporter = reporter,
            analytics = analytics,
        )
        appScope = scope
        remote = newRemote()
        store = newStore()
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
        monetization = newMonetization()
        todays = newTodays()
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
