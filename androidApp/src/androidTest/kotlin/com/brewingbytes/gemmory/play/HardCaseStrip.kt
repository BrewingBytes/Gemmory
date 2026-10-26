package com.brewingbytes.gemmory.play

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
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
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Device check for T40 (#81): the hard-case strip. White, cream, light, mid and deep skin, near-black and a mid violet,
 * each placed beside its own pale tint, at 16 and 24dp under the three Selected color styles. Where the gem's face is
 * close to its tint (white, cream and skin: 1.1-1.2:1), the table cut's shaded bottom bevel is what tells them apart,
 * so the face or the bottom bevel must differ from the tint by at least 8 luma levels (under Dim the rest the tint is
 * darkened and the face itself stands apart). Screenshots go to /data/local/tmp/strip-<style>-<zoom>.png.
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class HardCaseStrip {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as GemmoryApp
    private val ref = CanvasRef(CanvasKind.PHOTO, "strip")
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

    @Test
    fun eachHardColorStandsApartFromItsTint() {
        // Column 2k is color k placed, column 2k + 1 the same color unplaced.
        val pattern = Pattern(
            width = COLORS.size * 2,
            height = ROWS,
            palette = COLORS.mapIndexed { i, argb -> PaletteColor(i + 1, argb, Lab(i * 10.0, 0.0, 0.0)) },
            cells = IntArray(COLORS.size * 2 * ROWS) { (it % (COLORS.size * 2)) / 2 },
        )
        runBlocking { app.store.create(ref, pattern) }
        val colors = CellColors(pattern)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        compose.waitUntil("photo tile", 15_000) { compose.onAllNodes(hasTestTag("tile-photo-strip")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-photo-strip").performClick()
        var session: PlaySession? = null
        compose.waitUntil("canvas", 15_000) {
            scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
            session?.viewport?.isLaidOut == true
        }
        val s = session!!
        val midX = pattern.width / 2f
        val midY = pattern.height / 2f
        instrumentation.runOnMainSync {
            s.viewport.set(24f, midX, midY)
            for (slot in COLORS.indices) {
                s.select(slot)
                for (row in 0 until ROWS) s.place(row * pattern.width + slot * 2)
            }
            // The light skin tone stays selected, so each style's look lands on a hard color.
            s.select(LIGHT_SKIN)
        }
        SystemClock.sleep(400) // past the placement glints

        val failures = mutableListOf<String>()
        for (style in HighlightStyle.entries) {
            instrumentation.runOnMainSync { app.settings.setHighlight(style) }
            for (zoom in listOf(16f, 24f)) {
                instrumentation.runOnMainSync { s.viewport.set(zoom, midX, midY) }
                SystemClock.sleep(300)
                compose.waitForIdle()
                A11yTestSupport.shell("screencap -p /data/local/tmp/strip-${style.key}-${zoom.toInt()}.png")
                val image = compose.onNodeWithTag("play-canvas").captureToImage().asAndroidBitmap()
                fun pixel(cell: Int, fx: Float, fy: Float): Int {
                    val v = s.viewport
                    return image.getPixel(
                        (v.offsetX + (cell % pattern.width + fx) * v.cellPx).toInt(),
                        (v.offsetY + (cell / pattern.width + fy) * v.cellPx).toInt(),
                    )
                }
                val row = ROWS / 2
                for (slot in COLORS.indices) {
                    val gem = row * pattern.width + slot * 2
                    val bottomBevel = luma(pixel(gem, 0.5f, 0.92f))
                    val face = luma(pixel(gem, 0.5f, 0.5f))
                    val tint = luma(pixel(gem + 1, 0.2f, 0.2f))
                    if (maxOf(abs(bottomBevel - tint), abs(face - tint)) < 8) {
                        failures += "${style.key} ${zoom.toInt()}dp #%06X: face $face, bevel $bottomBevel vs tint $tint".format(COLORS[slot] and 0xFFFFFF)
                    }
                    if (abs(luma(pixel(gem, 0.5f, 0.5f)) - luma(colors.gem[slot])) > 3) {
                        failures += "${style.key} ${zoom.toInt()}dp #%06X: table isn't the exact color".format(COLORS[slot] and 0xFFFFFF)
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun luma(argb: Int): Int = (((argb shr 16) and 0xFF) * 299 + ((argb shr 8) and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000

    private companion object {
        const val ROWS = 5
        const val LIGHT_SKIN = 2
        val COLORS = listOf(
            0xFFFFFFFF, // white
            0xFFF3EAD3, // cream
            0xFFF1C9A5, // light skin
            0xFFC68E6A, // mid skin
            0xFF7B4A35, // deep skin
            0xFF1C1A1B, // near-black
            0xFF7A5BA8, // mid violet
        ).map { it.toInt() }
    }
}
