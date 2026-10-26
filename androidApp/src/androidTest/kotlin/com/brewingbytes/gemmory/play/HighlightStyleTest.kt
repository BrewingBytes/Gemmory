package com.brewingbytes.gemmory.play

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.settings.HIGHLIGHT_TAG
import com.brewingbytes.gemmory.settings.optionTag
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Selected color setting (#19): picked in Settings, applied at once on an open canvas, kept across a restart. The
 * canvas's TalkBack node doesn't change with the style. Placed gems are table cuts and unplaced cells keep a seam (T40).
 */
@RunWith(AndroidJUnit4::class)
class HighlightStyleTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as GemmoryApp
    private val ref = CanvasRef(CanvasKind.PHOTO, "highlight")
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        setStyle(HighlightStyle.TINT)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        setStyle(HighlightStyle.TINT)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    /** The same call the sheet's rows make. */
    private fun setStyle(style: HighlightStyle) = instrumentation.runOnMainSync { app.settings.setHighlight(style) }

    private fun waitForTag(tag: String) =
        compose.waitUntil("tag $tag", 10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun pickedInSettingsAndKeptAcrossARestart() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tile-bundled-starter")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForTag(HIGHLIGHT_TAG)
        compose.onNodeWithTag(HIGHLIGHT_TAG).assert(hasText("Stronger tint"))
        compose.onNodeWithTag(HIGHLIGHT_TAG).performClick()
        waitForTag(optionTag(HighlightStyle.DIM))
        compose.onNodeWithTag(optionTag(HighlightStyle.TINT)).assertIsSelected()
        compose.onNodeWithTag(optionTag(HighlightStyle.DIM)).assertIsNotSelected()

        compose.onNodeWithTag(optionTag(HighlightStyle.DIM)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(optionTag(HighlightStyle.DIM))).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(HIGHLIGHT_TAG).assert(hasText("Dim the rest"))
        assertEquals(HighlightStyle.DIM, app.settings.highlight.value)

        // Written to disk, so a new process reads it back.
        val file = File(context.applicationInfo.dataDir, "shared_prefs/settings.xml")
        compose.waitUntil("prefs on disk", 5_000) { file.exists() && file.readText().contains("\"highlight\">dim<") }
        scenario.recreate()
        waitForTag(HIGHLIGHT_TAG)
        compose.onNodeWithTag(HIGHLIGHT_TAG).assert(hasText("Dim the rest"))
        compose.onNodeWithTag(HIGHLIGHT_TAG).performClick()
        waitForTag(optionTag(HighlightStyle.DIM))
        compose.onNodeWithTag(optionTag(HighlightStyle.DIM)).assertIsSelected()
        compose.onNodeWithText("Outline").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(optionTag(HighlightStyle.OUTLINE))).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(HIGHLIGHT_TAG).assert(hasText("Outline"))
    }

    @Test
    fun anOpenCanvasFollowsTheStyleAtOnce() {
        // 106×142 like a Big canvas: fit zoom is under 8dp, so it draws the bake.
        val pattern = A11yTestSupport.pattern(106, 142, 3)
        runBlocking { app.store.create(ref, pattern) }
        val colors = CellColors(pattern)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        waitForTag("tile-photo-highlight")
        compose.onNodeWithTag("tile-photo-highlight").performClick()
        var session: PlaySession? = null
        compose.waitUntil(10_000) {
            scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
            session?.viewport?.isLaidOut == true
        }
        val s = session!!
        assertTrue(s.viewport.zoomDp < 8f, "fit zoom ${s.viewport.zoomDp}")
        val selected = s.selectedSlot
        val other = (selected + 1) % 3
        // Two unplaced neighbours in the middle row: one of the selected color, one of another.
        val row = 71
        val selectedCell = (row * 106 until row * 106 + 106).first { pattern.cells[it] == selected }
        val otherCell = selectedCell + 1 // its right-hand neighbour, so both stay on screen when zoomed in
        assertEquals(other, pattern.cells[otherCell])
        val description = description()

        fun pixel(cell: Int, fx: Float = 0.5f, fy: Float = 0.5f): Int {
            compose.waitForIdle()
            val image = compose.onNodeWithTag("play-canvas").captureToImage().asAndroidBitmap()
            val v = s.viewport
            val x = v.offsetX + (cell % pattern.width + fx) * v.cellPx
            val y = v.offsetY + (cell / pattern.width + fy) * v.cellPx
            return image.getPixel(x.toInt(), y.toInt())
        }

        // Fit zoom (the bake).
        assertColor(colors.selectedTint[selected], pixel(selectedCell), "tint: selected")
        assertColor(colors.tint[other], pixel(otherCell), "tint: other")
        setStyle(HighlightStyle.DIM)
        assertColor(colors.tint[selected], pixel(selectedCell), "dim: selected keeps the pale tint")
        assertColor(colors.dimmed[other], pixel(otherCell), "dim: other dims")
        setStyle(HighlightStyle.OUTLINE)
        assertColor(colors.selectedTint[selected], pixel(selectedCell), "outline below 8dp: stronger tint")
        assertColor(colors.tint[other], pixel(otherCell), "outline: other")

        // 24dp (sprites): the ring on the selected cell, its inside the pale tint.
        instrumentation.runOnMainSync {
            s.viewport.set(24f, (selectedCell % 106) + 0.5f, row + 0.5f)
        }
        val ringAt = (0.6f + 0.75f) / 24f
        assertColor(CellColors.ON_BONE, pixel(selectedCell, ringAt, 0.5f), "outline: on-bone outer ring", tolerance = 40)
        assertColor(colors.tint[selected], pixel(selectedCell, 0.25f, 0.25f), "outline: pale inside the ring")
        setStyle(HighlightStyle.TINT)
        assertColor(colors.selectedTint[selected], pixel(selectedCell, ringAt, 0.5f), "tint: no ring")
        setStyle(HighlightStyle.DIM)
        assertColor(colors.dimmed[other], pixel(otherCell, 0.25f, 0.25f), "dim at 24dp")
        assertEquals(description, description(), "the TalkBack node doesn't change with the style")

        // T40: a placed gem at 24dp is a table cut: the table in the exact color, a lit top bevel, a shaded bottom
        // bevel; an unplaced cell keeps a darker seam on its bottom edge.
        setStyle(HighlightStyle.TINT)
        val gemCell = selectedCell + 3
        assertEquals(selected, pattern.cells[gemCell])
        instrumentation.runOnMainSync { s.place(gemCell) }
        Thread.sleep(400) // past the 180ms placement glint
        assertColor(colors.gem[selected], pixel(gemCell), "table: the exact color")
        assertTrue(luma(pixel(gemCell, 0.5f, 0.08f)) > luma(colors.gem[selected]) + 8, "top bevel is lit")
        assertTrue(luma(pixel(gemCell, 0.5f, 0.92f)) < luma(colors.gem[selected]) - 8, "bottom bevel is shaded")
        assertTrue(luma(pixel(gemCell, 0.92f, 0.5f)) < luma(colors.gem[selected]), "right bevel is shaded")
        assertTrue(luma(pixel(otherCell, 0.25f, 0.99f)) < luma(colors.tint[other]), "unplaced bottom seam is darker")
        assertColor(colors.tint[other], pixel(otherCell, 0.25f, 0.25f), "unplaced face stays flat")
    }

    private fun description(): String =
        compose.onNodeWithTag("play-canvas").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    private fun luma(argb: Int): Int = (((argb shr 16) and 0xFF) * 299 + ((argb shr 8) and 0xFF) * 587 + (argb and 0xFF) * 114) / 1000

    private fun assertColor(expected: Int, actual: Int, what: String, tolerance: Int = 3) {
        val close = listOf(16, 8, 0).all { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) <= tolerance }
        assertTrue(close, "$what: expected #%06X, got #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF))
    }
}
