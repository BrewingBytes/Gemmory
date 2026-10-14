package com.brewingbytes.gemmory.app

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.storage.StorageLayout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * #14 "Done when": TalkBack alone finishes the starter picture. With TalkBack on, everything goes through the
 * accessibility service connection the way TalkBack acts: a click on the tile, then the canvas node's "Place next gem"
 * custom action (TalkBack's actions menu) until the completion screen. No touches and no app internals.
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class TalkBackStarterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val automation get() = A11yTestSupport.automation
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        assumeDeviceChecks()
        assumeTrue("needs TalkBack on the device", A11yTestSupport.talkBackInstalled)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        A11yTestSupport.talkBackOn()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        A11yTestSupport.talkBackOff()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    private fun find(timeoutMs: Long = 10_000, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        A11yTestSupport.waitFor(timeoutMs, "a node") {
            automation.clearCache()
            found = walk(automation.rootInActiveWindow, predicate)
            found != null
        }
        return found!!
    }

    private fun walk(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        return (0 until node.childCount).firstNotNullOfOrNull { walk(node.getChild(it), predicate) }
    }

    private fun AccessibilityNodeInfo.words() = (contentDescription ?: text)?.toString().orEmpty()

    /** What TalkBack focuses and acts on: the node itself or its nearest ancestor that has [action]. */
    private fun AccessibilityNodeInfo.actor(action: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo =
        generateSequence(this) { it.parent }.firstOrNull(action) ?: this

    @Test
    fun talkBackAloneFinishesTheStarter() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val tile = find { it.words() == "The Starry Night, 0% done" }.actor { it.isClickable }
        assertTrue(tile.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val hasPlace = { node: AccessibilityNodeInfo -> node.actionList.any { it.label == "Place next gem" } }
        var canvas = find { it.words().startsWith("Canvas. 0 of 768 placed.") }.actor(hasPlace)
        val place = canvas.actionList.first { it.label == "Place next gem" }.id
        var placed = 0
        val deadline = SystemClock.uptimeMillis() + 240_000
        while (placed < 768) {
            check(SystemClock.uptimeMillis() < deadline) { "only $placed placed" }
            if (!canvas.refresh()) canvas = find { it.words().startsWith("Canvas.") }.actor(hasPlace)
            if (canvas.performAction(place)) placed++ else SystemClock.sleep(20)
        }
        // Lights down, then the completion: "Finished" and the meta line.
        find(15_000) { it.words() == "Finished picture" }
        find(15_000) { it.words() == "Finished" }
    }
}
