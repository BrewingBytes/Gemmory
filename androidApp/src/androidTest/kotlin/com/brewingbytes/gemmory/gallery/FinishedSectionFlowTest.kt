package com.brewingbytes.gemmory.gallery

import android.app.Activity
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.app.scannerValidator
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
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

/** The Finished heading's TalkBack state (#89): "collapsed" or "expanded". */
private fun SemanticsNodeInteraction.state(): String? = fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

/**
 * Opens a finished canvas's tile from its tab's Finished section (#89): scrolls to the heading once the gallery has
 * moved the canvas there, expands it when it's collapsed (the state is kept between tests), then taps the tile.
 */
fun ComposeTestRule.openFinishedTile(ref: CanvasRef) {
    val grid = onNodeWithTag("gallery-grid")
    waitUntil("the Finished heading", 10_000) { runCatching { grid.performScrollToKey(FINISHED_TAG) }.isSuccess }
    val heading = onNodeWithTag(FINISHED_TAG)
    if (heading.state() == "collapsed") heading.performClick()
    waitUntil("${ref.id} in Finished", 5_000) { runCatching { grid.performScrollToKey("${ref.kind}/${ref.id}") }.isSuccess }
    onNodeWithTag(tileTag(ref)).performClick()
}

/**
 * #89: a finished canvas leaves the main grid for the collapsed "Finished · n" section at the end of its tab, the
 * section opens the completion still, Play again brings the canvas back, and the expand state is kept per tab.
 */
@RunWith(AndroidJUnit4::class)
class FinishedSectionFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as GemmoryApp
    private val photo = CanvasRef(CanvasKind.PHOTO, "finished-flow")
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.settings.setFinishedOpen(0, false)
        app.settings.setFinishedOpen(1, false)
        // No real AdMob: an interstitial after Done would cover the gallery (lessons, #50).
        app.resetMonetizationForTest(NoAds)
    }

    @After
    fun tearDown() {
        app.resetMonetizationForTest()
        if (::scenario.isInitialized) scenario.close()
        app.settings.setFinishedOpen(0, false)
        app.settings.setFinishedOpen(1, false)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Finishes the photo canvas from My Photos and comes back to the gallery with Done. */
    private fun finishPhoto() {
        runBlocking { app.store.create(photo, A11yTestSupport.pattern(8, 6, 3)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        compose.waitUntil(5_000) { exists(tileTag(photo)) }
        assertTrue(!exists(FINISHED_TAG), "nothing finished: no heading")
        compose.onNodeWithTag(tileTag(photo)).performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { exists("completion-done") }
        compose.onNodeWithTag("completion-done").performClick()
        compose.waitUntil(5_000) { exists(FINISHED_TAG) }
    }

    @Test
    fun aFinishedCanvasMovesToTheCollapsedSectionAndBackAfterPlayAgain() {
        finishPhoto()
        val heading = compose.onNodeWithTag(FINISHED_TAG)
        heading.assert(hasContentDescription("Finished, 1 photo canvas"))
        assertEquals("collapsed", heading.state())
        assertTrue(!exists(tileTag(photo)), "the finished canvas left the main grid, and the section is collapsed")
        // My Photos keeps its "New from photo" button, not the empty state, when every canvas is finished.
        assertTrue(compose.onAllNodes(hasText("Your photos, in gems")).fetchSemanticsNodes().isEmpty())

        heading.performClick()
        compose.waitUntil(5_000) { exists(tileTag(photo)) }
        assertEquals("expanded", heading.state())
        compose.onNodeWithTag(tileTag(photo)).assert(hasContentDescription(", done", substring = true))
        compose.onNodeWithTag(tileTag(photo)).performClick()
        compose.waitUntil(5_000) { exists("completion-still") }

        compose.onNodeWithText("Play again").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Start this picture over?")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start over").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { exists(tileTag(photo)) && !exists(FINISHED_TAG) }
        compose.onNodeWithTag(tileTag(photo)).assert(hasContentDescription("0% done", substring = true))
    }

    /** The expand state is per tab and kept: a new activity shows My Photos' section still open, Pictures' closed. */
    @Test
    fun theExpandStateIsKeptPerTab() {
        finishPhoto()
        compose.onNodeWithTag(FINISHED_TAG).performClick()
        compose.waitUntil(5_000) { exists(tileTag(photo)) }
        scenario.close()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        compose.waitUntil(5_000) { exists(FINISHED_TAG) && exists(tileTag(photo)) }
        assertEquals("expanded", compose.onNodeWithTag(FINISHED_TAG).state())
        assertTrue(app.settings.finishedOpen(1))
        assertTrue(!app.settings.finishedOpen(0), "Pictures keeps its own state")
    }

    /** No real AdMob: an interstitial after Done would cover the gallery. */
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

    /** TalkBack and the scanner on the heading, collapsed and expanded, at 1× and 2× font. */
    @Test
    fun theHeadingIsOneButtonNodeAtOneAndTwoTimesFont() {
        compose.enableAccessibilityChecks(scannerValidator())
        for (scale in listOf("1.0", "2.0")) {
            A11yTestSupport.shell("settings put system font_scale $scale")
            try {
                // A relaunch per scale, so the activity reads the new font scale.
                if (::scenario.isInitialized) scenario.close()
                File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
                app.settings.setFinishedOpen(1, false)
                finishPhoto()
                val heading = compose.onNodeWithTag(FINISHED_TAG)
                val config = heading.fetchSemanticsNode().config
                assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role), "at $scale×")
                assertEquals(listOf("Finished, 1 photo canvas"), config.getOrNull(SemanticsProperties.ContentDescription))
                heading.assertHeightIsAtLeast(48.dp)
                compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
                heading.performClick()
                compose.waitUntil(5_000) { exists(tileTag(photo)) }
                compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
            } finally {
                A11yTestSupport.shell("settings put system font_scale 1.0")
            }
        }
    }
}
