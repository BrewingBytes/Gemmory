package com.brewingbytes.gemmory.ads

import android.app.Activity
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.SpotRules
import com.brewingbytes.gemmory.policy.VipPlan
import com.brewingbytes.gemmory.policy.WheelPrize
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The 1.0.7 ad spots and the rewarded interstitial (ad-spots.md) against a fake gateway (eng D3): what each spot pays
 * and when, what leaving a fresh completion shows, and the caps the intro counts toward. No SDK, no Activity used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdSpotsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_760_000_000_000L // 2025-10-09, a Thursday morning in Bucharest
    private val clock = DayClock({ now }) { TimeZone.of("Europe/Bucharest") }
    private val entitlements = MutableStateFlow(Entitlements.FREE)
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun log(event: AnalyticsEvent) {
            events += event
        }
    }
    private val ref = CanvasRef(CanvasKind.BUNDLED, "starter")

    /** What a shown video does: watched to its reward, closed early, or failed to show. */
    private enum class Watch { EARNED, CLOSED_EARLY, FAILED }

    private class FakeGateway : AdGateway {
        val ready = MutableStateFlow<Set<AdSlot>>(emptySet())
        override val slotsReady: StateFlow<Set<AdSlot>> = ready
        val wanted = mutableSetOf<AdSlot>()
        var watch = Watch.EARNED
        var online = true
        val shown = mutableListOf<String>()
        var interstitialLoaded = true

        /** Runs while the video is up, before its reward: time passing during the watch. */
        var duringWatch: () -> Unit = {}

        override val sdkReady = true

        override fun online() = online

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded(): RewardedLoad = RewardedLoad.Loaded

        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false

        override fun preloadInterstitial() = Unit

        override fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean {
            if (!interstitialLoaded) return false
            shown += "interstitial"
            onShown()
            return true
        }

        override fun want(slot: AdSlot) {
            wanted += slot
        }

        override fun unwant(slot: AdSlot) {
            wanted -= slot
        }

        override fun showSlot(activity: Activity, slot: AdSlot, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
            if (slot !in ready.value) return false
            ready.value -= slot
            shown += slot.name
            duringWatch()
            when (watch) {
                Watch.EARNED -> {
                    onEarned()
                    onClosed(true)
                }
                Watch.CLOSED_EARLY -> onClosed(true)
                Watch.FAILED -> onClosed(false)
            }
            return true
        }
    }

    private fun TestScope.monetization(): Monetization = Monetization(
        filesDir = tmp.root,
        clock = clock,
        unlocks = PictureUnlocks(FakePrefs()),
        scope = backgroundScope,
        policy = UnconfinedTestDispatcher(testScheduler),
        reporter = {},
        entitlements = entitlements,
    )

    private fun TestScope.spots(m: Monetization, gateway: AdGateway) =
        AndroidAdSpots(m, gateway, { FAKE_ACTIVITY }, analytics, backgroundScope)

    @Test
    fun theBonusFromTheSecondFinishOncePerCanvas() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        m.onPictureFinished()
        assertFalse(spots.bonusAllowed(ref), "the first finish stays clean (DR-D3)")
        m.onPictureFinished()
        assertTrue(spots.bonusAllowed(ref))
        spots.want(Spot.BONUS)
        assertEquals(setOf(AdSlot.BONUS), gateway.wanted)

        gateway.ready.value = setOf(AdSlot.BONUS)
        gateway.watch = Watch.CLOSED_EARLY
        assertEquals(SpotOutcome.ClosedEarly, spots.playBonus(ref))
        assertEquals(0, m.inventory.value.fills, "closed early pays nothing")

        gateway.ready.value = setOf(AdSlot.BONUS)
        gateway.watch = Watch.EARNED
        assertEquals(SpotOutcome.Granted(SpotRules.BONUS_FILLS), spots.playBonus(ref))
        assertEquals(1, m.inventory.value.fills, "1 fill (DR-D17)")
        assertFalse(spots.bonusAllowed(ref), "once per canvas, ever")
        gateway.ready.value = setOf(AdSlot.BONUS)
        assertEquals(SpotOutcome.Gone, spots.playBonus(ref), "a second watch pays nothing more")
        assertEquals(1, m.inventory.value.fills)
        assertTrue(spots.takeBonusWatched(ref), "its exit blocks both interstitial formats")
        assertTrue(
            events.containsAll(
                listOf(AnalyticsEvent.rewardedOffered(Spot.BONUS.type), AnalyticsEvent.rewardedWatched(Spot.BONUS.type)),
            ),
        )
    }

    @Test
    fun aTapNeverWaitsForAVideo() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        repeat(2) { m.onPictureFinished() }
        assertEquals(SpotOutcome.NoVideo, spots.playBonus(ref), "expired at the tap: no wait (eng D4)")
        gateway.ready.value = setOf(AdSlot.BONUS)
        gateway.watch = Watch.FAILED
        assertEquals(SpotOutcome.NoVideo, spots.playBonus(ref), "failed to show")
        gateway.online = false
        assertEquals(SpotOutcome.Offline, spots.playBonus(ref))
        assertEquals(0, m.inventory.value.fills)
        assertFalse(spots.takeBonusWatched(ref), "no video seen, so the interstitial isn't blocked")
    }

    @Test
    fun todaysGiftOnceADayAfterAFirstFinish() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        assertFalse(spots.giftAllowed(), "nothing before a first finish (DR-D6)")
        m.onPictureFinished()
        assertTrue(spots.giftAllowed())
        assertTrue(spots.giftPopupDue())
        spots.giftPopupShown()
        assertFalse(spots.giftPopupDue(), "once a day, dismissed or not (DR-D5)")
        assertTrue(spots.giftAllowed(), "still in the Inventory sheet (DR-D7)")

        gateway.ready.value = setOf(AdSlot.GIFT)
        val outcome = assertIs<SpotOutcome.Granted>(spots.playGift())
        assertEquals(outcome.spin.amount, m.inventory.value.count(outcome.spin.item))
        assertFalse(spots.giftAllowed(), "opened today")

        now += 24 * 3_600_000L
        assertTrue(spots.giftAllowed(), "a new day")
        assertTrue(spots.giftPopupDue())
    }

    @Test
    fun sameAgainRepeatsTheSpinOnce() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        // A streak gift spin waiting, then spun.
        val spin = Spin(WheelPrize.PHOTO_CANVAS, Item.PHOTO_CANVAS, 1)
        gateway.ready.value = setOf(AdSlot.AGAIN)
        assertEquals(SpotOutcome.Granted(Spin(WheelPrize.PHOTO_CANVAS, Item.PHOTO_CANVAS, 1)), spots.playAgain(spin))
        gateway.ready.value = setOf(AdSlot.AGAIN)
        assertEquals(SpotOutcome.Gone, spots.playAgain(spin), "once per spin")
        assertEquals(1, m.inventory.value.canvases)
    }

    @Test
    fun vipSeesNoSpots() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val spots = spots(m, FakeGateway())
        repeat(2) { m.onPictureFinished() }
        entitlements.value = Entitlements(VipPlan.YEARLY)
        assertFalse(spots.bonusAllowed(ref))
        assertFalse(spots.giftAllowed())
    }

    @Test
    fun theRewardedInterstitialFirstThenTodaysInterstitial() = runTest(UnconfinedTestDispatcher()) {
        // A later cold start (the first session is over) and two finishes: an exit may show an ad.
        monetization()
        val m = monetization()
        val gateway = FakeGateway()
        repeat(2) { m.onPictureFinished() }
        var bonus = false
        val ads = Interstitials(m, gateway, { EncodeState.Idle }, analytics, { bonus }, log = {})
        ads.onFreshCompletion()
        assertTrue(AdSlot.REWARDED_INTERSTITIAL in gateway.wanted)

        gateway.ready.value = setOf(AdSlot.REWARDED_INTERSTITIAL)
        assertEquals(ReturnAd.INTRO, ads.onLeftFreshCompletion(FAKE_ACTIVITY, ref))
        assertTrue(gateway.shown.isEmpty(), "the shell shows the intro, nothing plays yet")
        ads.introShown()
        assertEquals(SpotRules.BONUS_FILLS, ads.playIntro(FAKE_ACTIVITY))
        assertEquals(1, m.inventory.value.fills)

        now += 9 * 60_000
        gateway.ready.value = setOf(AdSlot.REWARDED_INTERSTITIAL)
        assertEquals(ReturnAd.NONE, ads.onLeftFreshCompletion(FAKE_ACTIVITY, ref), "the intro counted toward the 10-min gap")
        now += 60_000
        gateway.ready.value = emptySet()
        assertEquals(ReturnAd.INTERSTITIAL, ads.onLeftFreshCompletion(FAKE_ACTIVITY, ref), "no rewarded interstitial: today's, under the caps")

        now += 10 * 60_000
        bonus = true
        gateway.ready.value = setOf(AdSlot.REWARDED_INTERSTITIAL)
        assertEquals(ReturnAd.NONE, ads.onLeftFreshCompletion(FAKE_ACTIVITY, ref), "a bonus watched on that screen blocks both")
    }

    @Test
    fun anIntroClosedEarlyPaysNothingAndRemoveAdsSeesNeither() = runTest(UnconfinedTestDispatcher()) {
        monetization()
        val m = monetization()
        val gateway = FakeGateway()
        repeat(2) { m.onPictureFinished() }
        val ads = Interstitials(m, gateway, { EncodeState.Idle }, analytics, log = {})
        gateway.ready.value = setOf(AdSlot.REWARDED_INTERSTITIAL)
        gateway.watch = Watch.CLOSED_EARLY
        assertEquals(null, ads.playIntro(FAKE_ACTIVITY))
        assertEquals(0, m.inventory.value.fills)

        entitlements.value = Entitlements(removeAds = true)
        gateway.wanted.clear()
        gateway.shown.clear()
        ads.onFreshCompletion()
        assertTrue(gateway.wanted.isEmpty(), "no request for a player who removed ads")
        gateway.ready.value = setOf(AdSlot.REWARDED_INTERSTITIAL)
        assertEquals(ReturnAd.NONE, ads.onLeftFreshCompletion(FAKE_ACTIVITY, ref))
        assertTrue(gateway.shown.isEmpty())
    }

    @Test
    fun theGiftDayIsTheTapDay() = runTest(UnconfinedTestDispatcher()) {
        // 23:59 in Bucharest; the video ends after midnight.
        now += (12 * 60 + 6) * 60_000L
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        m.onPictureFinished()
        val tapDay = m.today()
        gateway.ready.value = setOf(AdSlot.GIFT)
        gateway.duringWatch = { now += 2 * 60_000L }
        assertIs<SpotOutcome.Granted>(spots.playGift())
        assertTrue(SpotRules.giftId(tapDay) in m.inventory.value.rewarded, "paid as the tap's day's gift (ad-spots.md §4)")
        assertTrue(spots.giftAllowed(), "the new day's gift is still there")
    }

    @Test
    fun aGiftOrSameAgainNotWatchedPaysNothingAndStays() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        m.onPictureFinished()
        val spin = Spin(WheelPrize.FILLS_SMALL, Item.FILLS, 3)
        for (watch in listOf(Watch.CLOSED_EARLY, Watch.FAILED)) {
            gateway.watch = watch
            gateway.ready.value = setOf(AdSlot.GIFT, AdSlot.AGAIN)
            assertIs<SpotOutcome>(spots.playGift()).let { assertFalse(it is SpotOutcome.Granted, "$watch: the gift") }
            assertIs<SpotOutcome>(spots.playAgain(spin)).let { assertFalse(it is SpotOutcome.Granted, "$watch: same again") }
        }
        gateway.online = false
        assertEquals(SpotOutcome.Offline, spots.playGift())
        assertEquals(SpotOutcome.Offline, spots.playAgain(spin))
        assertEquals(0, m.inventory.value.fills + m.inventory.value.canvases + m.inventory.value.tokens)
        assertTrue(spots.giftAllowed(), "today's gift is still there")

        gateway.online = true
        gateway.watch = Watch.EARNED
        gateway.ready.value = setOf(AdSlot.AGAIN)
        assertEquals(SpotOutcome.Granted(spin), spots.playAgain(spin), "same again still pays once")
    }

    @Test
    fun removeAdsStillSeesTheSpots() = runTest(UnconfinedTestDispatcher()) {
        val m = monetization()
        val spots = spots(m, FakeGateway())
        repeat(2) { m.onPictureFinished() }
        entitlements.value = Entitlements(removeAds = true)
        assertTrue(spots.bonusAllowed(ref), "Remove ads removes the ads that play on their own, not the videos chosen")
        assertTrue(spots.giftAllowed())
    }

    @Test
    fun anOfferIsSeenOncePerMoment() = runTest(UnconfinedTestDispatcher()) {
        val spots = spots(monetization(), FakeGateway())
        val seen = { events.count { it == AnalyticsEvent.rewardedOfferSeen(Spot.GIFT.type) } }
        spots.seen(Spot.GIFT)
        spots.seen(Spot.GIFT)
        assertEquals(1, seen())
        spots.want(Spot.GIFT)
        spots.seen(Spot.GIFT)
        assertEquals(2, seen(), "a new moment after want()")
    }

    @Test
    fun aReturnWithoutTheAdReleasesTheCompletionsState() = runTest(UnconfinedTestDispatcher()) {
        monetization()
        val m = monetization()
        val gateway = FakeGateway()
        val spots = spots(m, gateway)
        repeat(2) { m.onPictureFinished() }
        val ads = Interstitials(m, gateway, { EncodeState.Idle }, analytics, spots::takeBonusWatched, log = {})
        ads.onFreshCompletion()
        gateway.ready.value = setOf(AdSlot.BONUS)
        assertIs<SpotOutcome.Granted>(spots.playBonus(ref))
        // The reminder ask took this return: nothing stays wanted, and the watched bonus is forgotten.
        ads.leftWithoutAd(ref)
        assertFalse(AdSlot.REWARDED_INTERSTITIAL in gateway.wanted)
        assertFalse(spots.takeBonusWatched(ref))
    }

    private companion object {
        val FAKE_ACTIVITY: Activity by lazy {
            sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let { f ->
                f.isAccessible = true
                (f.get(null) as sun.misc.Unsafe).allocateInstance(Activity::class.java) as Activity
            }
        }
    }
}
