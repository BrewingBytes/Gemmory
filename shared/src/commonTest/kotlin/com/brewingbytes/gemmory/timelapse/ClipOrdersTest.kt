package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.PlaceResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The founder clip tool's synthetic logs (T47). */
class ClipOrdersTest {
    private fun pattern(width: Int, height: Int, colors: Int = 4) = Pattern(
        width = width,
        height = height,
        palette = List(colors) { PaletteColor(it + 1, 0xFF000000.toInt() or it, Lab(it * 20.0, 0.0, 0.0)) },
        cells = IntArray(width * height) { (it * 7 + it / width) % colors },
    )

    /** The starter, odd and even sizes, a single cell, a Big 4:3. */
    private val patterns = listOf(pattern(24, 32), pattern(7, 5), pattern(6, 6), pattern(1, 1, colors = 1), pattern(142, 106))

    @Test
    fun everyOrderIsAPermutationOfEveryCell() {
        for (p in patterns) for (order in ClipOrder.entries) {
            val log = ClipOrders.of(order, p, seed = 42)
            assertEquals(p.cells.size, log.size, "$order ${p.width}x${p.height}")
            assertContentEquals(p.cells.indices.toList(), log.sorted(), "$order ${p.width}x${p.height}")
        }
    }

    @Test
    fun everyOrderPlaysAsASaveLog() {
        val p = pattern(24, 32)
        for (order in ClipOrder.entries) {
            val state = CanvasState.empty(p)
            for (cell in ClipOrders.of(order, p, seed = 7)) assertIs<PlaceResult.Placed>(state.place(cell, p.cells[cell]))
            assertTrue(state.isFinished, "$order finishes the canvas")
        }
    }

    @Test
    fun byColorGoesSlotBySlotInReadingOrder() {
        val p = pattern(24, 32)
        val log = ClipOrders.byColor(p)
        assertTrue(log.toList().zipWithNext().all { (a, b) -> p.cells[a] < p.cells[b] || (p.cells[a] == p.cells[b] && a < b) })
    }

    @Test
    fun rippleRingsGoOutwardFromTheCenter() {
        for (p in patterns) {
            val log = ClipOrders.ripple(p)
            val d = log.map { ClipOrders.distance(p, it) }
            assertTrue(d.zipWithNext().all { (a, b) -> a <= b }, "${p.width}x${p.height} never steps inward")
            assertEquals(p.cells.indices.minOf { ClipOrders.distance(p, it) }, d.first())
        }
        // 7×5: the one center cell first, then its four neighbours.
        val odd = pattern(7, 5)
        val log = ClipOrders.ripple(odd)
        assertEquals(2 * 7 + 3, log[0])
        assertEquals(setOf(1 * 7 + 3, 2 * 7 + 2, 2 * 7 + 4, 3 * 7 + 3), log.slice(1..4).toSet())
        // 6×6: the four middle cells form the first ring.
        val even = pattern(6, 6)
        assertEquals(setOf(14, 15, 20, 21), ClipOrders.ripple(even).take(4).toSet())
    }

    @Test
    fun randomIsStableForASeed() {
        val p = pattern(24, 32)
        assertContentEquals(ClipOrders.random(p, 99), ClipOrders.random(p, 99))
        assertFalse(ClipOrders.random(p, 99).contentEquals(ClipOrders.random(p, 100)))
        assertFalse(ClipOrders.random(p, 99).contentEquals(IntArray(p.cells.size) { it }))
        assertEquals(ClipOrders.seedOf("lavender-hood"), ClipOrders.seedOf("lavender-hood"))
        assertTrue(ClipOrders.seedOf("lavender-hood") != ClipOrders.seedOf("lavender"))
    }
}
