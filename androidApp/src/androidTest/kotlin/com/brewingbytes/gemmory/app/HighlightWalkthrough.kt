package com.brewingbytes.gemmory.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.play.CellColors
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.StorageLayout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sqrt

/**
 * Device check for #19: the three Selected color styles on a dark, a cream and a skin-tone color of the bundled
 * pictures, at fit, 8, 16 and 24dp, with a third of that color placed. Paced so it can be watched on the emulator
 * window; screenshots go to /data/local/tmp/hl-<color>-<style>-<zoom>.png. Run it again with animations off for the
 * reduced-motion pass (`-e shotPrefix hlrm`).
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class HighlightWalkthrough {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val prefix = InstrumentationRegistry.getArguments().getString("shotPrefix") ?: "hl"
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        assumeDeviceChecks()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        instrumentation.runOnMainSync { app.settings.setHighlight(HighlightStyle.TINT) }
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil("tag $tag", 15_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        SystemClock.sleep(1_200)
        compose.waitForIdle()
        A11yTestSupport.shell("screencap -p /data/local/tmp/$prefix-$name.png")
    }

    private class Pick(val picture: String, val slot: Int, val label: String)

    @Test
    fun allStylesOnDarkCreamAndSkinTone() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tile-bundled-starter")
        // Choose the colors from the pictures themselves: the darkest, the palest warm neutral and the closest to a
        // light skin tone, each from the picture where it is most common.
        val picks = listOf(
            pick("dark") { r, g, b -> -luminance(r, g, b) },
            pick("cream") { r, g, b -> if (r >= g && g >= b && r - b < 60) luminance(r, g, b) else -1.0 },
            pick("skin") { r, g, b -> -sqrt(((r - 224) * (r - 224) + (g - 172) * (g - 172) + (b - 138) * (b - 138)).toDouble()) },
        )
        for (p in picks) {
            open(p.picture)
            val s = session()!!
            val argb = s.pattern.palette[p.slot].argb
            Log.i(TAG, "${p.label}: ${p.picture} color ${p.slot + 1} #%06X".format(argb and 0xFFFFFF))
            val cell = densest(s, p.slot)
            instrumentation.runOnMainSync {
                s.select(p.slot)
                repeat(s.remaining(p.slot) / 3) { s.placeNext() }
            }
            for (style in HighlightStyle.entries) {
                instrumentation.runOnMainSync { app.settings.setHighlight(style) }
                for (zoom in listOf(0f, 8f, 16f, 24f)) {
                    instrumentation.runOnMainSync {
                        s.viewport.set(zoom, cell % s.pattern.width + 0.5f, cell / s.pattern.width + 0.5f)
                    }
                    shot("${p.label}-${style.key}-${if (zoom == 0f) "fit" else zoom.toInt().toString()}")
                }
            }
            compose.onNodeWithContentDescription("Back").performClick()
            waitForTag("tile-bundled-starter")
        }
    }

    /** The Settings row and the sheet at 200% font on a 360dp-wide phone: nothing clips, every row stays tappable. */
    @Test
    fun settingsRowAndSheetAtTwoHundredPercentFontOn360dp() {
        A11yTestSupport.shell("settings put system font_scale 2.0")
        A11yTestSupport.shell("wm size 945x2100") // 360dp wide at 420dpi
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitForTag("tile-bundled-starter")
            compose.onNodeWithContentDescription("Settings").performClick()
            waitForTag("settings-highlight")
            shot("font2-settings")
            compose.onNodeWithTag("settings-highlight").performClick()
            waitForTag("highlight-dim")
            for (style in HighlightStyle.entries) {
                compose.onNodeWithTag("highlight-${style.key}").assertHeightIsAtLeast(48.dp)
            }
            shot("font2-sheet")
            compose.onNodeWithTag("highlight-dim").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("highlight-dim")).fetchSemanticsNodes().isEmpty() }
            shot("font2-settings-dim")
        } finally {
            A11yTestSupport.shell("settings put system font_scale 1.0")
            A11yTestSupport.shell("wm size reset")
        }
    }

    private fun open(picture: String) {
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("tile-bundled-$picture"))
        compose.onNodeWithTag("tile-bundled-$picture").performClick()
        compose.waitUntil(15_000) { session()?.viewport?.isLaidOut == true }
    }

    /** The color of the four pictures that scores best by [score], weighted lightly toward common colors. */
    private fun pick(label: String, score: (Int, Int, Int) -> Double): Pick {
        var best: Pick? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (picture in PICTURES) {
            open(picture)
            val s = session()!!
            val counts = IntArray(s.pattern.palette.size)
            s.pattern.cells.forEach { counts[it]++ }
            s.pattern.palette.forEachIndexed { slot, color ->
                if (counts[slot] < 40) return@forEachIndexed
                val c = color.argb
                val value = score((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) + counts[slot] / 1e6
                if (value > bestScore) {
                    bestScore = value
                    best = Pick(picture, slot, label)
                }
            }
            compose.onNodeWithContentDescription("Back").performClick()
            waitForTag("tile-bundled-starter")
        }
        return best!!
    }

    /** The cell of [slot] with the most cells of [slot] around it, so a zoomed shot shows a patch of it. */
    private fun densest(s: PlaySession, slot: Int): Int {
        val w = s.pattern.width
        val h = s.pattern.height
        var best = 0
        var bestCount = -1
        for (cell in s.pattern.cells.indices) {
            if (s.pattern.cells[cell] != slot) continue
            val x = cell % w
            val y = cell / w
            var count = 0
            for (dy in -3..3) for (dx in -3..3) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h && s.pattern.cells[ny * w + nx] == slot) count++
            }
            if (count > bestCount) {
                bestCount = count
                best = cell
            }
        }
        return best
    }

    private fun luminance(r: Int, g: Int, b: Int): Double =
        CellColors.contrast(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b, 0xFF000000.toInt())

    private companion object {
        const val TAG = "HighlightWalkthrough"
        val PICTURES = listOf("starter", "cat", "puppy", "cottage")
    }
}
