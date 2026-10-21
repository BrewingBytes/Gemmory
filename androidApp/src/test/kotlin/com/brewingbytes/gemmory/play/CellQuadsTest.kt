package com.brewingbytes.gemmory.play

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CellQuadsTest {
    @Test
    fun aQuadCoversTheCellAndSamplesTheSpriteRectTheOldDrawBitmapUsed() {
        val quads = CellQuads()
        // Atlas at 20px: pitch 22, 12 per row, 1px padding. Slot 5 SELECTED is index 17: column 5, row 1.
        val index = 5 * SpriteCache.KINDS + SpriteKind.SELECTED.ordinal
        val srcLeft = (index % 12) * 22 + 1
        val srcTop = (index / 12) * 22 + 1
        quads.add(left = -3.5f, top = 40f, size = 19.5f, srcLeft = srcLeft, srcTop = srcTop, srcSize = 20)

        assertEquals(1, quads.count)
        assertEquals(8, quads.floatCount)
        assertEquals(6, quads.indexCount)
        assertContentEquals(
            floatArrayOf(-3.5f, 40f, 16f, 40f, 16f, 59.5f, -3.5f, 59.5f),
            quads.positions.copyOf(8),
        )
        assertContentEquals(floatArrayOf(111f, 23f, 131f, 23f, 131f, 43f, 111f, 43f), quads.texs.copyOf(8))
    }

    @Test
    fun eachQuadIsTwoTrianglesOverItsOwnFourVertices() {
        val quads = CellQuads()
        repeat(3) { quads.add(it * 10f, 0f, 10f, 0, 0, 8) }
        assertContentEquals(
            shortArrayOf(0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7, 8, 9, 10, 8, 10, 11),
            quads.indices.copyOf(quads.indexCount),
        )
    }

    @Test
    fun resetEmptiesTheBatchAndTheNextQuadStartsAtZero() {
        val quads = CellQuads()
        quads.add(0f, 0f, 10f, 0, 0, 8)
        quads.add(10f, 0f, 10f, 0, 0, 8)
        quads.reset()
        assertEquals(0, quads.count)
        quads.add(5f, 6f, 10f, 1, 1, 8)
        assertEquals(1, quads.count)
        assertEquals(5f, quads.positions[0])
        assertEquals(6f, quads.positions[1])
    }

    @Test
    fun growingKeepsEarlierQuadsAndAFullBatchStaysWithin16BitIndices() {
        val quads = CellQuads()
        repeat(CellQuads.MAX_QUADS) { quads.add(it.toFloat(), 0f, 1f, it % 100, 0, 1) }

        assertTrue(quads.full)
        assertEquals(0f, quads.positions[0])
        assertEquals(1_500f, quads.positions[1_500 * 8])
        assertEquals(83f, quads.texs[(CellQuads.MAX_QUADS - 1) * 8 + 2])
        val last = quads.indices[quads.indexCount - 1].toInt() and 0xFFFF
        assertEquals(CellQuads.MAX_QUADS * 4 - 1, last)
        assertTrue(last <= 0xFFFF)
        assertFailsWith<IllegalStateException> { quads.add(0f, 0f, 1f, 0, 0, 1) }

        quads.reset()
        assertFalse(quads.full)
    }
}
