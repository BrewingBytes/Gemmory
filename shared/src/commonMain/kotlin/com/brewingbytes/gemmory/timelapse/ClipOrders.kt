package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Pattern
import kotlin.random.Random

/** The placement orders of the founder clip tool (T47); [fileName] goes into the clip's file name. Its debug screen names them. */
enum class ClipOrder(val fileName: String) {
    BY_COLOR("by-color"),
    RIPPLE("ripple"),
    RANDOM("random"),
}

/**
 * Synthetic placement logs for the founder clip tool (T47): each is a permutation of every cell index, so it's a
 * valid save-format log and the time-lapse replays it like a player's.
 */
object ClipOrders {
    fun of(order: ClipOrder, pattern: Pattern, seed: Long): IntArray = when (order) {
        ClipOrder.BY_COLOR -> byColor(pattern)
        ClipOrder.RIPPLE -> ripple(pattern)
        ClipOrder.RANDOM -> random(pattern, seed)
    }

    /** Palette slot order (most common color first), reading order within a slot. */
    fun byColor(pattern: Pattern): IntArray = pattern.cells.indices.sortedBy { pattern.cells[it] }.toIntArray()

    /**
     * Rings out from the grid's center: 4-connected (Manhattan) distance from the center, reading order within a ring.
     * The distance is measured from the geometric center at double scale, so even sizes stay symmetric.
     */
    fun ripple(pattern: Pattern): IntArray = pattern.cells.indices.sortedBy { distance(pattern, it) }.toIntArray()

    /** A shuffle that's the same for the same [seed]. */
    fun random(pattern: Pattern, seed: Long): IntArray {
        val log = IntArray(pattern.cells.size) { it }
        val random = Random(seed)
        for (i in log.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val t = log[i]
            log[i] = log[j]
            log[j] = t
        }
        return log
    }

    /** Twice the Manhattan distance from [cell]'s center to the grid's center. */
    fun distance(pattern: Pattern, cell: Int): Int {
        val x = cell % pattern.width
        val y = cell / pattern.width
        return abs(2 * x + 1 - pattern.width) + abs(2 * y + 1 - pattern.height)
    }

    /** A picture's own stable seed (FNV-1a of its id), so a re-render gives the same clip. */
    fun seedOf(id: String): Long {
        var hash = FNV_OFFSET
        for (c in id) {
            hash = hash xor c.code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    private fun abs(v: Int) = if (v < 0) -v else v

    private const val FNV_OFFSET = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
