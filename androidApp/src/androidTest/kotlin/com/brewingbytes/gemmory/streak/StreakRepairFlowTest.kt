package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.copy.strings
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.gallery.STREAK_TAG
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.review.ReviewPrefs
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The streak repair (#102) through the real app, with a fake ad SDK: a 6-day streak ended two days ago (the day off
 * was used last week). From the celebration's "can still be restored" button, a video brings it back; on the streak
 * screen, an online no-fill offers the grace once in 30 days, offline and a video closed early keep the offer, and VIP
 * brings it back once a week with no video, then sees the video panel.
 */
@RunWith(AndroidJUnit4::class)
class StreakRepairFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val streakPrefs get() = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
    private val billing = BillingBackends.fake
    private val ads = FakeAds()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val today = LocalDate.now().toEpochDay()
    private val photo = CanvasRef(CanvasKind.PHOTO, "repair")

    /** Rewarded videos that load, fail, or are watched to the end or closed early, as the test says. */
    private class FakeAds : AdGateway {
        var load: RewardedLoad = RewardedLoad.Loaded
        var online = true
        var earn = true
        var loads = 0
        var delayMs = 0L
        override val sdkReady = true
        override fun online() = online
        override suspend fun ensureConsent(activity: Activity) = true
        override suspend fun start() = Unit
        override fun preloadRewarded() = Unit
        override suspend fun loadRewarded(): RewardedLoad {
            kotlinx.coroutines.delay(delayMs)
            loads++
            return load
        }
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
            if (load != RewardedLoad.Loaded) return false
            Handler(Looper.getMainLooper()).post {
                if (earn) onEarned()
                onClosed(true)
            }
            return true
        }
        override fun preloadInterstitial() = Unit
        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }

    @Before
    fun setUp() {
        assertTrue(BuildConfig.FAKE_BILLING, "connected tests run on the fake billing backend")
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        setVip(false)
        app.resetMonetizationForTest(ads)
        app.resetTodaysForTest(enabled = false)
        app.resetCollectionsForTest()
        app.resetReviewsForTest({}, finishes = { 0 })
        ReminderPrefs(context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE)).ask = ReminderAskState.DONE
        app.resetRemindersForTest()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        billing.reset()
        runBlocking { app.billing.refresh() }
        app.resetStreakForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetCollectionsForTest()
        app.resetReviewsForTest()
        app.resetRemindersForTest()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
        // Today's grace for every reward type, the streak repair's among them.
        File(context.filesDir, StorageLayout.LEDGER_DAILY).delete()
        for (file in listOf("streak", ReminderPrefs.FILE, ReviewPrefs.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun setVip(vip: Boolean) {
        billing.reset()
        if (vip) billing.own(Products.VIP_YEARLY)
        runBlocking { app.billing.refresh() }
        compose.waitUntil("vip=$vip", 5_000) { app.monetization.entitlements.value.vip == vip }
    }

    /**
     * 6 days through 3 days ago, the day off used last week: the streak ended on the day after. [restarted]: a picture
     * was already finished today, so a new 1-day streak stands (the chip shows) and the old one waits in brokenLength.
     */
    private fun seed(restarted: Boolean, lastGraceRepair: Long? = null, lastVipRepair: Long? = null) {
        val edit = streakPrefs.edit().clear().putLong("restDay", today - 6)
        if (restarted) {
            edit.putInt("length", 1).putLong("lastDay", today).putInt("brokenLength", 6).putLong("brokenLastDay", today - 3)
        } else {
            edit.putInt("length", 6).putLong("lastDay", today - 3)
        }
        lastGraceRepair?.let { edit.putLong("lastGraceRepairDay", it) }
        lastVipRepair?.let { edit.putLong("lastVipRepairDay", it) }
        edit.commit()
        app.resetStreakForTest(celebrations = true)
    }

    /** [create]: false on a relaunch in the same test, where the canvas is still there. */
    private fun launch(create: Boolean = true) {
        if (create) runBlocking { app.store.create(photo, A11yTestSupport.pattern(8, 6, 3)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photos") { exists(tileTag(photo)) }
    }

    /** The restarted streak's chip opens Your streak, with the repair panel first. */
    private fun openStreakScreen() {
        waitFor("the chip") { exists(STREAK_TAG) }
        compose.onNodeWithTag(STREAK_TAG).performClick()
        waitFor("the repair panel") { exists(STREAK_REPAIR_TAG) }
        assertTrue(shows("Bring back your 6 days"))
    }

    private fun tapPrimary() = compose.onNodeWithTag(STREAK_REPAIR_PRIMARY_TAG).performClick()

    private fun waitRestored(length: Int) {
        waitFor("restored") { exists(STREAK_RESTORED_TAG) }
        assertTrue(shows("Streak back: $length days"))
        waitFor("the panel gone") { !exists(STREAK_REPAIR_TAG) }
        assertEquals(length, app.streak.current())
        assertEquals(
            listOf(today - 2, today - 1),
            app.streak.history().filter { it.kind == DayKind.REPAIRED }.map { it.day },
            "the missed days are logged as repaired",
        )
    }

    @Test
    fun aVideoFromTheCelebrationBringsItBack() {
        seed(restarted = false)
        launch()
        compose.onNodeWithTag(tileTag(photo)).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        waitFor("the completion screen") { exists("completion-done") }
        waitFor("leaving the completion") {
            if (exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            !exists("completion-done")
        }
        waitFor("the celebration") { exists(CELEBRATION_TAG) }
        assertTrue(shows("Your 6 days can still be restored"))
        compose.onNodeWithTag(CELEBRATION_SEE_TAG).performClick()
        waitFor("the repair panel") { shows("Your streak ended on ${StreakText(context.strings).weekday(today - 2)}. Watch a short video to restore it.") }
        assertTrue(shows("You can do this until ${StreakText(context.strings).weekday(today)}."))
        tapPrimary()
        // Today's finish adds on: 6 + 1.
        waitRestored(7)
    }

    /**
     * A rotation while the video loads keeps the tap (the sheet keeps its state too): the video shows and the streak
     * comes back. After the restore, a rotation keeps "Streak back" and the number without rolling it up again.
     */
    @Test
    fun aRotationKeepsTheVideoAndDoesntReplayTheRollUp() {
        ads.delayMs = 3_000
        seed(restarted = true)
        launch()
        openStreakScreen()
        tapPrimary()
        waitFor("loading") { shows("Loading video") }
        scenario.recreate()
        waitRestored(7)
        val loadsAfter = ads.loads
        assertEquals(1, loadsAfter, "the load went on through the rotation, no second one")
        var vm: StreakViewModel? = null
        scenario.onActivity { vm = ViewModelProvider(it)[StreakViewModel::class.java] }
        waitFor("rolled up") { vm!!.ui.value.restored?.rollUp == false }
        scenario.recreate()
        waitFor("the streak screen again") { exists(STREAK_RESTORED_TAG) }
        assertEquals(false, vm!!.ui.value.restored?.rollUp, "a rotation doesn't roll it up again")
        assertTrue(shows("7"))
    }

    @Test
    fun anOnlineNoFillOffersTheGraceOnceInThirtyDays() {
        ads.load = RewardedLoad.Failed(LoadFailure.NO_FILL)
        seed(restarted = true)
        launch()
        openStreakScreen()
        tapPrimary()
        waitFor("the grace") { shows("No video right now. This one's on us today.") }
        tapPrimary()
        waitRestored(7)
        assertEquals(today, streakPrefs.getLong("lastGraceRepairDay", 0))
        scenario.close()

        // Broken again 10 days after a grace repair (and on another day's ledger grace): no grace this time.
        File(context.filesDir, StorageLayout.LEDGER_DAILY).delete()
        app.resetMonetizationForTest(ads)
        seed(restarted = true, lastGraceRepair = today - 10)
        launch(create = false)
        openStreakScreen()
        tapPrimary()
        waitFor("no video") { shows("No video right now. Try again in a bit.") }
        assertTrue(exists(STREAK_REPAIR_TAG), "the offer stays")
    }

    @Test
    fun offlineKeepsTheOffer() {
        ads.online = false
        ads.load = RewardedLoad.Failed(LoadFailure.NETWORK)
        seed(restarted = true)
        launch()
        openStreakScreen()
        tapPrimary()
        waitFor("offline") { shows("You're offline. Connect to watch a video.") }
        assertEquals(1, app.streak.current(), "nothing changed")
        ads.online = true
        ads.load = RewardedLoad.Loaded
        tapPrimary() // Try again: loads and shows in one tap.
        waitRestored(7)
    }

    @Test
    fun aVideoClosedEarlyKeepsTheOffer() {
        ads.earn = false
        seed(restarted = true)
        launch()
        openStreakScreen()
        tapPrimary()
        waitFor("closed early") { exists("streak-repair-closed-early") }
        assertTrue(shows("Watch to the end to bring it back."))
        assertEquals(1, app.streak.current(), "no repair")
        ads.earn = true
        waitFor("the next video") { shows("Watch a video") }
        tapPrimary()
        waitRestored(7)
    }

    @Test
    fun vipBringsItBackOnceAWeekWithNoVideo() {
        setVip(true)
        seed(restarted = true)
        launch()
        openStreakScreen()
        assertTrue(shows("Your weekly VIP repair can bring it back."))
        val before = ads.loads
        tapPrimary()
        waitRestored(7)
        assertEquals(before, ads.loads, "no video was asked for")
        assertEquals(today, streakPrefs.getLong("lastVipRepairDay", 0))
    }

    @Test
    fun vipAfterItsWeeklyRepairSeesTheVideoPanel() {
        setVip(true)
        seed(restarted = true, lastVipRepair = today - 1)
        launch()
        openStreakScreen()
        assertTrue(shows("Watch a short video to restore it."))
        assertTrue(shows(StreakText(context.strings).vipRepairUsed(today - 1)))
        assertFalse(shows("Your weekly VIP repair can bring it back."))
        tapPrimary()
        waitRestored(7)
        assertEquals(today - 1, streakPrefs.getLong("lastVipRepairDay", 0), "a video repair leaves the week alone")
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** On a timeout, the screen is saved to /data/local/tmp/repair-<what>.png for a look at what showed instead. */
    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/repair-${what.replace(' ', '-')}.png")
        throw e
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }
}
