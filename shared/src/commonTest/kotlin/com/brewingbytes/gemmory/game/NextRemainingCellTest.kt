package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextRemainingCellTest {
    // 4×2, slots: 0 1 0 1 / 1 0 2 0 → slot 0 at cells 0, 2, 5, 7; slot 1 at 1, 3, 4; slot 2 at 6.
    private val pattern = Pattern(
        width = 4,
        height = 2,
        palette = List(3) { PaletteColor(it + 1, 0xFF000000.toInt() or it, Lab(it * 10.0, 0.0, 0.0)) },
        cells = intArrayOf(0, 1, 0, 1, 1, 0, 2, 0),
    )

    private fun next(placed: Set<Int>, slot: Int, after: Int) = nextRemainingCell(pattern, { it in placed }, slot, after)

    @Test
    fun startsAtTheFirstCellWithNoCursor() {
        assertEquals(0, next(emptySet(), slot = 0, after = -1))
        assertEquals(1, next(emptySet(), slot = 1, after = -1))
    }

    @Test
    fun scansRowMajorAfterTheCursor() {
        assertEquals(2, next(emptySet(), slot = 0, after = 0))
        assertEquals(5, next(emptySet(), slot = 0, after = 2))
        assertEquals(7, next(emptySet(), slot = 0, after = 5))
    }

    @Test
    fun wrapsAroundToTheStart() {
        assertEquals(0, next(emptySet(), slot = 0, after = 7))
        assertEquals(1, next(emptySet(), slot = 1, after = 4))
    }

    @Test
    fun skipsPlacedCells() {
        assertEquals(7, next(setOf(2, 5), slot = 0, after = 0))
        assertEquals(2, next(setOf(5, 7, 0), slot = 0, after = 2), "the cursor cell itself is checked last")
        assertEquals(4, next(setOf(1, 3), slot = 1, after = 1))
    }

    @Test
    fun aSingleCellLeftIsFoundAgainFromItself() {
        assertEquals(6, next(emptySet(), slot = 2, after = 6))
        assertEquals(6, next(emptySet(), slot = 2, after = -1))
    }

    @Test
    fun noneLeftIsNull() {
        assertNull(next(setOf(0, 2, 5, 7), slot = 0, after = 3))
        assertNull(next(setOf(6), slot = 2, after = -1))
    }

    @Test
    fun anOutOfRangeCursorStartsAtTheTop() {
        assertEquals(0, next(emptySet(), slot = 0, after = 99))
    }

    @Test
    fun followsTheGameStateAsGemsArePlaced() {
        val state = CanvasState.empty(pattern)
        var cursor = -1
        val found = mutableListOf<Int>()
        while (true) {
            val cell = nextRemainingCell(pattern, state::isPlaced, 0, cursor) ?: break
            found += cell
            state.place(cell, 0)
            cursor = cell
        }
        assertEquals(listOf(0, 2, 5, 7), found)
    }
}
