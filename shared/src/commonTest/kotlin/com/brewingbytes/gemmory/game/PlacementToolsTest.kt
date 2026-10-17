package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.timelapse.FramePlan
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacementToolsTest {
    private fun pattern(width: Int, vararg rows: String): Pattern {
        val cells = rows.joinToString("").map { it - '0' }.toIntArray()
        val slots = cells.max() + 1
        return Pattern(
            width = width,
            height = rows.size,
            palette = List(slots) { PaletteColor(it + 1, 0xFF000000.toInt() or it, Lab(it * 10.0, 0.0, 0.0)) },
            cells = cells,
        )
    }

    // 5×4: a 0-region on the left that wraps round a 1 at (1,1), a separate 0 at (4,3), 2s down the right.
    private val p = pattern(
        5,
        "00012",
        "01002",
        "00112",
        "11110",
    )

    @Test
    fun brushInTheMiddleIsTheSquareNearestFirst() {
        val b = PlacementTools.brush(p, 1 * 5 + 2)
        assertContentEquals(intArrayOf(7, 2, 6, 8, 12, 1, 3, 11, 13), b.cells)
        assertTrue(b.rings.all { it == 0 })
    }

    @Test
    fun brushIsClippedAtCornersAndEdges() {
        assertContentEquals(intArrayOf(0, 1, 5, 6), PlacementTools.brush(p, 0).cells)
        assertContentEquals(intArrayOf(19, 14, 18, 13), PlacementTools.brush(p, 19).cells)
        // Right edge, middle row: no wrap onto the next row's first column.
        assertContentEquals(intArrayOf(9, 4, 8, 14, 3, 13), PlacementTools.brush(p, 9).cells)
    }

    @Test
    fun regionIsFourConnectedRingsFromTheTap() {
        val r = PlacementTools.region(p, 0, 0)
        assertContentEquals(intArrayOf(0, 1, 5, 2, 10, 7, 11, 8), r.cells)
        assertContentEquals(intArrayOf(0, 1, 1, 2, 2, 3, 3, 4), r.rings)
        // The lone 0 at the bottom-right corner is its own region; diagonals don't connect.
        assertContentEquals(intArrayOf(19), PlacementTools.region(p, 19, 0).cells)
    }

    @Test
    fun regionGoesThroughPlacedCellsOfItsColor() {
        val s = CanvasState.empty(p)
        s.place(1, 0)
        s.place(2, 0)
        val r = PlacementTools.region(p, 0, 0)
        assertContentEquals(intArrayOf(0, 1, 5, 2, 10, 7, 11, 8), r.cells)
        val batch = PlacementTools.place(s, r, 0)
        // 1 and 2 were placed already: skipped, but the region still reaches 7 and 8 through them.
        assertContentEquals(intArrayOf(0, 5, 10, 7, 11, 8), batch.placed)
        assertContentEquals(intArrayOf(0, 1, 2, 3, 3, 4), batch.rings)
    }

    @Test
    fun regionOnAnotherColorIsEmpty() {
        assertEquals(0, PlacementTools.region(p, 6, 0).cells.size)
        assertEquals(0, PlacementTools.region(p, -1, 0).cells.size)
    }

    @Test
    fun brushPlacesOnlyTheSelectedColorsUnplacedCells() {
        val s = CanvasState.empty(p)
        s.place(2, 0)
        val before = s.log()
        val batch = PlacementTools.place(s, PlacementTools.brush(p, 7), 0)
        // Around 7: 7 2 6 8 12 1 3 11 13. 2 is placed, 6/12/3/13 are color 2.
        assertContentEquals(intArrayOf(7, 8, 1, 11), batch.placed)
        assertContentEquals(before + intArrayOf(7, 8, 1, 11), s.log())
        assertFalse(s.isPlaced(6))
        assertEquals(8, s.remaining(1))
        assertEquals(4, s.remaining(0))
    }

    @Test
    fun aBatchOfOnlyWrongOrPlacedCellsChangesNothing() {
        val s = CanvasState.empty(p)
        s.place(0, 0)
        val log = s.log()
        val batch = PlacementTools.place(s, PlacementTools.brush(p, 0), 1)
        // Around 0 for slot 1: 0 (placed, 0), 1 (0), 5 (0), 6 (1!) → only 6.
        assertContentEquals(intArrayOf(6), batch.placed)
        val none = PlacementTools.place(s, ToolCells(intArrayOf(0, 1, 4), IntArray(3)), 1)
        assertEquals(0, none.placed.size)
        assertNull(none.colorFinished)
        assertFalse(none.pictureFinished)
        assertContentEquals(log + intArrayOf(6), s.log())
    }

    @Test
    fun fillColorIsEveryRemainingCellNearestFirst() {
        val s = CanvasState.empty(p)
        s.place(4, 2)
        val f = PlacementTools.fillColor(p, s::isPlaced, 2, 19)
        assertContentEquals(intArrayOf(14, 9), f.cells)
        assertContentEquals(intArrayOf(0, 1), f.rings)
    }

    @Test
    fun aBatchThatFinishesAColorEndsOnColorFinished() {
        val s = CanvasState.empty(p)
        val batch = PlacementTools.place(s, PlacementTools.fillColor(p, s::isPlaced, 2, 0), 2)
        assertContentEquals(intArrayOf(4, 9, 14), batch.placed)
        val done = assertNotNull(batch.colorFinished)
        assertEquals(2, done.slot)
        assertEquals(0, done.next)
        assertFalse(batch.pictureFinished)
        assertTrue(s.isFinished(2))
    }

    @Test
    fun aBatchThatFinishesThePicture() {
        val s = CanvasState.empty(p)
        for (cell in 0 until p.cells.size) if (p.cells[cell] != 2) s.place(cell, p.cells[cell])
        val batch = PlacementTools.place(s, PlacementTools.fillColor(p, s::isPlaced, 2, 0), 2)
        assertTrue(batch.pictureFinished)
        assertNull(assertNotNull(batch.colorFinished).next)
        assertTrue(s.isFinished)
    }

    @Test
    fun aBatchCrossingAMilestoneIsCountedOnce() {
        // 20 cells: 25% at the 5th, 50% at the 10th. 4 placed, then an 8-cell region takes it to 12.
        val s = CanvasState.empty(p)
        for (cell in intArrayOf(3, 6, 12, 13)) s.place(cell, 1)
        val before = s.placedCount
        assertEquals(4, before)
        PlacementTools.place(s, PlacementTools.region(p, 0, 0), 0)
        assertEquals(12, s.placedCount)
        // It crosses 25% and 50%: the higher plays, once.
        assertEquals(50, Milestones.crossed(before, s.placedCount, s.cellCount))
    }

    @Test
    fun theLogAndTheTimeLapseReplayARegionAsARipple() {
        // A 9×9 single color: a fill from the middle must reach the log ring by ring, and every time-lapse build frame
        // shows a prefix of it, so no frame shows a far cell before a nearer one.
        val big = pattern(9, *Array(9) { "000000000" })
        val s = CanvasState.empty(big)
        val tool = PlacementTools.region(big, 40, 0)
        PlacementTools.place(s, tool, 0)
        assertContentEquals(tool.cells, s.log())
        val restored = assertNotNull(CanvasState.restore(big, s.log()))
        assertContentEquals(s.log(), restored.log())
        val manhattan = s.log().map { kotlin.math.abs(it % 9 - 4) + kotlin.math.abs(it / 9 - 4) }
        assertEquals(manhattan.sorted(), manhattan)
        val plan = FramePlan(s.placedCount, photoIntro = false)
        var last = 0
        for (frame in 0 until FramePlan.BUILD_FRAMES) {
            val shown = plan.placedThrough(frame)
            assertTrue(shown >= last)
            last = shown
        }
        assertEquals(81, last)
    }

    @Test
    fun sweepTimingIsCappedAndRingsShareIt() {
        assertEquals(0, Sweep.durationMs(0))
        assertEquals(16, Sweep.durationMs(1))
        assertEquals(320, Sweep.durationMs(20))
        // 100 rings would take 1.6s at 16ms: squeezed into 600.
        assertEquals(6f, Sweep.ringMs(100))
        assertEquals(600, Sweep.durationMs(100))
    }

    @Test
    fun sweepHapticsSwellAndClicksSpreadAcrossIt() {
        val ticks = Sweep.hapticTicks(600)
        assertEquals(11, ticks.size)
        assertEquals(0, ticks.first().delayMs)
        assertTrue(ticks.drop(1).all { it.delayMs == Sweep.TICK_GAP_MS })
        assertEquals(Sweep.TICK_FROM, ticks.first().scale)
        assertEquals(Sweep.TICK_TO, ticks.last().scale)
        assertEquals(1, Sweep.hapticTicks(0).size)
        assertContentEquals(intArrayOf(0, 120, 240, 360, 480), Sweep.clickTimes(600))
    }
}
