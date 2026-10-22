package com.brewingbytes.gemmory.app

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.gallery.GalleryState
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.TODAY_TAG
import com.brewingbytes.gemmory.gallery.TOMORROW_TAG
import com.brewingbytes.gemmory.gallery.seedFirstFinish
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_NOT_NOW_TAG
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
import com.brewingbytes.gemmory.storage.StorageLayout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R1 for T45 (first run clear): a fresh install reaches the starter with nothing in front of it (no Today card or
 * Tomorrow line, no sheet) and no Play Games before the first gem; the first finish gives Play Games its turn, which
 * waits through the finish's return (a rotation on it included) and comes at the next gallery visit; an upgrader with
 * a finish gets it at once.
 * Debug builds run Play Games off, so its turn is read from the app, not from Play Games' own sheet.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun freshInstall() {
        clear()
        // The UMP form belongs to a real first launch (ConsentManager); in the EEA it would come first, alone.
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        // No real AdMob: an interstitial after Done would cover the gallery (lessons, #50).
        app.resetMonetizationForTest(NoAds)
        app.resetTodaysForTest()
        // An empty catalog from a fake source (#111): no live drop for the Today card or the Tomorrow line.
        app.resetRemoteForTest(FakeSource(TestCatalog()), listOf(TestCatalog.KEY))
        app.resetAchievementsForTest()
        app.playGamesStartedForTest = false
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clear()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        app.resetStreakForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetRemoteForTest()
        app.resetAchievementsForTest()
        app.playGamesStartedForTest = false
    }

    private fun clear() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.ACHIEVEMENTS).delete()
        context.getSharedPreferences("streak", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("play_games_ready").commit()
    }

    @Test
    fun aFreshInstallReachesTheStarterFirstAndPlayGamesWaitsForItsTurn() {
        launch()
        assertEquals("starter", gallery().pictures.first().ref.id, "the starter is the first Pictures tile")
        assertFalse(exists(TODAY_TAG) || exists(TOMORROW_TAG), "no Today card or Tomorrow line before the first finish (DR4)")
        assertFalse(exists(REMINDER_ASK_NOT_NOW_TAG), "no sheet over the gallery")
        assertFalse(app.playGamesStartedForTest, "no Play Games before the first gem")

        // The first finish, and the return from it: Play Games' turn has come but isn't taken there.
        compose.onNodeWithTag("tile-bundled-starter").performClick()
        compose.waitUntil("the starter's canvas", 10_000) { session()?.viewport?.isLaidOut == true }
        assertFalse(app.playGamesStartedForTest, "never over a canvas")
        instrumentation.runOnMainSync { val s = session()!!; while (!s.isFinished) s.placeNext() }
        done()
        compose.waitUntil("the turn has come", 5_000) { app.settings.playGamesReady }
        assertFalse(app.playGamesStartedForTest, "not on the return from a fresh finish")
        // A rotation (or a restored process) on that return is still the return, not a new visit.
        scenario.recreate()
        compose.waitUntil("the gallery again", 10_000) { exists("gallery-grid") }
        assertFalse(app.playGamesStartedForTest, "not after a rotation on the return from a fresh finish")
        if (exists(REMINDER_ASK_NOT_NOW_TAG)) compose.onNodeWithTag(REMINDER_ASK_NOT_NOW_TAG).performClick()

        // Now the Today card shows; it opens a canvas, and the gallery after it is Play Games' turn.
        // The return can leave the grid scrolled down to the Finished section; the card sits at its top.
        compose.waitUntil("the Today card", 10_000) {
            runCatching { compose.onNodeWithTag("gallery-grid").performScrollToIndex(0) }
            exists(TODAY_TAG)
        }
        compose.onNodeWithTag(TODAY_TAG).performClick()
        compose.waitUntil("today's canvas", 10_000) { session()?.viewport?.isLaidOut == true }
        assertFalse(app.playGamesStartedForTest, "never over a canvas")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil("Play Games' turn at the gallery", 5_000) { exists("gallery-grid") && app.playGamesStartedForTest }
    }

    /** An upgrader with a finished canvas has had Play Games before: it starts at the first gallery, as in 0.8.1. */
    @Test
    fun anUpgraderWithAFinishGetsPlayGamesAtTheFirstGallery() {
        app.seedFirstFinish()
        compose.waitUntil("the finish counted", 5_000) { app.achievements.state.finishes > 0 }
        launch()
        compose.waitUntil("Play Games' turn at the first gallery", 5_000) { app.playGamesStartedForTest }
        compose.waitUntil("the Today card", 10_000) { exists(TODAY_TAG) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil("the starter tile", 10_000) { exists("tile-bundled-starter") }
    }

    /** Done on the completion screen; a tap during its entrance can be missed, so it's tapped until the gallery shows. */
    private fun done() {
        compose.waitUntil("the completion screen", 10_000) { exists("completion-done") }
        compose.waitUntil("the gallery", 10_000) {
            if (!exists("gallery-grid") && exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            exists("gallery-grid")
        }
    }

    private fun gallery(): GalleryState {
        var state = GalleryState()
        compose.waitUntil("the gallery loaded", 10_000) {
            scenario.onActivity { state = ViewModelProvider(it)[GalleryViewModel::class.java].state.value }
            state.loaded
        }
        return state
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private object NoAds : AdGateway {
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
}
