package com.brewingbytes.gemmory.streak

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_YES_TAG
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The streak celebration (#101) through the real app: leaving a fresh finish on a new streak day shows it in place
 * of the gallery, and the return chain (reminder ask, review, interstitial check) runs once the gallery shows, after
 * Continue or after the streak screen (G6, O4), also across a rotation (O6). A second finish the same day, or the
 * switch off, goes straight to the gallery as before (R1). A cold start shows a celebration missed under 30 minutes
 * ago (G9). The chain's run is seen through the reminder's finish count, which only the chain moves.
 */
@RunWith(AndroidJUnit4::class)
class StreakFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val streakPrefs get() = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
    private val ads = CountingAds()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val today = LocalDate.now().toEpochDay()
    private val first = CanvasRef(CanvasKind.PHOTO, "streak-a")
    private val second = CanvasRef(CanvasKind.PHOTO, "streak-b")

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
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetMonetizationForTest(ads)
        app.resetTodaysForTest(enabled = false)
        app.resetCollectionsForTest()
        // Below every review threshold: the return chain's review never comes due here.
        app.resetReviewsForTest({}, finishes = { 0 })
        // The reminder ask already answered, unless a test wants it.
        reminderPrefs().ask = ReminderAskState.DONE
        app.resetRemindersForTest()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
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
        for (file in listOf("streak", ReminderPrefs.FILE, ReviewPrefs.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun reminderPrefs() = ReminderPrefs(context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE))

    /** A streak of [length] days through yesterday ([length] 0: none), read by the app before the activity starts. */
    private fun seed(length: Int, celebrations: Boolean = true) {
        if (length > 0) streakPrefs.edit().clear().putInt("length", length).putLong("lastDay", today - 1).commit()
        app.resetStreakForTest(celebrations)
    }

    private fun launch(photos: Boolean = true) {
        runBlocking {
            app.store.create(first, A11yTestSupport.pattern(8, 6, 3))
            app.store.create(second, A11yTestSupport.pattern(8, 6, 3))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        if (!photos) return
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photos") { exists(tileTag(first)) }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** On a timeout, the screen is saved to /data/local/tmp/streak-<what>.png for a look at what showed instead. */
    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/streak-${what.replace(' ', '-')}.png")
        throw e
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    /** Finishes [ref] and taps Done until the completion screen is gone (it can miss a tap during its entrance). */
    private fun finishAndDone(ref: CanvasRef) {
        compose.onNodeWithTag(tileTag(ref)).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        waitFor("the completion screen") { exists("completion-done") }
        waitFor("leaving the completion") {
            if (exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            !exists("completion-done")
        }
        compose.waitForIdle()
    }

    /** [text] shows, as text or as the celebration's description (its words are one TalkBack node). */
    private fun shows(text: String) =
        compose.onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun continueToGallery() {
        compose.onNodeWithTag(CELEBRATION_CONTINUE_TAG).performClick()
        waitFor("the gallery") { !exists(CELEBRATION_TAG) && (exists("gallery-grid") || exists(tileTag(first))) }
        compose.waitForIdle()
    }

    /** O4: a new day's celebration comes first; the return chain waits for the gallery, then runs once. */
    @Test
    fun aNewDayIsCelebratedThenTheReturnChainRuns() {
        seed(length = 2)
        launch()
        finishAndDone(first)
        waitFor("the celebration") { exists(CELEBRATION_TAG) }
        assertTrue(shows("3 days in a row"))
        assertEquals(0, reminderPrefs().finishes, "the chain waits behind the celebration")
        continueToGallery()
        waitFor("the chain ran") { reminderPrefs().finishes == 1 }
        assertNull(app.streak.pendingCelebration(), "shown once")

        // A second finish the same day: no celebration, the plain return.
        finishAndDone(second)
        waitFor("the gallery") { exists(tileTag(first)) }
        assertFalse(exists(CELEBRATION_TAG))
        waitFor("the chain ran again") { reminderPrefs().finishes == 2 }
    }

    /** E3 / O4: day 1 is celebrated too, and the reminder ask (due on the 1st finish) follows on the gallery. */
    @Test
    fun dayOneIsCelebratedAndTheReminderAskFollows() {
        reminderPrefs().ask = ReminderAskState.NOT_ASKED
        app.resetRemindersForTest()
        seed(length = 0)
        launch()
        finishAndDone(first)
        waitFor("the celebration") { exists(CELEBRATION_TAG) }
        assertTrue(shows("You started a streak"))
        assertFalse(exists(REMINDER_ASK_YES_TAG), "never over the celebration")
        continueToGallery()
        waitFor("the reminder ask") { exists(REMINDER_ASK_YES_TAG) }
    }

    /** O6: a rotation on the celebration keeps it and the waiting chain, which runs once. */
    @Test
    fun aRotationOnTheCelebrationKeepsTheChain() {
        seed(length = 4)
        launch()
        finishAndDone(first)
        waitFor("the celebration") { exists(CELEBRATION_TAG) }
        scenario.recreate()
        waitFor("the celebration after the rotation") { exists(CELEBRATION_TAG) }
        assertTrue(shows("5 days in a row"))
        assertTrue(shows("Your 5-day gift spin is in the bag."))
        assertEquals(0, reminderPrefs().finishes)
        continueToGallery()
        waitFor("the chain ran") { reminderPrefs().finishes == 1 }
        compose.waitForIdle()
        assertEquals(1, reminderPrefs().finishes, "once")
    }

    /** G6: "See your streak" opens the streak screen; the chain runs when the gallery shows after it. */
    @Test
    fun seeYourStreakThenBackRunsTheChain() {
        seed(length = 2)
        launch()
        finishAndDone(first)
        waitFor("the celebration") { exists(CELEBRATION_TAG) }
        compose.onNodeWithTag(CELEBRATION_SEE_TAG).performClick()
        waitFor("the streak screen") { exists(STREAK_SCREEN_TAG) }
        assertTrue(shows("Done for today"))
        assertEquals(0, reminderPrefs().finishes)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("the gallery") { exists(tileTag(first)) }
        waitFor("the chain ran") { reminderPrefs().finishes == 1 }
    }

    /** O5 / R1: with the celebration switched off, Done goes straight to the gallery and the chain runs as before. */
    @Test
    fun withTheSwitchOffTheReturnIsAsBefore() {
        seed(length = 2, celebrations = false)
        launch()
        finishAndDone(first)
        waitFor("the gallery") { exists(tileTag(first)) }
        assertFalse(exists(CELEBRATION_TAG))
        waitFor("the chain ran") { reminderPrefs().finishes == 1 }
        assertEquals(3, app.streak.state().length, "the day still counts")
    }

    /** G9: a celebration the process never showed shows on the next gallery visit if the finish was under 30 min ago. */
    @Test
    fun aColdStartShowsARecentMissedCelebration() {
        pending(minutesAgo = 5)
        launch(photos = false)
        waitFor("the celebration") { exists(CELEBRATION_TAG) }
        assertTrue(shows("3 days in a row"))
        continueToGallery()
        assertNull(app.streak.pendingCelebration())
        assertEquals(0, reminderPrefs().finishes, "no return chain after a cold start")
    }

    /** G9: an older one is dropped; the chip's flame lights up instead. */
    @Test
    fun aColdStartDropsAnOldMissedCelebration() {
        pending(minutesAgo = 45)
        launch(photos = false)
        compose.waitForIdle()
        assertFalse(exists(CELEBRATION_TAG))
        waitFor("dropped") { app.streak.pendingCelebration() == null }
        assertEquals(3, app.streak.state().length)
    }

    /** A streak of 3 counted today with its celebration still pending, as a process that died on the completion leaves it. */
    private fun pending(minutesAgo: Long) {
        streakPrefs.edit().clear()
            .putInt("length", 3)
            .putLong("lastDay", today)
            .putLong("pendingDay", today)
            .putInt("pendingLength", 3)
            .putString("pendingCovered", "")
            .putLong("pendingFinishedAt", System.currentTimeMillis() - minutesAgo * 60_000)
            .putString("pendingCanvas", "PHOTO/${first.id}")
            .commit()
        app.resetStreakForTest()
    }
}
