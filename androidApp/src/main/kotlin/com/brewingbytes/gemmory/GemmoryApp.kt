package com.brewingbytes.gemmory

import android.app.Activity
import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.brewingbytes.gemmory.achievements.Achievements
import com.brewingbytes.gemmory.achievements.GamesBackend
import com.brewingbytes.gemmory.achievements.PlayGamesBackend
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.AdMobGateway
import com.brewingbytes.gemmory.ads.ResumedActivity
import com.brewingbytes.gemmory.ads.ConsentManager
import com.brewingbytes.gemmory.ads.Interstitials
import com.brewingbytes.gemmory.ads.Monetization
import com.brewingbytes.gemmory.ads.isLocked
import com.brewingbytes.gemmory.audio.MusicController
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.billing.BillingRepository
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.collections.CollectionPrefs
import com.brewingbytes.gemmory.collections.Collections
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.play.PlayStreak
import com.brewingbytes.gemmory.policy.Achievement
import com.brewingbytes.gemmory.policy.AchievementCodec
import com.brewingbytes.gemmory.policy.AchievementRules
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.DayPlayed
import com.brewingbytes.gemmory.policy.EntitlementCache
import com.brewingbytes.gemmory.policy.EntitlementCodec
import com.brewingbytes.gemmory.policy.GameEvent
import com.brewingbytes.gemmory.policy.ReminderReason
import com.brewingbytes.gemmory.policy.StreakHistoryCodec
import com.brewingbytes.gemmory.reminder.ReminderJob
import com.brewingbytes.gemmory.reminder.ReminderNotifier
import com.brewingbytes.gemmory.reminder.ReminderPicture
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.reminder.ReminderScheduler
import com.brewingbytes.gemmory.reminder.Reminders
import com.brewingbytes.gemmory.reminder.SystemReminderAlarm
import com.brewingbytes.gemmory.reminder.SystemReminderComponents
import com.brewingbytes.gemmory.remote.HttpRemoteSource
import com.brewingbytes.gemmory.remote.RemoteKeys
import com.brewingbytes.gemmory.remote.RemotePictures
import com.brewingbytes.gemmory.remote.RemoteSource
import com.brewingbytes.gemmory.review.PlayReviewGateway
import com.brewingbytes.gemmory.review.ReviewGateway
import com.brewingbytes.gemmory.review.ReviewPrefs
import com.brewingbytes.gemmory.review.Reviews
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.storage.FileStateStore
import com.brewingbytes.gemmory.storage.LoadResult
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.streak.StreakView
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.ConsentFirst
import com.brewingbytes.gemmory.telemetry.CrashlyticsReporter
import com.brewingbytes.gemmory.telemetry.FirebaseAnalyticsLogger
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.brewingbytes.gemmory.telemetry.PLAYED_FILE
import com.brewingbytes.gemmory.telemetry.PrefsDayStore
import com.brewingbytes.gemmory.timelapse.EncodeManager
import com.brewingbytes.gemmory.timelapse.MediaCodecEncoder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

    /** The streak with what covers a missed day (#101, O8): every streak reader goes through it. */
    lateinit var streakView: StreakView
        private set

    /** Play Games achievements (#49): counted on the phone, sent while the Gallery or Settings shows. */
    lateinit var achievements: Achievements
        private set

    /** Play Games, set up at the first foreground of a process (eng D11), never on a background start. */
    private val playGames: GamesBackend by lazy {
        if (BuildConfig.PLAY_GAMES) PlayGamesBackend(this, getString(R.string.game_services_project_id), reporter) else GamesBackend.Off
    }

    private val games = object : GamesBackend {
        override val available: Boolean get() = playGames.available

        override fun session(activity: Activity) = playGames.session(activity)
    }

    /** The evening reminder (T35): Settings, the ask and the alarm. */
    lateinit var reminders: Reminders
        private set

    private lateinit var reminderNotifier: ReminderNotifier

    /** The in-app review prompt (T39): off until `features.review` is on in the signed catalog. */
    lateinit var reviews: Reviews
        private set

    /** The app is on screen (between the process's onStart and onStop): the reminder skips its evening then. */
    @Volatile
    private var foreground = false

    private var firstForegroundDone = false

    /** Connected tests only (T36): the wall time that dates drops, Today's picture and the gallery's midnight reload. */
    @VisibleForTesting
    @Volatile
    internal var testNowMillis: (() -> Long)? = null

    /**
     * The local date of drops (T36), Today's picture, `day_played` and the gallery's midnight reload: one clock, so they
     * all turn over together.
     */
    val dayClock = DayClock({ testNowMillis?.invoke() ?: System.currentTimeMillis() }) { TimeZone.currentSystemDefault() }

    /** Today's picture (#34). */
    lateinit var todays: TodaysPictures
        private set

    /** `day_played` (T36): the local day it was last logged, in the "played" preferences (a day, no user content). */
    lateinit var dayPlayed: DayPlayed
        private set

    /** Remote pictures (#43): the signed catalog in the repo's `pictures/v1/`, checked once a day from the Gallery. */
    lateinit var remote: RemotePictures
        private set

    /** Collections (T37): progress, completion, keepsake and token. Reads the current remote, store and monetization. */
    lateinit var collections: Collections
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

    /** The gateway in use, so the music follows a test's fake ads too (T42). */
    private lateinit var gateways: MutableStateFlow<AdGateway>

    /** Ambient music (T42). */
    lateinit var music: MusicController
        private set

    lateinit var appScope: CoroutineScope
        private set

    /** Never counted as a cold start here: that happens at the process's first foreground (eng D11). */
    private fun newMonetization() = Monetization(
        filesDir = filesDir,
        clock = DayClock(System::currentTimeMillis) { TimeZone.currentSystemDefault() },
        unlocks = PictureUnlocks(this),
        scope = appScope,
        policy = Dispatchers.IO.limitedParallelism(1),
        entitlements = billing.entitlements,
        reporter = reporter,
        processStart = false,
    )

    /** Connected tests only (#33): re-reads the streak from its preferences, which a test seeded. */
    @VisibleForTesting
    internal fun resetStreakForTest() {
        streakView.close()
        streak = newStreak()
        streakView = newStreakView()
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
        dayClock,
        { monetization.unlocks },
        analytics,
        enabled,
        // Read on each use, so a connected test's replaced remote pictures count (T36).
        { remote.todaysDrops() },
    )

    private fun newRemote(
        source: RemoteSource = HttpRemoteSource(BuildConfig.REMOTE_PICTURES_URL),
        keys: List<ByteArray> = RemoteKeys.PRODUCTION,
        appVersion: Int = BuildConfig.VERSION_CODE,
    ) =
        RemotePictures(
            filesDir = filesDir,
            source = source,
            keys = keys,
            appVersion = appVersion,
            reservedIds = BundledPictures.ALL.map { it.id }.toSet(),
            clock = dayClock,
            scope = appScope,
            io = Dispatchers.IO.limitedParallelism(1),
            reporter = reporter,
            // Read on each use: the unlocks are replaced with the monetization in connected tests.
            earned = { if (::monetization.isInitialized) monetization.unlocks.unlocked.value else emptySet() },
        )

    private fun newCollections() = Collections(
        prefs = CollectionPrefs(getSharedPreferences(CollectionPrefs.FILE, MODE_PRIVATE)),
        catalog = { remote.catalog() },
        vip = { monetization.entitlements.value.vip },
        unlock = { monetization.unlocks.unlock(it) },
        earnToken = { monetization.earnTokenOnce(it) },
        keep = { store.keep(it) },
        keptToday = { remote.earnedToday(it) },
        today = { dayClock.today() },
        analytics = analytics,
    )

    /** Connected tests only (T37): re-reads the completed collections from their preferences, which a test cleared. */
    @VisibleForTesting
    internal fun resetCollectionsForTest() {
        collections = newCollections()
    }

    /**
     * Connected tests only (#43): remote pictures from [source] signed by [keys] (a test key, never trusted in a
     * real build), re-read from disk, which the test cleared or seeded. Call before launching MainActivity. The
     * canvas store is rebuilt to read them. No argument puts GitHub and the production key back. [appVersion] reads the
     * catalog as a later app would, for a field this build's versionCode doesn't take yet (T43's mystery before 16).
     */
    @VisibleForTesting
    internal fun resetRemoteForTest(source: RemoteSource? = null, keys: List<ByteArray> = RemoteKeys.PRODUCTION, appVersion: Int = BuildConfig.VERSION_CODE) {
        remote.pause()
        remote = if (source == null) newRemote() else newRemote(source, keys, appVersion)
        store = newStore()
    }

    /** A picture's canvas by id: ids are unique across bundled and remote pictures (the catalog check refuses a clash). */
    fun pictureRef(id: String): CanvasRef =
        CanvasRef(if (BundledPictures.byId(id) == null && remote.listed().any { it.id == id }) CanvasKind.REMOTE else CanvasKind.BUNDLED, id)

    /**
     * [pictureRef] for an id from outside the gallery (a rewarded sheet, later a notification): null unless it's a
     * bundled picture, a listed remote one, or a remote one already started. A dated drop before its date (T36) is
     * none of these, so a crafted or stale id can't open it.
     */
    fun openableRef(id: String): CanvasRef? {
        val ref = pictureRef(id)
        return when {
            ref.kind == CanvasKind.REMOTE || BundledPictures.byId(id) != null -> ref
            store.repository.dirOf(CanvasRef(CanvasKind.REMOTE, id)).isDirectory -> CanvasRef(CanvasKind.REMOTE, id)
            else -> null
        }
    }

    private fun newReminders(): Reminders {
        val prefs = ReminderPrefs(getSharedPreferences(ReminderPrefs.FILE, MODE_PRIVATE))
        val scheduler = ReminderScheduler(
            prefs,
            SystemReminderAlarm(this),
            SystemReminderComponents(this),
            { dayClock.nowMillis() },
            { TimeZone.currentSystemDefault() },
        )
        return Reminders(prefs, scheduler, reminderNotifier, analytics)
    }

    private fun newReviews(gateway: ReviewGateway = PlayReviewGateway(), finishes: () -> Int = { achievements.finishes }) = Reviews(
        prefs = ReviewPrefs(getSharedPreferences(ReviewPrefs.FILE, MODE_PRIVATE)),
        gateway = gateway,
        analytics = analytics,
        flagOn = { remote.catalog()?.features?.review == true },
        finishes = finishes,
        today = dayClock::today,
    )

    /**
     * Connected tests only (T39): re-reads the review counter, which a test cleared, asks [gateway] instead of Play
     * and, given [finishes], counts those instead of the achievements ledger's. No arguments puts Play back.
     */
    @VisibleForTesting
    internal fun resetReviewsForTest(gateway: ReviewGateway = PlayReviewGateway(), finishes: (() -> Int)? = null) {
        reviews = if (finishes == null) newReviews(gateway) else newReviews(gateway, finishes)
    }

    /** Connected tests only (T35): re-reads the reminder settings from their preferences, which a test cleared or seeded. */
    @VisibleForTesting
    internal fun resetRemindersForTest() {
        reminders = newReminders()
    }

    /**
     * The evening run (T35) for the alarm set for [scheduledAt], from the reminder receiver: what's on the phone now,
     * Today's pick stored, and the post.
     */
    suspend fun runEveningReminder(scheduledAt: Long) {
        // Turned off after the alarm was handed over, or an alarm from before this field existed: nothing to do.
        if (!reminders.on.value || scheduledAt == 0L) return
        val vip = monetization.entitlements.value.vip
        val job = ReminderJob(
            clock = dayClock,
            zone = { TimeZone.currentSystemDefault() },
            check = { remote.checkForReminder() },
            canvases = { store.reminderCanvases() },
            todaysDrops = { remote.todaysDrops().toList() },
            pickToday = { started -> todays.pick(started, vip, remote.listed(), remote.drops(), remote::isNew) },
            streak = { streak.state() },
            cover = { streakView.cover() },
            allowed = reminderNotifier::allowed,
            foreground = { foreground },
            notify = { post -> reminderNotifier.post(post, post.pictureId?.let(::reminderPicture), vip, dayClock.today()) },
            analytics = analytics,
        )
        job.run(scheduledAt)
    }

    /** A bundled or remote picture as the notification names and draws it; null if it can't be opened now. */
    private fun reminderPicture(id: String): ReminderPicture? {
        val ref = openableRef(id) ?: return null
        BundledPictures.byId(id)?.let { picture ->
            return ReminderPicture(picture.title, BundledPictures.canvasSizeOf(id)) {
                (GridCodec.decode(assets.open(picture.asset).use { it.readBytes() }) as? Decoded.Ok)?.value?.pattern
            }
        }
        val listed = remote.listed().firstOrNull { it.id == id }
        val title = listed?.title ?: CanvasStore.titleOf(ref, store.repository.readRecord(ref))
        // An unfinished mystery (T43) is never named or drawn in the reminder.
        val mystery = store.hiddenAs(ref) != null
        return ReminderPicture(title, listed?.size, mystery) {
            store.pattern(ref) ?: (store.repository.load(ref) as? LoadResult.Loaded)?.grid?.pattern
        }
    }

    /**
     * Where a reminder tap goes (T35; design system, navigation map): a drop or Today's picture opens its canvas while
     * it's still today's and free, or open anyway; a streak or almost-finished canvas opens while it exists unfinished.
     * Null sends the player to the gallery with no message: after midnight, withdrawn, no longer free, or a crafted id.
     */
    suspend fun reminderTarget(reason: ReminderReason, pictureId: String?, canvasKey: String?, day: Long): CanvasRef? {
        if (day != dayClock.today()) return null
        return when (reason) {
            ReminderReason.DROP, ReminderReason.TODAY -> {
                val ref = pictureId?.let(::openableRef) ?: return null
                val vip = monetization.entitlements.value.vip
                val started = store.isStarted(ref)
                val catalogLocked = BundledPictures.byId(ref.id)?.locked ?: remote.listed().firstOrNull { it.id == ref.id }?.locked ?: true
                val free = todays.isFreeToday(ref) || todays.isToday(ref, vip)
                ref.takeIf { free || !isLocked(catalogLocked, ref.id, monetization.unlocks.unlocked.value, monetization.entitlements.value, started) }
            }
            ReminderReason.STREAK, ReminderReason.ALMOST_FINISHED -> {
                val ref = canvasKey?.let(CanvasStore::refOfReminderKey) ?: return null
                ref.takeIf { store.repository.dirOf(it).isDirectory && !store.isFinished(it) }
            }
        }
    }

    private fun newStore() = CanvasStore(
        filesDir = filesDir,
        scope = appScope,
        writer = storeWriter,
        bundledGrid = { picture -> assets.open(picture.asset).use { it.readBytes() } },
        encodes = encodes,
        reporter = reporter,
        analytics = analytics,
        remote = remote,
        achievements = { achievements.record(it) },
        dayClock = dayClock,
    )

    private val storeWriter = Dispatchers.IO.limitedParallelism(1)

    private fun newStreak() = PlayStreak(
        context = this,
        clock = dayClock,
        analytics = analytics,
        history = FileStateStore(File(filesDir, StorageLayout.STREAK_HISTORY), StreakHistoryCodec::encode, StreakHistoryCodec::decode, reporter),
        scope = appScope,
        resolve = { monetization.streakDay(it) },
    ) { achievements.record(GameEvent.StreakDay(it)) }

    private fun newStreakView() = StreakView(streak, dayClock, billing.entitlements, appScope)

    private fun newAchievements(backend: GamesBackend, ids: (Achievement) -> String = { getString(achievementId(it)) }) = Achievements(
        store = FileStateStore(File(filesDir, StorageLayout.ACHIEVEMENTS), AchievementCodec::encode, AchievementCodec::decode, reporter),
        backend = backend,
        ids = ids,
        scope = appScope,
        worker = Dispatchers.IO.limitedParallelism(1),
    )

    /**
     * Connected tests only (#49): achievements sent to [backend] (a fake games client) under [ids] (the real ones are
     * blank until Play Console is set up), re-read from their file, which the test cleared or seeded. No argument puts
     * the build's backend and ids back.
     */
    @VisibleForTesting
    internal fun resetAchievementsForTest(backend: GamesBackend? = null, ids: ((Achievement) -> String)? = null) {
        achievements = if (backend == null) newAchievements(games) else newAchievements(backend, ids ?: { getString(achievementId(it)) })
    }

    /** Each canvas's first finished day, for the streak log's one-time backfill (#101, E4): day to canvas. */
    private suspend fun firstFinishes(): List<Pair<Long, String>> {
        val (pictures, photos) = store.summaries()
        return (pictures + photos).filterIsInstance<CanvasSummary.Playable>().mapNotNull { summary ->
            summary.firstFinishedDay?.let { it to CanvasStore.reminderKey(summary.ref) }
        }
    }

    /** Every canvas finished on the phone, for the one-time achievements backfill (founder, 2026-10-18). */
    private suspend fun finishedOnPhone(): List<GameEvent.Finished> {
        val (pictures, photos) = store.summaries()
        return (pictures + photos).filterIsInstance<CanvasSummary.Playable>().filter { it.state.isFinished }.map {
            val pattern = it.state.pattern
            GameEvent.Finished(it.size ?: AchievementRules.sizeOf(pattern.width, pattern.height), it.ref.kind == CanvasKind.PHOTO)
        }
    }

    /**
     * Connected tests only (#32): runs the ads on [ads] (AdMob by default) and re-reads the ledger and the unlocks from
     * disk, so a test that cleared them starts with today's grace unused. Call before launching MainActivity. Not a
     * cold start: the ads ledger's session state is left as it is. The replaced instance is closed.
     */
    @VisibleForTesting
    internal fun resetMonetizationForTest(ads: AdGateway = adMob) {
        this.ads = ads
        gateways.value = ads
        monetization.close()
        monetization = newMonetization()
        interstitials = Interstitials(monetization, ads, { ref -> encodes.state(ref).value }, analytics)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun newMusic() = MusicController(
        context = this,
        settings = settings,
        reporter = reporter,
        adShowing = gateways.flatMapLatest { it.fullScreen },
        exporting = encodes.encoding,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    /** An achievement's Play Console id resource (games-ids.xml, named as Play Console exports them). */
    private fun achievementId(achievement: Achievement): Int = when (achievement) {
        Achievement.FIRST_SPARKLE -> R.string.achievement_first_sparkle
        Achievement.QUICK_STUDY -> R.string.achievement_quick_study
        Achievement.STEADY_HANDS -> R.string.achievement_steady_hands
        Achievement.THE_LONG_EVENING -> R.string.achievement_the_long_evening
        Achievement.YOUR_OWN_GEM -> R.string.achievement_your_own_gem
        Achievement.SHOW_AND_TELL -> R.string.achievement_show_and_tell
        Achievement.WORK_IN_PROGRESS -> R.string.achievement_work_in_progress
        Achievement.TRAY_REGULAR -> R.string.achievement_tray_regular
        Achievement.GALLERY_WALL -> R.string.achievement_gallery_wall
        Achievement.PHOTO_ALBUM -> R.string.achievement_photo_album
        Achievement.A_WEEK_OF_GEMS -> R.string.achievement_a_week_of_gems
        Achievement.A_MONTH_OF_GEMS -> R.string.achievement_a_month_of_gems
    }

    override fun onCreate() {
        super.onCreate()
        reporter = NonFatalReporter.forBuild(BuildConfig.FIREBASE) {
            CrashlyticsReporter.scrubCrashes()
            CrashlyticsReporter(FirebaseCrashlytics.getInstance())
        }
        analytics = ConsentFirst(Analytics.forBuild(BuildConfig.FIREBASE) { FirebaseAnalyticsLogger(FirebaseAnalytics.getInstance(this)) })
        // The saved consent reaches Firebase before anything can log (eng D6): a reminder or reboot may start the app in
        // the background and log at once. Later changes follow below.
        consent = ConsentManager(this, reporter)
        analytics.consent(consent.consentMode.value)
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
        streak = newStreak()
        streakView = newStreakView()
        todays = newTodays()
        collections = newCollections()
        dayPlayed = DayPlayed(dayClock, PrefsDayStore(this, PLAYED_FILE))
        achievements = newAchievements(games)
        // Consent Mode v2 from the UMP result, on every change (R3-7); the saved one was applied above.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { consent.consentMode.collect { analytics.consent(it) } }
        // Interstitial loads use the Activity on screen (T34), held weakly.
        val resumed = ResumedActivity().also(::registerActivityLifecycleCallbacks)
        ads = AdMobGateway(this, consent, resumed, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        adMob = ads
        interstitials = Interstitials(monetization, ads, { ref -> encodes.state(ref).value }, analytics)
        gateways = MutableStateFlow(ads)
        music = newMusic()
        reminderNotifier = ReminderNotifier(this)
        reminders = newReminders()
        reviews = newReviews()
        // Every start re-arms the alarm, since a force-stop clears it (T35).
        reminders.reschedule()
        // The first session ends on the first foreground after ≥ 30 min in the background (eng D6). Billing asks Play
        // on every foreground, the first one being the cold start (eng E6).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                if (!firstForegroundDone) {
                    firstForegroundDone = true
                    onFirstForeground()
                }
                monetization.onForeground()
                billing.onForeground()
                music.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                monetization.onBackground()
                music.setForeground(false)
            }
        })
    }

    /**
     * The process came to the screen for the first time (T35, eng D11). Only now does it count as a cold start for the
     * first session, and only now do Play Games and the achievements backfill set up, so a reminder, reboot or update
     * that starts the process in the background does none of it.
     */
    private fun onFirstForeground() {
        monetization.onProcessStart()
        playGames.available
        val streakNow = streakView.refresh().shown
        achievements.backfill { finishedOnPhone() to streakNow }
        appScope.launch { streak.backfill(::firstFinishes) }
    }
}
