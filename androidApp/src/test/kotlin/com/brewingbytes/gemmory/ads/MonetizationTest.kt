package com.brewingbytes.gemmory.ads

import android.app.Activity
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.ConsumeResult
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.policy.Spent
import com.brewingbytes.gemmory.policy.VipPlan
import kotlinx.coroutines.flow.MutableStateFlow
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.timelapse.EncodeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonetizationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_760_000_000_000L // 2025-10-09, a Thursday morning in Bucharest
    private val clock = DayClock({ now }) { TimeZone.of("Europe/Bucharest") }
    private val reports = mutableListOf<Throwable>()
    private val prefs = FakePrefs()

    private val entitlements = MutableStateFlow(Entitlements.FREE)

    private fun TestScope.monetization(processStart: Boolean = true): Monetization = Monetization(
        filesDir = tmp.root,
        clock = clock,
        unlocks = PictureUnlocks(prefs),
        scope = backgroundScope,
        policy = UnconfinedTestDispatcher(testScheduler),
        reporter = { reports += it },
        entitlements = entitlements,
        processStart = processStart,
    )

    private class FakeGateway : AdGateway {
        var loaded = true
        val shown = mutableListOf<String>()
        var preloads = 0
        override val sdkReady = true

        override fun online() = true

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded(): RewardedLoad = RewardedLoad.Loaded

        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false

        override fun preloadInterstitial() {
            preloads++
        }

        override fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean {
            if (!loaded) return false
            shown += "interstitial"
            onShown()
            return true
        }
    }

    private val ref = CanvasRef(CanvasKind.BUNDLED, "starter")
    private val noActivity: Activity? = null

    @Test
    fun theLedgerLivesUnderTheBackupExcludedDir() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = false), m.consume("photo-1"))
        m.onPictureFinished()
        val files = File(tmp.root, StorageLayout.LEDGER).listFiles()!!.map { it.name }.toSet()
        assertEquals(setOf("daily", "ads"), files)
        assertTrue(File(tmp.root, StorageLayout.LEDGER_DAILY).isFile && File(tmp.root, StorageLayout.LEDGER_ADS).isFile)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun allowanceFlowFollowsTheLedgerAndSurvivesARestart() = runTest(UnconfinedTestDispatcher()) {
        val first = monetization()
        assertEquals(Allowance.Free, first.allowance.value)
        first.consume("photo-1")
        assertEquals(Allowance.WatchVideo, first.allowance.value)
        first.grantReward()
        assertEquals(Allowance.Bonus, first.allowance.value)

        val second = monetization()
        second.refresh()
        assertEquals(Allowance.Bonus, second.allowance.value)
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = true), second.consume("photo-1"), "idempotent across processes")
    }

    @Test
    fun theAllowanceFollowsBillingAsVipStartsAndLapses() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        m.consume("photo-1")
        assertEquals(Allowance.WatchVideo, m.allowance.value)
        entitlements.value = Entitlements(VipPlan.YEARLY)
        assertEquals(Allowance.Unlimited, m.allowance.value, "VIP started: no line, Start")
        assertEquals(ConsumeResult.Ok(null, repeat = false), m.consume("photo-2"), "VIP uses nothing")
        entitlements.value = Entitlements(removeAds = true)
        assertEquals(Allowance.WatchVideo, m.allowance.value, "VIP lapsed: remove-ads adds no canvases")
    }

    @Test
    fun theVipActionWaitsForTheSecondSession() = runTest(UnconfinedTestDispatcher()) {
        assertFalse(monetization().firstSessionOver.value, "first launch")
        assertTrue(monetization().firstSessionOver.value, "a later cold start")
    }

    /** #32: a connected test's replacement instance is not a cold start, so it never ends the first session. */
    @Test
    fun aReplacementInstanceIsNotAColdStart() = runTest(UnconfinedTestDispatcher()) {
        monetization().close()
        assertFalse(monetization(processStart = false).firstSessionOver.value, "replaced in the same process")
        assertTrue(monetization().firstSessionOver.value, "a later real cold start")
    }

    /**
     * T35 (eng D11): a process the reminder, a reboot or an update started in the background never counts as a cold
     * start; its first foreground ([Monetization.onProcessStart]) does, as before.
     */
    @Test
    fun aBackgroundProcessStartLeavesTheFirstSessionAlone() = runTest(UnconfinedTestDispatcher()) {
        monetization(processStart = false).also { it.onProcessStart() }.close()
        // Two receiver starts that never came to the screen.
        repeat(2) { assertFalse(monetization(processStart = false).also { it.close() }.firstSessionOver.value, "a background start") }
        val foreground = monetization(processStart = false)
        assertFalse(foreground.firstSessionOver.value)
        foreground.onProcessStart()
        assertTrue(foreground.firstSessionOver.value, "the next real cold start ends the first session")
    }

    @Test
    fun aClosedInstanceStopsFollowingBilling() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        m.consume("photo-1")
        m.close()
        entitlements.value = Entitlements(VipPlan.YEARLY)
        assertEquals(Allowance.WatchVideo, m.allowance.value)
    }

    @Test
    fun theAllowanceResetsAtLocalMidnightWhileTheAppRuns() = runTest(UnconfinedTestDispatcher()) {
        now = 1_760_043_600_000L - 60_000 // 23:59 local on 2025-10-09 (21:00Z is midnight in Bucharest, UTC+3)
        val m = monetization()
        m.consume("photo-1")
        assertEquals(Allowance.WatchVideo, m.allowance.value)
        now += 2 * 60_000
        testScheduler.advanceTimeBy(2 * 60_000)
        testScheduler.runCurrent()
        assertEquals(Allowance.Free, m.allowance.value, "the midnight ticker refreshed the line")
    }

    @Test
    fun interstitialCapsAfterLeavingAFreshCompletion() = runTest(UnconfinedTestDispatcher()) {
        val gateway = FakeGateway()
        var encode: EncodeState = EncodeState.Idle
        // First process: first session, so nothing even after two finishes.
        val first = monetization()
        val firstAds = Interstitials(first, gateway, { encode }, log = {})
        first.onPictureFinished()
        first.onPictureFinished()
        firstAds.onFreshCompletion()
        assertEquals(0, gateway.preloads, "no request when the caps already say no")
        firstAds.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertTrue(gateway.shown.isEmpty(), "first session")

        // A later cold start ends the first session.
        val second = monetization()
        val ads = Interstitials(second, gateway, { encode }, log = {})
        ads.onFreshCompletion()
        assertEquals(1, gateway.preloads)
        encode = EncodeState.Running(10, 450)
        ads.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertTrue(gateway.shown.isEmpty(), "never during share")
        encode = EncodeState.Idle
        ads.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertEquals(1, gateway.shown.size, "2nd finish, later session")

        now += 9 * 60_000
        ads.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertEquals(1, gateway.shown.size, "within 10 min")
        now += 60_000
        ads.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertEquals(2, gateway.shown.size, "10 min later")

        second.onRewardedShown()
        now += 10 * 60_000
        ads.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertEquals(3, gateway.shown.size, "rewarded 10 min ago is fine")
        now += 10 * 60_000 - 60_000
        second.onRewardedShown()
        now += 60_000
        ads.onLeftFreshCompletion(noActivity.orFake(), ref)
        assertEquals(3, gateway.shown.size, "1 min after a rewarded ad")
    }

    @Test
    fun picturesUnlockForGoodAndVipSeesNoLocks() = runTest(UnconfinedTestDispatcher()) {
        val locked = BundledPicture("sunflowers", "Sunflowers", "pictures/x.gmrg", 0, locked = true)
        val free = BundledPicture("starter", "The Starry Night", "pictures/starter.gmrg", 0)
        data class Row(val name: String, val picture: BundledPicture, val unlocked: Set<String>, val entitlements: Entitlements, val started: Boolean, val expected: Boolean)
        val rows = listOf(
            Row("locked, free player", locked, emptySet(), Entitlements.FREE, started = false, expected = true),
            Row("remove-ads still watches", locked, emptySet(), Entitlements(removeAds = true), started = false, expected = true),
            Row("unlocked", locked, setOf("sunflowers"), Entitlements.FREE, started = false, expected = false),
            Row("VIP", locked, emptySet(), Entitlements(VipPlan.WEEKLY), started = false, expected = false),
            Row("started under a lapsed VIP (E4c)", locked, emptySet(), Entitlements.FREE, started = true, expected = false),
            Row("a free picture", free, emptySet(), Entitlements.FREE, started = false, expected = false),
        )
        for (r in rows) assertEquals(r.expected, isLocked(r.picture, r.unlocked, r.entitlements, r.started), r.name)

        val m = monetization()
        assertTrue(m.useGrace(RewardType.PICTURE))
        m.unlocks.unlock("sunflowers")
        assertFalse(m.useGrace(RewardType.PICTURE), "one picture grace a day")
        assertEquals(setOf("sunflowers"), PictureUnlocks(prefs).unlocked.value, "permanent: a new process still has it")
        now += 2 * 24 * 3_600_000L
        assertEquals(setOf("sunflowers"), PictureUnlocks(prefs).unlocked.value, "no midnight reset for unlocks")
    }

    /** Interstitials only hands the activity to the gateway; the fake never touches it. */
    private fun Activity?.orFake(): Activity = this ?: FAKE_ACTIVITY

    private companion object {
        val FAKE_ACTIVITY: Activity by lazy {
            sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let { f ->
                f.isAccessible = true
                (f.get(null) as sun.misc.Unsafe).allocateInstance(Activity::class.java) as Activity
            }
        }
    }
}
