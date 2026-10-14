package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasGesturesTest {
    // 3×2, slots: 0 0 1 / 2 1 0.
    private val pattern = Pattern(
        width = 3,
        height = 2,
        palette = List(3) { PaletteColor(it + 1, 0xFF000000.toInt() or it, Lab(it * 10.0, 0.0, 0.0)) },
        cells = intArrayOf(0, 0, 1, 2, 1, 0),
    )

    @Test
    fun tapZoomsBelowPlacementZoomAndPlacesFromIt() {
        assertEquals(TapAction.ZOOM_IN, CanvasGestures.tapAction(15.9f, cell = 0))
        assertEquals(TapAction.PLACE, CanvasGestures.tapAction(16f, cell = 0))
        assertEquals(TapAction.NONE, CanvasGestures.tapAction(30f, cell = null))
        assertEquals(TapAction.NONE, CanvasGestures.tapAction(4f, cell = null))
    }

    @Test
    fun theStartCellDecidesPaintOrPan() {
        val state = CanvasState.empty(pattern)
        assertTrue(CanvasGestures.startsPaint(16f, cell = 0, state = state, selectedSlot = 0))
        assertFalse(CanvasGestures.startsPaint(16f, cell = 2, state = state, selectedSlot = 0), "other color pans")
        assertFalse(CanvasGestures.startsPaint(15.9f, cell = 0, state = state, selectedSlot = 0), "under 16dp pans")
        assertFalse(CanvasGestures.startsPaint(20f, cell = null, state = state, selectedSlot = 0), "outside pans")
        state.place(0, 0)
        assertFalse(CanvasGestures.startsPaint(20f, cell = 0, state = state, selectedSlot = 0), "placed gem pans")
    }

    @Test
    fun doubleTapGoesToTwentyFourOrBackToFit() {
        assertEquals(24f, CanvasZoom.doubleTapTarget(zoomDp = 4f, fitDp = 4f))
        assertEquals(4f, CanvasZoom.doubleTapTarget(zoomDp = 24f, fitDp = 4f))
        assertEquals(4f, CanvasZoom.doubleTapTarget(zoomDp = 48f, fitDp = 4f))
        // A small canvas whose fit is already past 24dp: double-tap never zooms out past fit.
        assertEquals(30f, CanvasZoom.doubleTapTarget(zoomDp = 30f, fitDp = 30f))
    }

    @Test
    fun cellsAlongAHorizontalPath() {
        assertContentEquals(intArrayOf(0, 1, 2), CanvasGestures.cellsAlong(0.5f, 0.5f, 2.5f, 0.5f, 3, 2))
        assertContentEquals(intArrayOf(5, 4, 3), CanvasGestures.cellsAlong(2.9f, 1.2f, 0.1f, 1.8f, 3, 2))
    }

    @Test
    fun cellsAlongAFastDiagonalSkipNothing() {
        // From cell 0 to cell 5 in one move event: every cell the line crosses is listed, in order.
        val cells = CanvasGestures.cellsAlong(0.2f, 0.4f, 2.8f, 1.6f, 3, 2)
        assertEquals(0, cells.first())
        assertEquals(5, cells.last())
        assertEquals(cells.toSet().size, cells.size)
        for (i in 1 until cells.size) {
            val a = cells[i - 1]
            val b = cells[i]
            val step = kotlin.math.abs(a % 3 - b % 3) + kotlin.math.abs(a / 3 - b / 3)
            assertEquals(1, step, "cells $a → $b are neighbours")
        }
    }

    @Test
    fun cellsAlongStaysInsideTheGrid() {
        assertContentEquals(intArrayOf(0, 1, 2), CanvasGestures.cellsAlong(-1.5f, 0.5f, 4.5f, 0.5f, 3, 2))
        assertContentEquals(intArrayOf(), CanvasGestures.cellsAlong(-3f, -3f, -1f, -2f, 3, 2))
        assertContentEquals(intArrayOf(4), CanvasGestures.cellsAlong(1.3f, 1.3f, 1.6f, 1.9f, 3, 2))
    }

    @Test
    fun aNonPlacingTapThenAQuickSecondTapTogglesZoom() {
        val chain = TapChain(timeoutMillis = 300, slopPx = 40f)
        chain.first(upAt = 1_000, x = 100f, y = 100f, outcome = TapOutcome.OTHER)
        assertEquals(DoubleTap.TOGGLE_ZOOM, chain.second(downAt = 1_200, x = 110f, y = 95f, wouldPlace = false))
        assertNull(chain.second(downAt = 1_250, x = 110f, y = 95f, wouldPlace = false), "a double-tap consumes the chain")
    }

    @Test
    fun placingTapsNeverMakeADoubleTap() {
        val chain = TapChain(timeoutMillis = 300, slopPx = 40f)
        chain.first(upAt = 1_000, x = 100f, y = 100f, outcome = TapOutcome.PLACED)
        assertNull(chain.second(downAt = 1_100, x = 100f, y = 100f, wouldPlace = false))
    }

    @Test
    fun aQuickCorrectiveTapAfterAMissPlacesInsteadOfZooming() {
        val chain = TapChain(timeoutMillis = 300, slopPx = 40f)
        // A wrong-color tap, then a quick tap one cell over on the selected color: a single tap, so it places.
        chain.first(upAt = 1_000, x = 100f, y = 100f, outcome = TapOutcome.OTHER)
        assertNull(chain.second(downAt = 1_150, x = 130f, y = 100f, wouldPlace = true))
        assertNull(chain.second(downAt = 1_200, x = 130f, y = 100f, wouldPlace = false), "the chain was consumed")
    }

    @Test
    fun aSecondTapAfterTapToZoomIsSwallowed() {
        val chain = TapChain(timeoutMillis = 300, slopPx = 40f)
        chain.first(upAt = 1_000, x = 100f, y = 100f, outcome = TapOutcome.ZOOMED_IN)
        assertEquals(DoubleTap.SWALLOW, chain.second(downAt = 1_200, x = 100f, y = 100f, wouldPlace = true))
    }

    @Test
    fun tooLateOrTooFarIsASingleTap() {
        val chain = TapChain(timeoutMillis = 300, slopPx = 40f)
        chain.first(upAt = 1_000, x = 100f, y = 100f, outcome = TapOutcome.OTHER)
        assertNull(chain.second(downAt = 1_301, x = 100f, y = 100f, wouldPlace = false))
        chain.first(upAt = 2_000, x = 100f, y = 100f, outcome = TapOutcome.OTHER)
        assertNull(chain.second(downAt = 2_100, x = 160f, y = 100f, wouldPlace = false))
        chain.first(upAt = 3_000, x = 100f, y = 100f, outcome = TapOutcome.OTHER)
        chain.clear()
        assertNull(chain.second(downAt = 3_100, x = 100f, y = 100f, wouldPlace = false))
    }
}
