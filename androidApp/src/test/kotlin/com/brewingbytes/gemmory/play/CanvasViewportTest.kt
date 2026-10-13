package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.save.ViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanvasViewportTest {
    // 100×50 cells in a 1000×1000 px viewport at density 2: fit is 10 px = 5dp per cell.
    private fun viewport(view: ViewState? = null) = CanvasViewport(100, 50).apply {
        restore(view)
        onLayout(1000, 1000, 2f)
    }

    @Test
    fun opensAtFitCentered() {
        val v = viewport()
        assertEquals(5f, v.zoomDp)
        assertEquals(0f, v.offsetX)
        assertEquals(250f, v.offsetY)
    }

    @Test
    fun zoomStaysBetweenFitAnd48dp() {
        val v = viewport()
        v.transform(500f, 500f, 0f, 0f, 100f)
        assertEquals(48f, v.zoomDp)
        v.transform(500f, 500f, 0f, 0f, 0.001f)
        assertEquals(5f, v.zoomDp)
    }

    @Test
    fun pinchKeepsThePointUnderTheFingers() {
        val v = viewport()
        v.set(20f, 50f, 25f)
        val before = v.pointAt(300f, 700f)
        v.transform(300f, 700f, 0f, 0f, 1.5f)
        val after = v.pointAt(300f, 700f)
        assertEquals(before.first, after.first, 1e-3f)
        assertEquals(before.second, after.second, 1e-3f)
    }

    @Test
    fun panIsClampedToTheCanvasEdges() {
        val v = viewport()
        v.set(20f, 50f, 25f)
        v.transform(500f, 500f, 100_000f, 100_000f, 1f)
        assertEquals(0f, v.offsetX)
        assertEquals(0f, v.offsetY)
    }

    @Test
    fun cellAtMapsViewportPositionsToCells() {
        val v = viewport()
        assertEquals(0, v.cellAt(1f, 251f))
        assertEquals(1 * 100 + 2, v.cellAt(25f, 265f))
        assertNull(v.cellAt(500f, 100f))
    }

    @Test
    fun restoresASavedView() {
        val saved = viewport().apply { set(24f, 30f, 20f) }.toViewState(slot = 4)
        val v = viewport(saved)
        assertEquals(24f, v.zoomDp)
        assertEquals(30f, v.centerX, 1e-3f)
        assertEquals(20f, v.centerY, 1e-3f)
    }

    @Test
    fun restoresTheSameSpotOnADifferentScreen() {
        // Saved in a square window, restored after process death in landscape at another density.
        val saved = viewport().apply { set(24f, 30f, 20f) }.toViewState(slot = 4)
        val v = CanvasViewport(100, 50).apply {
            restore(saved)
            onLayout(1000, 600, 2.5f)
        }
        assertEquals(24f, v.zoomDp)
        assertEquals(30f, v.centerX, 1e-3f)
        assertEquals(20f, v.centerY, 1e-3f)
    }

    @Test
    fun keepsTheCenterAcrossRotation() {
        val v = viewport()
        v.set(24f, 30f, 20f)
        v.onLayout(1000, 600, 2f)
        assertEquals(24f, v.zoomDp)
        assertEquals(30f, v.centerX, 1e-3f)
        assertEquals(20f, v.centerY, 1e-3f)
    }
}
