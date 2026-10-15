package com.brewingbytes.gemmory.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Play Store phone screenshots (#8, for the store listing): 1080×1920, because Play rejects a long side more than twice
 * the short one, with a clean demo-mode status bar. Saved to /data/local/tmp/store-N-name.png. Fake billing prices.
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        assumeDeviceChecks()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        BillingBackends.fake.reset()
        runBlocking { app.billing.refresh() }
        A11yTestSupport.shell("wm size 1080x1920")
        A11yTestSupport.shell("settings put global sysui_demo_allowed 1")
        for (cmd in listOf(
            "-e command enter",
            "-e command clock -e hhmm 1000",
            "-e command battery -e level 100 -e plugged false",
            "-e command network -e wifi show -e level 4 -e fully true",
            "-e command network -e mobile hide",
            "-e command notifications -e visible false",
        )) {
            A11yTestSupport.shell("am broadcast -a com.android.systemui.demo $cmd")
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        A11yTestSupport.shell("am broadcast -a com.android.systemui.demo -e command exit")
        A11yTestSupport.shell("wm size reset")
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun shot(name: String) {
        SystemClock.sleep(1_500)
        compose.waitForIdle()
        A11yTestSupport.shell("screencap -p /data/local/tmp/store-$name.png")
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    @Test
    fun capture() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-bundled-starter").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        val colors = s.pattern.palette.size
        // About half the colors done, the way a player fills a picture.
        instrumentation.runOnMainSync {
            for (slot in 0 until colors / 2) {
                s.select(slot)
                while (s.remaining(slot) > 0) s.placeNext()
            }
            s.select(colors / 2)
            repeat(s.remaining(colors / 2) / 2) { s.placeNext() }
        }
        shot("2-canvas")
        instrumentation.runOnMainSync { s.viewport.set(24f, s.pattern.width * 0.55f, s.pattern.height * 0.45f) }
        shot("3-canvas-zoom")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        shot("1-gallery")

        compose.onNodeWithTag("tile-bundled-starter").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val again = session()!!
        instrumentation.runOnMainSync { while (!again.isFinished) again.placeNext() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("completion-fresh")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        shot("4-completion")

        compose.onNodeWithTag("completion-done").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-membership").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("plan-yearly")).fetchSemanticsNodes().isNotEmpty() }
        shot("5-paywall")
    }
}
