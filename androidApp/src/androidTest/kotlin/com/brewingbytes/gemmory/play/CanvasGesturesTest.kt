package com.brewingbytes.gemmory.play

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasZoom
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The canvas gesture table (design D23), Find (D24) and the first hint (D12), with real touches on the real screen.
 * The canvas is 100×130 (fit is well under 16dp on a phone); columns come in pairs of one color, so a row reads
 * 0 0 1 1 2 2 3 3 0 0 … (column c has slot (c / 2) % 4), and color 5 (slot 4) has only 12 cells, for Find.
 */
@RunWith(AndroidJUnit4::class)
class CanvasGesturesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ref = CanvasRef(CanvasKind.PHOTO, "gestures")
    private val findCells = IntArray(12) { 100 * (30 + it * 6) + 40 + it }
    private val pattern = Pattern(
        width = 100,
        height = 130,
        palette = List(5) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x202020), Lab(it * 10.0, 0.0, 0.0)) },
        cells = IntArray(100 * 130) { (it % 100 / 2) % 4 }.also { cells -> findCells.forEach { cells[it] = 4 } },
    )
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        hintsShown(true)
        runBlocking { (context.applicationContext as GemmoryApp).store.create(ref, pattern) }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun hintsShown(shown: Boolean) {
        val edit = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        if (shown) CoachHint.entries.forEach { edit.putBoolean(it.key, true) }
        edit.commit()
    }

    private fun open(): PlaySession {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tile-photo-gestures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-photo-gestures").performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
        compose.waitForIdle()
        return session()!!
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        compose.waitForIdle()
    }

    /** Zooms to [zoomDp] with cell ([col], [row]) at the middle of the canvas. */
    private fun PlaySession.zoomTo(zoomDp: Float, col: Int = 50, row: Int = 65) =
        onMain { viewport.set(zoomDp, col + 0.5f, row + 0.5f) }

    private fun PlaySession.centerOf(cell: Int): Offset {
        val vp = viewport
        return Offset(vp.offsetX + (cell % pattern.width + 0.5f) * vp.cellPx, vp.offsetY + (cell / pattern.width + 0.5f) * vp.cellPx)
    }

    private fun canvas(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        compose.onNodeWithTag("play-canvas").performTouchInput(block)
        compose.waitForIdle()
    }

    @Test
    fun underSixteenDpATapZoomsToTwentyFourAndPlacesNothing() {
        val s = open()
        assertTrue(s.viewport.zoomDp < CanvasZoom.PLACE_MIN_DP, "fit is ${s.viewport.zoomDp}dp")
        canvas { click(s.centerOf(65 * 100 + 50)) }
        assertEquals(CanvasZoom.TAP_ZOOM_DP, s.viewport.zoomDp, 0.01f)
        assertEquals(0, s.placedCount)
    }

    @Test
    fun aDragFromAnUnplacedSelectedCellPaintsItsColorAlongThePathAndSkipsTheRest() {
        val s = open()
        s.zoomTo(24f, col = 50)
        onMain { s.select(1) }
        val row = 65
        // From column 50 (slot 1) to column 60: slot 1 is at columns 50-51 and 58-59 in that stretch.
        val from = s.centerOf(row * 100 + 50)
        val to = s.centerOf(row * 100 + 60)
        val wrongBefore = s.wrongTaps
        canvas { swipe(from, to, durationMillis = 600) }
        assertContentEquals(intArrayOf(row * 100 + 50, row * 100 + 51, row * 100 + 58, row * 100 + 59), s.log())
        assertEquals(wrongBefore, s.wrongTaps, "cells of other colors are skipped silently")
        assertEquals(50.5f, s.viewport.centerX, 0.01f, "a paint drag doesn't pan")
    }

    @Test
    fun aDragThatStartsOnAnotherColorOrAPlacedGemPans() {
        val s = open()
        s.zoomTo(24f)
        onMain { s.select(1) }
        val start = s.centerOf(65 * 100 + 50) // slot 1, placed first
        onMain { s.place(65 * 100 + 50) }
        val placed = s.placedCount
        val centerX = s.viewport.centerX
        canvas { swipe(start, start + Offset(-300f, 0f), durationMillis = 400) }
        assertNotEquals(centerX, s.viewport.centerX, "starting on a placed gem pans")
        assertEquals(placed, s.placedCount)

        val other = s.centerOf(65 * 100 + 48) // slot 0, not selected
        val centerY = s.viewport.centerY
        canvas { swipe(other, other + Offset(0f, -300f), durationMillis = 400) }
        assertNotEquals(centerY, s.viewport.centerY, "starting on another color pans")
        assertEquals(placed, s.placedCount)
    }

    @Test
    fun underSixteenDpADragPans() {
        val s = open()
        s.zoomTo(12f)
        onMain { s.select(1) }
        val centerX = s.viewport.centerX
        val start = s.centerOf(65 * 100 + 50)
        canvas { swipe(start, start + Offset(-300f, 0f), durationMillis = 400) }
        assertNotEquals(centerX, s.viewport.centerX)
        assertEquals(0, s.placedCount)
    }

    @Test
    fun twoFingersAlwaysNavigateEvenFromAPaintableCell() {
        val s = open()
        s.zoomTo(24f)
        onMain { s.select(1) }
        val a = s.centerOf(65 * 100 + 46)
        val b = s.centerOf(65 * 100 + 55)
        val zoom = s.viewport.zoomDp
        canvas { pinch(a, a - Offset(150f, 0f), b, b + Offset(150f, 0f), durationMillis = 500) }
        assertTrue(s.viewport.zoomDp > zoom, "pinch out zooms in: ${s.viewport.zoomDp}")
        assertEquals(0, s.placedCount, "two fingers never paint")
    }

    @Test
    fun doubleTapZoomsInFromFitAndBackOutWhenItsFirstTapDidntPlace() {
        val s = open()
        val fit = s.viewport.zoomDp
        val cell = 65 * 100 + 50
        // At fit the first tap already zooms to 24dp; the second one is swallowed, not a zoom back out.
        canvas { doubleClick(s.centerOf(cell)) }
        assertEquals(CanvasZoom.TAP_ZOOM_DP, s.viewport.zoomDp, 0.01f)
        // On a cell of another color (no placement), a double-tap goes back to fit.
        onMain { s.select(0) }
        canvas { doubleClick(s.centerOf(cell)) }
        assertEquals(fit, s.viewport.zoomDp, 0.01f)
        assertEquals(0, s.placedCount)
    }

    @Test
    fun twoQuickPlacingTapsPlaceTwoGemsAndDontZoom() {
        val s = open()
        s.zoomTo(24f)
        onMain { s.select(1) }
        val first = 65 * 100 + 50
        canvas { doubleClick(s.centerOf(first)) }
        // Both taps land on the same cell: the first places it, the second taps a placed gem (nothing happens).
        assertContentEquals(intArrayOf(first), s.log())
        assertEquals(24f, s.viewport.zoomDp, 0.01f)
    }

    @Test
    fun aQuickCorrectiveTapAfterAMissPlacesTheGemAndKeepsTheZoom() {
        val s = open()
        s.zoomTo(24f)
        onMain { s.select(1) }
        val miss = 65 * 100 + 49 // slot 0, next to slot 1 at column 50
        val right = 65 * 100 + 50
        val wrong = s.wrongTaps
        canvas {
            click(s.centerOf(miss))
            advanceEventTime(100)
            click(s.centerOf(right))
        }
        assertEquals(wrong + 1, s.wrongTaps)
        assertContentEquals(intArrayOf(right), s.log())
        assertEquals(24f, s.viewport.zoomDp, 0.01f)
    }

    @Test
    fun aLongPressSelectsThatCellsColor() {
        val s = open()
        s.zoomTo(24f)
        onMain { s.select(0) }
        canvas { longClick(s.centerOf(65 * 100 + 52)) } // slot 2
        assertEquals(2, s.selectedSlot)
        assertEquals(0, s.placedCount)
    }

    @Test
    fun aWrongColorTapChangesNothing() {
        val s = open()
        s.zoomTo(24f)
        onMain { s.select(1) }
        onMain { s.place(65 * 100 + 50) }
        val log = s.log()
        val wrong = s.wrongTaps
        val remaining = (0 until 5).map { s.remaining(it) }
        canvas { click(s.centerOf(65 * 100 + 48)) } // slot 0
        assertEquals(wrong + 1, s.wrongTaps)
        assertContentEquals(log, s.log())
        assertEquals(remaining, (0 until 5).map { s.remaining(it) })
        assertEquals(1, s.selectedSlot)
    }

    @Test
    fun findShowsAtTwentyLeftAndWalksTheRemainingCellsInOrder() {
        val s = open()
        onMain { s.select(0) }
        assertTrue(compose.onAllNodes(hasTestTag("find")).fetchSemanticsNodes().isEmpty(), "hidden above 20 left")
        onMain { s.select(4) }
        compose.onNodeWithTag("find").performClick()
        compose.waitForIdle()
        assertEquals(CanvasZoom.TAP_ZOOM_DP, s.viewport.zoomDp, 0.01f)
        val first = findCells[0]
        assertEquals(first % 100 + 0.5f, s.viewport.centerX, 0.01f)
        assertEquals(first / 100 + 0.5f, s.viewport.centerY, 0.01f)
        // Place the found one; the next Find goes to the next remaining cell.
        canvas { click(s.centerOf(first)) }
        assertContentEquals(intArrayOf(first), s.log())
        compose.onNodeWithTag("find").performClick()
        compose.waitForIdle()
        assertEquals(findCells[1] / 100 + 0.5f, s.viewport.centerY, 0.01f)
    }

    @Test
    fun theFirstHintShowsOnAFarOutCanvasAndPickAColorFollowsATapZoom() {
        hintsShown(false)
        val s = open()
        compose.onNode(hasText(context.getString(CoachHint.ZOOM_IN.text))).assertExists()
        canvas { click(s.centerOf(65 * 100 + 50)) }
        compose.onNode(hasText(context.getString(CoachHint.TAP_ZOOMED.text))).assertExists()
        compose.mainClock.advanceTimeBy(6_500)
        compose.waitForIdle()
        compose.onNode(hasText(context.getString(CoachHint.PICK_COLOR.text))).assertExists()
    }
}
