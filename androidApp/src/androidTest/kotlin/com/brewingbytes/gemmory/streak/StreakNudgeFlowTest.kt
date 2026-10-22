package com.brewingbytes.gemmory.streak

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
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.gallery.STREAK_NUDGE_TAG
import com.brewingbytes.gemmory.gallery.STREAK_TAG
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
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
 * The keep-it-alive nudge (#102, G2-G4) through the real app: a 6-day streak with nothing finished today shows the line
 * on the Pictures tab, and its tap opens the target's canvas (a Quick picture, then the canvas closest to done); a
 * finish today takes it away; a broken streak in its repair window shows the chip's "0 ›" and the broken line, which
 * opens the streak screen; the kill switch (O5) hides it. Today's picture is off, so the nudge has its own card.
 */
@RunWith(AndroidJUnit4::class)
class StreakNudgeFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val streakPrefs get() = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val today = LocalDate.now().toEpochDay()
    private val photo = CanvasRef(CanvasKind.PHOTO, "nudge")
    private val billing = BillingBackends.fake

    @Before
    fun setUp() {
        assertTrue(BuildConfig.FAKE_BILLING, "connected tests run on the fake billing backend")
        assertTrue(BuildConfig.STREAK_CELEBRATION, "the kill-switch flag is on in debug")
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetTodaysForTest(enabled = false)
        app.resetCollectionsForTest()
        app.resetReviewsForTest({}, finishes = { 0 })
        ReminderPrefs(context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE)).ask = ReminderAskState.DONE
        app.resetRemindersForTest()
        app.streakNudge = true
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        app.streakNudge = true
        app.resetStreakForTest()
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

    /** 6 days through yesterday, the day off used this week: it ends at midnight unless a picture is finished today. */
    private fun seedAlive() {
        streakPrefs.edit().clear().putInt("length", 6).putLong("lastDay", today - 1).putLong("restDay", today - 3).commit()
        app.resetStreakForTest(celebrations = false)
    }

    /** 6 days through 3 days ago, the day off used last week: it ended the day after, and can still be repaired. */
    private fun seedBroken() {
        streakPrefs.edit().clear().putInt("length", 6).putLong("lastDay", today - 3).putLong("restDay", today - 6).commit()
        app.resetStreakForTest(celebrations = false)
    }

    private fun launch() {
        runBlocking { app.store.create(photo, A11yTestSupport.pattern(8, 6, 3)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("the gallery") { shows("My Photos") }
    }

    /** The gallery's first load reads every canvas; a busy emulator can take a while. */
    private fun waitNudge() = waitFor("the nudge", 60_000) { exists(STREAK_NUDGE_TAG) }

    @Test
    fun theLineOpensAQuickPictureWhenNothingIsStarted() {
        seedAlive()
        launch()
        waitNudge()
        val quick = runBlocking { app.quickOpenable(app.store.reminderCanvases()) }.first()
        assertTrue(nudgeReads("Finish a picture today to keep your 6 days"))
        assertTrue(nudgeReads("is Quick"))
        compose.onNodeWithTag(STREAK_NUDGE_TAG).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        assertEquals(quick, session()!!.ref.id)
    }

    @Test
    fun theLinePointsAtTheCanvasClosestToDoneAndGoesAfterAFinish() {
        seedAlive()
        launch()
        // Some gems on the photo canvas: it's now the closest to done.
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photos", 60_000) { exists(tileTag(photo)) }
        compose.onNodeWithTag(tileTag(photo)).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        instrumentation.runOnMainSync { repeat(10) { session()!!.placeNext() } }
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("the gallery") { shows("Pictures") }
        compose.onNode(hasText("Pictures")).performClick()
        waitFor("the canvas hint", 60_000) { nudgeReads("Your photo canvas is") }
        compose.onNodeWithTag(STREAK_NUDGE_TAG).performClick()
        waitFor("the photo canvas") { session()?.ref == photo }
        // Finish it: the streak's day is done and the nudge goes.
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        waitFor("the completion screen") { exists("completion-done") }
        waitFor("leaving the completion") {
            if (exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            !exists("completion-done")
        }
        waitFor("the gallery") { exists(STREAK_TAG) }
        compose.onNode(hasText("Pictures")).performClick()
        waitFor("the day done") { app.streak.state().lastDay == today }
        compose.waitForIdle()
        assertFalse(exists(STREAK_NUDGE_TAG), "no nudge once a picture is finished today")
    }

    @Test
    fun aBrokenStreakShowsZeroAndOpensTheStreakScreen() {
        seedBroken()
        launch()
        waitNudge()
        assertTrue(nudgeReads("Your 6 days can still be restored"))
        assertTrue(nudgeReads("See your streak"))
        waitFor("the chip") { exists(STREAK_TAG) }
        assertTrue(
            compose.onAllNodes(hasTestTag(STREAK_TAG) and hasContentDescription("your 6 days can still be restored", substring = true))
                .fetchSemanticsNodes().isNotEmpty(),
            "the chip reads the broken streak",
        )
        compose.onNodeWithTag(STREAK_NUDGE_TAG).performClick()
        waitFor("the repair panel") { exists(STREAK_REPAIR_TAG) }
    }

    /**
     * The nudge's target is read at the gallery's load only while it can show: a streak that comes back with the
     * gallery open (here VIP's freeze, confirmed after the load) brings the line in without leaving the gallery.
     */
    @Test
    fun aStreakThatComesBackWithTheGalleryOpenShowsTheLine() {
        // 6 days through 2 days ago, the day off used this week: yesterday breaks it without VIP.
        streakPrefs.edit().clear().putInt("length", 6).putLong("lastDay", today - 2).putLong("restDay", today - 4).commit()
        app.resetStreakForTest(celebrations = false)
        launch()
        waitNudge()
        assertTrue(nudgeReads("Your 6 days can still be restored"))
        try {
            billing.own(Products.VIP_YEARLY)
            runBlocking { app.billing.refresh() }
            waitFor("the line", 30_000) { nudgeReads("Finish a picture today to keep your 6 days") }
            assertTrue(nudgeReads("Yesterday is covered when you finish"))
        } finally {
            billing.reset()
            runBlocking { app.billing.refresh() }
        }
    }

    @Test
    fun theKillSwitchHidesIt() {
        app.streakNudge = false
        seedAlive()
        launch()
        waitFor("the chip") { exists(STREAK_TAG) }
        compose.waitForIdle()
        assertFalse(exists(STREAK_NUDGE_TAG))
    }

    /** The nudge is one TalkBack node: its lines are in its description, not its text. */
    private fun nudgeReads(text: String) =
        compose.onAllNodes(hasTestTag(STREAK_NUDGE_TAG) and hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** On a timeout, the screen is saved to /data/local/tmp/nudge-<what>.png for a look at what showed instead. */
    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/nudge-${what.replace(' ', '-')}.png")
        throw e
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }
}
