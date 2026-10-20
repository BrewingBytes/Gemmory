package com.brewingbytes.gemmory.app

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.gallery.openFinishedTile
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device walkthrough for #14 (window visible): large screens (D22) and 200% font on a 360dp phone (D20). Each display
 * is set with wm size / density, and every screen is captured to /data/local/tmp/walk-<name>.png for review. The
 * canvas's zoom, center and selected color must survive every size change (rotation and fold, D22).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class ScreenSizesWalkthrough {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    private data class Display(val name: String, val width: Int, val height: Int, val density: Int)

    private val phone = Display("phone", 1080, 2400, 420)
    private val narrow = Display("phone360", 1080, 2400, 480)
    private val tablets = listOf(
        Display("tablet-portrait", 1600, 2560, 320),
        Display("tablet-landscape", 2560, 1600, 320),
        Display("fold-open-portrait", 1840, 2208, 420),
        Display("fold-open-landscape", 2208, 1840, 420),
        Display("fold-closed", 1080, 2092, 420),
    )

    /** No real AdMob: an interstitial after Done would cover the screens being captured. */
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

    @Before
    fun setUp() {
        assumeDeviceChecks()
        app.resetMonetizationForTest(NoAds)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        runBlocking {
            // Today's free photo canvas is used, so the gallery shows its allowance note.
            app.monetization.consume("walkthrough")
            for (i in 1..5) app.store.create(CanvasRef(CanvasKind.PHOTO, "walk-$i"), A11yTestSupport.pattern(30 + i * 4, 40, 6))
            // Too big to fit at 24dp on any of these screens, so the restored center is never clamped.
            app.store.create(CanvasRef(CanvasKind.PHOTO, "walk-big"), A11yTestSupport.pattern(160, 160, 6))
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        app.resetMonetizationForTest()
        A11yTestSupport.shell("wm size reset")
        A11yTestSupport.shell("wm density reset")
        A11yTestSupport.shell("settings put system font_scale 1.0")
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun display(d: Display) {
        A11yTestSupport.shell("wm size ${d.width}x${d.height}")
        A11yTestSupport.shell("wm density ${d.density}")
        settle()
    }

    private fun settle() {
        SystemClock.sleep(2_500)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        settle()
        A11yTestSupport.shell("screencap -p /data/local/tmp/walk-$name.png")
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun gallery(name: String) {
        compose.onNodeWithText("My Photos").performClick()
        shot("$name-gallery-photos")
        compose.onNodeWithText("Pictures").performClick()
        compose.onNodeWithTag("tile-bundled-starter").performCustomAccessibilityActionWithLabel("Start over")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Start this picture over?")).fetchSemanticsNodes().isNotEmpty() }
        shot("$name-sheet")
        compose.onNodeWithText("Keep").performClick()
    }

    @Test
    fun largeScreensKeepTheirLayoutsAndTheCanvasKeepsItsState() {
        display(phone)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        for (d in tablets) {
            display(d)
            gallery(d.name)
        }
        // The paywall column stays at most 480dp wide (D22).
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-membership").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("plan-yearly")).fetchSemanticsNodes().isNotEmpty() }
        for (d in tablets) {
            display(d)
            shot("${d.name}-paywall")
        }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        // The canvas: zoom, center and selection survive every rotation and fold.
        display(phone)
        compose.onNodeWithText("My Photos").performClick()
        compose.onNodeWithTag("tile-photo-walk-big").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync {
            s.select(2)
            s.viewport.set(24f, 80.5f, 70.5f)
        }
        for (d in tablets + phone) {
            display(d)
            val now = session()!!
            assertEquals(2, now.selectedSlot, d.name)
            assertEquals(24f, now.viewport.zoomDp, 0.01f, d.name)
            assertEquals(80.5f, now.viewport.centerX, 0.01f, d.name)
            assertEquals(70.5f, now.viewport.centerY, 0.01f, d.name)
            shot("${d.name}-canvas")
        }
    }

    @Test
    fun twoHundredPercentFontOnA360dpPhone() {
        display(narrow)
        A11yTestSupport.shell("settings put system font_scale 2.0")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        shot("font2-gallery")
        gallery("font2")
        compose.onNodeWithTag("tile-bundled-starter").performCustomAccessibilityActionWithLabel("Start over")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Start this picture over?")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("My Photos").performClick()
        compose.onNodeWithTag("tile-photo-walk-1").performCustomAccessibilityActionWithLabel("Rename")
        shot("font2-rename")
        compose.onNodeWithText("Keep").performClick()

        compose.onNodeWithContentDescription("Settings").performClick()
        shot("font2-settings")
        compose.onNodeWithTag("settings-membership").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("plan-yearly")).fetchSemanticsNodes().isNotEmpty() }
        shot("font2-paywall")
        compose.onNodeWithTag("paywall-fine-print").performScrollTo()
        shot("font2-paywall-bottom")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("settings-delete-all").performClick()
        shot("font2-delete-all")
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("My Photos").performClick()
        compose.onNodeWithTag("tile-photo-walk-1").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (s.remaining(s.selectedSlot) > 15) s.placeNext() }
        shot("font2-canvas")
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("completion-fresh")).fetchSemanticsNodes().isNotEmpty() }
        // The picture loads in real time; the lights-down sequence starts only then.
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        shot("font2-completion")
        compose.onNodeWithTag("completion-done").performClick()
        compose.onNodeWithText("My Photos").performClick()
        // #89: the finished canvas sits in My Photos' Finished section.
        compose.openFinishedTile(CanvasRef(CanvasKind.PHOTO, "walk-1"))
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("completion-still")).fetchSemanticsNodes().isNotEmpty() }
        shot("font2-completion-still")
        compose.onNodeWithText("Play again").performClick()
        shot("font2-play-again")
    }
}
