package com.brewingbytes.gemmory.review

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.collections.CollectionPrefs
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.remote.RemoteFeatures
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_NOT_NOW_TAG
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_YES_TAG
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The in-app review prompt (T39) through the real app, with Play replaced by a counting gateway: with
 * `features.review` off nothing is asked (R1); with it on, a due attempt asks Play once instead of the interstitial;
 * a reminder ask due on the same return comes first (D22). The player's first finish is made "yesterday" on the test
 * clock, since the first-finish day never asks.
 */
@RunWith(AndroidJUnit4::class)
class ReviewFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val catalog = TestCatalog()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val ads = CountingAds()
    private var asked = 0
    private val day = 24 * 60 * 60 * 1000L
    private var offset = -day

    /** Shows nothing, counts every interstitial the app asks for. */
    private class CountingAds : AdGateway {
        var interstitials = 0
        override val sdkReady = true
        override fun online() = true
        override suspend fun ensureConsent(activity: Activity) = true
        override suspend fun start() = Unit
        override fun preloadRewarded() = Unit
        override suspend fun loadRewarded() = RewardedLoad.Loaded
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false
        override fun preloadInterstitial() = Unit
        override fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean {
            interstitials++
            return false
        }
    }

    @Before
    fun setUp() {
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.testNowMillis = { System.currentTimeMillis() + offset }
        app.resetMonetizationForTest(ads)
        app.resetRemoteForTest(FakeSource(catalog), listOf(TestCatalog.KEY))
        app.resetTodaysForTest(enabled = false)
        app.resetCollectionsForTest()
        // Past the first threshold whatever this phone's achievements ledger holds.
        app.resetReviewsForTest({ asked++ }, finishes = { 3 })
        app.resetRemindersForTest()
    }

    @After
    fun tearDown() {
        app.resetStreakForTest()
        if (::scenario.isInitialized) scenario.close()
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        app.testNowMillis = null
        app.resetRemoteForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetCollectionsForTest()
        app.resetReviewsForTest()
        app.resetRemindersForTest()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        File(context.filesDir, StorageLayout.INVENTORY).delete()
        for (file in listOf(PictureUnlocks.FILE, TodaysPictures.FILE, CollectionPrefs.FILE, ReminderPrefs.FILE, ReviewPrefs.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun reminderPrefs() = ReminderPrefs(context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE))

    private fun reviewPrefs() = ReviewPrefs(context.getSharedPreferences(ReviewPrefs.FILE, Context.MODE_PRIVATE))

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** On a timeout, the screen is saved to /data/local/tmp/review-<what>.png for a look at what showed instead. */
    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/review-${what.replace(' ', '-')}.png")
        throw e
    }

    /** Publishes the catalog with the review switch [review], seeds a photo canvas, checks the catalog and opens the app. */
    private fun launch(review: Boolean) {
        catalog.features = RemoteFeatures(review = review)
        catalog.add("shell", locked = false)
        runBlocking { app.store.create(CanvasRef(CanvasKind.PHOTO, "warm-up"), A11yTestSupport.pattern(8, 6, 3)) }
        runBlocking { app.remote.check(force = true) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("the starter tile") { exists("tile-bundled-starter") }
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun placeAllAndDone() {
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        waitFor("the completion screen") { exists("completion-done") }
        waitFor("the gallery") {
            if (!exists("gallery-grid") && exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            exists("gallery-grid")
        }
        compose.waitForIdle()
    }

    /** The player's first finish, "yesterday": the photo, then "Not now" if the reminder ask shows; the clock moves to today. */
    private fun finishFirstYesterday() {
        val photo = CanvasRef(CanvasKind.PHOTO, "warm-up")
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photo") { exists(tileTag(photo)) }
        compose.onNodeWithTag(tileTag(photo)).performClick()
        placeAllAndDone()
        if (exists(REMINDER_ASK_YES_TAG)) {
            compose.onNodeWithTag(REMINDER_ASK_NOT_NOW_TAG).performClick()
            waitFor("the ask closed") { !exists(REMINDER_ASK_YES_TAG) }
        }
        compose.onNode(hasText("Pictures")).performClick()
        offset = 0
    }

    private fun finish(ref: CanvasRef) {
        val grid = compose.onNodeWithTag("gallery-grid")
        waitFor("${ref.id} in the grid") { runCatching { grid.performScrollToKey("${ref.kind}/${ref.id}") }.isSuccess }
        compose.onNodeWithTag(tileTag(ref)).performClick()
        placeAllAndDone()
    }

    /** R1: with the switch off, a return that would be due asks Play nothing and keeps the counter at zero. */
    @Test
    fun theSwitchOffAsksNothing() {
        launch(review = false)
        finishFirstYesterday()
        finish(CanvasRef(CanvasKind.BUNDLED, "lemons"))
        assertEquals(0, asked)
        assertEquals(0, reviewPrefs().made)
    }

    /** A due attempt asks Play once instead of the interstitial; the next return, inside 14 days, asks nothing. */
    @Test
    fun aDueAttemptAsksPlayOnceInsteadOfTheInterstitial() {
        launch(review = true)
        finishFirstYesterday()
        val before = ads.interstitials
        finish(CanvasRef(CanvasKind.BUNDLED, "lemons"))
        waitFor("Play asked") { asked == 1 }
        // The PR's screenshot (AGENTS.md): Play's own sheet can't show on a sideloaded app, so the return it lands on.
        if (InstrumentationRegistry.getArguments().getString("screenshots") == "true") {
            // After the gallery's fade-in from the completion screen.
            Thread.sleep(2_000)
            compose.waitForIdle()
            A11yTestSupport.shell("screencap -p /data/local/tmp/review-gallery-return.png")
        }
        assertEquals(before, ads.interstitials, "no interstitial on the review's return")
        assertEquals(1, reviewPrefs().made)
        finish(CanvasRef(CanvasKind.REMOTE, "shell"))
        assertEquals(1, asked, "14 days apart")
    }

    /** D22: a reminder ask due on the same return comes first; the review waits for the next eligible finish. */
    @Test
    fun theReminderAskComesFirst() {
        launch(review = true)
        finishFirstYesterday()
        // The ask's second attempt is due at the next counted finish (ReminderAsk.SECOND_AT).
        reminderPrefs().apply {
            ask = ReminderAskState.NOT_NOW_ONCE
            finishes = 2
        }
        app.resetRemindersForTest()
        finish(CanvasRef(CanvasKind.BUNDLED, "lemons"))
        waitFor("the reminder ask") { exists(REMINDER_ASK_YES_TAG) }
        assertEquals(0, asked)
        compose.onNodeWithTag(REMINDER_ASK_NOT_NOW_TAG).performClick()
        waitFor("the ask closed") { !exists(REMINDER_ASK_YES_TAG) }
        finish(CanvasRef(CanvasKind.REMOTE, "shell"))
        waitFor("Play asked") { asked == 1 }
        assertTrue(!exists(REMINDER_ASK_YES_TAG))
    }
}
