package com.brewingbytes.gemmory.streak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
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
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.review.ReviewPrefs
import com.brewingbytes.gemmory.share.StreakShareCompletedReceiver
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.timelapse.EncodeManager
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
 * The milestone share card (#104) through the real app: a 7th day's celebration offers Share (G10), which writes the
 * 1080×1920 card and opens the share sheet over the celebration; back returns to it, Continue to the gallery. The day
 * was finished on a pure red photo canvas: the card shows the flame and no red (G14). An app picked logs
 * `streak_share_completed`, never `share_completed`. An ordinary day keeps "See your streak" (R1).
 */
@RunWith(AndroidJUnit4::class)
class StreakShareFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val streakPrefs get() = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val today = LocalDate.now().toEpochDay()
    private val photo = CanvasRef(CanvasKind.PHOTO, "streak-red")
    private val card get() = File(File(context.cacheDir, EncodeManager.DIR), "streak-7.png")

    private class NoAds : AdGateway {
        override val sdkReady = true
        override fun online() = true
        override suspend fun ensureConsent(activity: Activity) = true
        override suspend fun start() = Unit
        override fun preloadRewarded() = Unit
        override suspend fun loadRewarded() = RewardedLoad.Loaded
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false
        override fun preloadInterstitial() = Unit
        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }

    @Before
    fun setUp() {
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetMonetizationForTest(NoAds())
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
        card.delete()
        for (file in listOf("streak", ReminderPrefs.FILE, ReviewPrefs.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    /** [length] days in a row through yesterday; a finish today makes it [length] + 1. */
    private fun seedAndFinish(length: Int) {
        streakPrefs.edit().clear().putInt("length", length).putLong("lastDay", today - 1).commit()
        app.resetStreakForTest()
        val red = Pattern(8, 6, listOf(PaletteColor(1, Color.RED, Lab(53.0, 80.0, 67.0))), IntArray(48))
        runBlocking { app.store.create(photo, red) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photo") { exists(tileTag(photo)) }
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
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/share-${what.replace(' ', '-')}.png")
        throw e
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    /** The window with focus, from the system: the share sheet is another app's. A back sent before it has focus goes to the celebration. */
    private fun focused() = A11yTestSupport.shell("dumpsys window").lineSequence().firstOrNull { "mCurrentFocus" in it }.orEmpty()

    private fun analyticsLog() = A11yTestSupport.shell("logcat -d -s Gemmory:I")

    @Test
    fun aMilestoneSharesTheCardThroughTheShareSheet() {
        seedAndFinish(length = 6)
        assertTrue(exists(CELEBRATION_SHARE_TAG), "a milestone offers Share")
        assertFalse(exists(CELEBRATION_SEE_TAG))

        compose.onNodeWithTag(CELEBRATION_SHARE_TAG).performClick()
        waitFor("the card") { card.isFile }
        waitFor("the share sheet", 15_000) { "intentresolver" in focused() || "ResolverActivity" in focused() }
        val png = BitmapFactory.decodeFile(card.path)
        assertEquals(1080 to 1920, png.width to png.height)
        val pixels = IntArray(png.width * png.height).also { png.getPixels(it, 0, png.width, 0, 0, png.width, png.height) }
        assertTrue(pixels.none { Color.red(it) > 200 && Color.green(it) < 40 && Color.blue(it) < 40 }, "the red photo canvas isn't on the card")

        A11yTestSupport.shell("input keyevent KEYCODE_BACK")
        waitFor("back on the celebration", 15_000) { "MainActivity" in focused() && exists(CELEBRATION_TAG) }

        // The sheet's callback when an app is picked.
        A11yTestSupport.shell("logcat -c")
        context.sendBroadcast(Intent(context, StreakShareCompletedReceiver::class.java).putExtra(StreakShareCompletedReceiver.MILESTONE, 7))
        waitFor("streak_share_completed") { "analytics streak_share_completed {milestone=7}" in analyticsLog() }
        assertFalse("analytics share_completed" in analyticsLog(), "not counted as a picture share")

        compose.onNodeWithTag(CELEBRATION_CONTINUE_TAG).performClick()
        waitFor("the gallery") { !exists(CELEBRATION_TAG) && exists(tileTag(photo)) }
    }

    /** R1: an ordinary day keeps "See your streak" and no Share. */
    @Test
    fun anOrdinaryDayHasNoShare() {
        seedAndFinish(length = 2)
        assertTrue(exists(CELEBRATION_SEE_TAG))
        assertFalse(exists(CELEBRATION_SHARE_TAG))
    }
}
