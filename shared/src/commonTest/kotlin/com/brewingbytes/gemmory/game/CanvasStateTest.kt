package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasStateTest {
    // 3×2, slots: 0 0 1 / 2 1 0 → slot 0 has 3 cells, slot 1 has 2, slot 2 has 1.
    private val pattern = Pattern(
        width = 3,
        height = 2,
        palette = List(3) { PaletteColor(it + 1, 0xFF000000.toInt() or it, Lab(it * 10.0, 0.0, 0.0)) },
        cells = intArrayOf(0, 0, 1, 2, 1, 0),
    )

    private fun placed(result: PlaceResult): List<GameEvent> = assertIs<PlaceResult.Placed>(result).events

    private fun rejected(result: PlaceResult): Rejection = assertIs<PlaceResult.Rejected>(result).reason

    private fun snapshot(s: CanvasState) =
        Triple(s.log().toList(), (0 until s.cellCount).map { s.isPlaced(it) }, (0 until 3).map { s.remaining(it) })

    @Test
    fun startsEmptyWithCountsPerColor() {
        val s = CanvasState.empty(pattern)
        assertEquals(0, s.placedCount)
        assertEquals(listOf(3, 2, 1), (0 until 3).map { s.remaining(it) })
        assertFalse(s.isFinished)
        assertContentEquals(intArrayOf(), s.log())
    }

    @Test
    fun placesACellOfTheSelectedColor() {
        val s = CanvasState.empty(pattern)
        assertEquals(listOf<GameEvent>(GameEvent.GemPlaced(cell = 1, slot = 0)), placed(s.place(1, 0)))
        assertTrue(s.isPlaced(1))
        assertEquals(2, s.remaining(0))
        assertContentEquals(intArrayOf(1), s.log())
    }

    @Test
    fun wrongColorChangesNothing() {
        val s = CanvasState.empty(pattern)
        s.place(0, 0)
        val before = snapshot(s)
        assertEquals(Rejection.WRONG_COLOR, rejected(s.place(2, 0)))
        assertEquals(Rejection.WRONG_COLOR, rejected(s.place(3, 1)))
        assertEquals(Rejection.WRONG_COLOR, rejected(s.place(1, 99)))
        assertEquals(before, snapshot(s))
    }

    @Test
    fun alreadyPlacedChangesNothing() {
        val s = CanvasState.empty(pattern)
        s.place(0, 0)
        val before = snapshot(s)
        assertEquals(Rejection.ALREADY_PLACED, rejected(s.place(0, 0)))
        assertEquals(before, snapshot(s))
    }

    @Test
    fun outOfRangeChangesNothing() {
        val s = CanvasState.empty(pattern)
        s.place(0, 0)
        val before = snapshot(s)
        assertEquals(Rejection.OUT_OF_RANGE, rejected(s.place(-1, 0)))
        assertEquals(Rejection.OUT_OF_RANGE, rejected(s.place(6, 0)))
        assertEquals(before, snapshot(s))
    }

    @Test
    fun logKeepsPlacementOrder() {
        val s = CanvasState.empty(pattern)
        s.place(5, 0)
        s.place(4, 1)
        s.place(0, 0)
        s.place(2, 1)
        assertContentEquals(intArrayOf(5, 4, 0, 2), s.log())
    }

    @Test
    fun logGrowsPastItsInitialCapacity() {
        val big = Pattern(40, 40, pattern.palette.take(1), IntArray(1600))
        val s = CanvasState.empty(big)
        val order = (0 until 1600).reversed()
        order.forEach { s.place(it, 0) }
        assertContentEquals(order.toList().toIntArray(), s.log())
        assertTrue(s.isFinished)
    }

    @Test
    fun colorFinishedSelectsTheNextUnfinishedColor() {
        val s = CanvasState.empty(pattern)
        s.place(2, 1)
        assertEquals(
            listOf(GameEvent.GemPlaced(4, 1), GameEvent.ColorFinished(slot = 1, next = 2)),
            placed(s.place(4, 1)),
        )
        assertTrue(s.isFinished(1))
        // Slot 2 is next after 2 → wraps to the first unfinished.
        assertEquals(listOf(GameEvent.GemPlaced(3, 2), GameEvent.ColorFinished(slot = 2, next = 0)), placed(s.place(3, 2)))
    }

    @Test
    fun pictureFinishedComesAfterTheLastColorFinished() {
        val s = CanvasState.empty(pattern)
        listOf(0 to 0, 1 to 0, 2 to 1, 3 to 2, 4 to 1).forEach { (cell, slot) -> s.place(cell, slot) }
        assertEquals(
            listOf(GameEvent.GemPlaced(5, 0), GameEvent.ColorFinished(slot = 0, next = null), GameEvent.PictureFinished),
            placed(s.place(5, 0)),
        )
        assertTrue(s.isFinished)
        assertNull(s.firstUnfinished())
    }

    @Test
    fun restoreReplaysTheLog() {
        val s = assertNotNull(CanvasState.restore(pattern, intArrayOf(3, 0, 2)))
        assertContentEquals(intArrayOf(3, 0, 2), s.log())
        assertEquals(listOf(2, 1, 0), (0 until 3).map { s.remaining(it) })
        assertTrue(s.isFinished(2))
        assertTrue(s.isPlaced(0) && s.isPlaced(2) && s.isPlaced(3))
        assertFalse(s.isPlaced(1))
    }

    @Test
    fun restoreRefusesLogsThePlacementRuleRejects() {
        assertNull(CanvasState.restore(pattern, intArrayOf(0, 0)))
        assertNull(CanvasState.restore(pattern, intArrayOf(6)))
        assertNull(CanvasState.restore(pattern, intArrayOf(-1)))
    }

    @Test
    fun restoreOfAFullLogIsFinished() {
        val s = assertNotNull(CanvasState.restore(pattern, intArrayOf(5, 4, 3, 2, 1, 0)))
        assertTrue(s.isFinished)
    }

    @Test
    fun trayOrderMovesFinishedColorsToTheEnd() {
        val s = CanvasState.empty(pattern)
        assertContentEquals(intArrayOf(0, 1, 2), s.trayOrder())
        listOf(0, 1, 5).forEach { s.place(it, 0) }
        assertContentEquals(intArrayOf(1, 2, 0), s.trayOrder())
        s.place(3, 2)
        assertContentEquals(intArrayOf(1, 0, 2), s.trayOrder())
    }

    @Test
    fun initialSelectionIsTheFirstUnfinishedColor() {
        val s = assertNotNull(CanvasState.restore(pattern, intArrayOf(0, 1, 5)))
        assertEquals(1, s.initialSlot(restored = null))
    }

    @Test
    fun initialSelectionRestoresAnUnfinishedSavedColor() {
        val s = CanvasState.empty(pattern)
        assertEquals(2, s.initialSlot(restored = 2))
    }

    @Test
    fun initialSelectionIgnoresAFinishedOrInvalidSavedColor() {
        val s = assertNotNull(CanvasState.restore(pattern, intArrayOf(3)))
        assertEquals(0, s.initialSlot(restored = 2))
        assertEquals(0, s.initialSlot(restored = 7))
    }

    @Test
    fun nextUnfinishedWrapsAndSkipsFinished() {
        val s = assertNotNull(CanvasState.restore(pattern, intArrayOf(2, 4)))
        assertEquals(2, s.nextUnfinishedAfter(0))
        assertEquals(0, s.nextUnfinishedAfter(2))
    }
}
