package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Pattern

/** What one accepted placement caused, in order: always [GemPlaced], then maybe [ColorFinished], then maybe [PictureFinished]. */
sealed interface GameEvent {
    data class GemPlaced(val cell: Int, val slot: Int) : GameEvent

    /** [next] is the slot to auto-select (design D25), or null when nothing is left. */
    data class ColorFinished(val slot: Int, val next: Int?) : GameEvent

    data object PictureFinished : GameEvent
}

enum class Rejection { OUT_OF_RANGE, ALREADY_PLACED, WRONG_COLOR }

sealed interface PlaceResult {
    data class Placed(val events: List<GameEvent>) : PlaceResult

    /** Nothing changed: no log entry, no counter moved (eng D4). */
    data class Rejected(val reason: Rejection) : PlaceResult
}

/**
 * The play state of one canvas: the in-memory placed bitset, the remaining count per color and the ordered
 * placement log, which is the only thing saved (eng D4). Every change goes through [place], so the log is always
 * an ordered list of unique cell indices, and a restored log is replayed through the same rule.
 *
 * Tray order (design D25) is slot order with finished colors moved to the end.
 */
class CanvasState private constructor(val pattern: Pattern) {
    val cellCount: Int = pattern.cells.size
    private val placedBits = LongArray((cellCount + 63) / 64)
    private val remaining: IntArray = pattern.countsPerSlot()
    private var log = IntArray(minOf(cellCount, 256))

    var placedCount: Int = 0
        private set

    val isFinished: Boolean get() = placedCount == cellCount

    fun isPlaced(cell: Int): Boolean = placedBits[cell ushr 6] and (1L shl (cell and 63)) != 0L

    fun remaining(slot: Int): Int = remaining[slot]

    fun isFinished(slot: Int): Boolean = remaining[slot] == 0

    /** The [index]th placement, oldest first (0 until [placedCount]). */
    fun placedAt(index: Int): Int {
        require(index in 0 until placedCount) { "no placement $index of $placedCount" }
        return log[index]
    }

    /** A copy of the placement log, oldest first. */
    fun log(): IntArray = log.copyOf(placedCount)

    /** Places a gem on [cell] if it's unplaced and of [selectedSlot]'s color; anything else changes nothing. */
    fun place(cell: Int, selectedSlot: Int): PlaceResult {
        if (cell !in 0 until cellCount) return PlaceResult.Rejected(Rejection.OUT_OF_RANGE)
        if (isPlaced(cell)) return PlaceResult.Rejected(Rejection.ALREADY_PLACED)
        val slot = pattern.cells[cell]
        if (slot != selectedSlot) return PlaceResult.Rejected(Rejection.WRONG_COLOR)

        placedBits[cell ushr 6] = placedBits[cell ushr 6] or (1L shl (cell and 63))
        remaining[slot]--
        if (placedCount == log.size) log = log.copyOf(minOf(cellCount, log.size * 2))
        log[placedCount++] = cell

        val events = ArrayList<GameEvent>(3)
        events += GameEvent.GemPlaced(cell, slot)
        if (remaining[slot] == 0) events += GameEvent.ColorFinished(slot, nextUnfinishedAfter(slot))
        if (isFinished) events += GameEvent.PictureFinished
        return PlaceResult.Placed(events)
    }

    /** Unfinished slots in slot order, then finished ones. */
    fun trayOrder(): IntArray {
        val slots = remaining.indices
        return (slots.filter { remaining[it] > 0 } + slots.filter { remaining[it] == 0 }).toIntArray()
    }

    fun firstUnfinished(): Int? = remaining.indices.firstOrNull { remaining[it] > 0 }

    /** The unfinished trough that followed [slot] in tray order, wrapping to the first; null when all are done. */
    fun nextUnfinishedAfter(slot: Int): Int? =
        (slot + 1 until remaining.size).firstOrNull { remaining[it] > 0 } ?: firstUnfinished()

    /** The selection on open (design D25): the restored slot if it still has gems left, else the first unfinished. */
    fun initialSlot(restored: Int?): Int {
        if (restored != null && restored in remaining.indices && remaining[restored] > 0) return restored
        return firstUnfinished() ?: restored?.takeIf { it in remaining.indices } ?: 0
    }

    companion object {
        fun empty(pattern: Pattern): CanvasState = CanvasState(pattern)

        /** Replays [log] through [place]; null if any entry would be rejected (out of range or already placed). */
        fun restore(pattern: Pattern, log: IntArray): CanvasState? {
            val state = CanvasState(pattern)
            for (cell in log) {
                if (cell !in 0 until state.cellCount) return null
                if (state.place(cell, pattern.cells[cell]) !is PlaceResult.Placed) return null
            }
            return state
        }
    }
}
