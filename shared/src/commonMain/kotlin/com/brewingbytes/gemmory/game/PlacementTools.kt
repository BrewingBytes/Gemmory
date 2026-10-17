package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Pattern

/**
 * The placement tools (#20). [TAP], [BRUSH] and [REGION] are the tool well's modes; [FILL_COLOR] is the tray's
 * "Fill N" button, never a mode. [param] is the analytics value.
 */
enum class PlacementTool(val param: String) {
    TAP("tap"),
    BRUSH("brush"),
    REGION("region"),
    FILL_COLOR("color"),
    ;

    companion object {
        /** What the tool well offers, in its order. */
        val MODES = listOf(TAP, BRUSH, REGION)
    }
}

/**
 * A tool's cells in placement order, with the reveal ring of each (same size, never decreasing): the save log gets
 * the cells in this order, so the time-lapse replays a fill the way the canvas showed it.
 */
class ToolCells(val cells: IntArray, val rings: IntArray) {
    init {
        require(cells.size == rings.size) { "${cells.size} cells but ${rings.size} rings" }
    }
}

/**
 * A tool's placements, all through [CanvasState.place]: [placed] in log order, each with its reveal [rings] entry.
 * [colorFinished] and [pictureFinished] are what the batch's last gem caused, as for a single tap.
 */
class Batch(
    val placed: IntArray,
    val rings: IntArray,
    val colorFinished: GameEvent.ColorFinished?,
    val pictureFinished: Boolean,
) {
    val lastRing: Int get() = if (rings.isEmpty()) 0 else rings.last()
}

/** Which cells each tool picks (#20, design system Placement tools). Pure, so it's tested without Android. */
object PlacementTools {
    /** The brush is 3×3: a square with a middle, so the finger is at its center. */
    const val BRUSH_SIZE = 3

    /** Fill color shows while the selected color has this many cells or fewer left, as Find does (design D24). */
    const val FILL_COLOR_MAX_LEFT = 20

    /**
     * The brush around [center]: the [BRUSH_SIZE]² square clipped to the grid, nearest first (then reading order).
     * One ring: a brush stamp shows at once, like a drag.
     */
    fun brush(pattern: Pattern, center: Int): ToolCells {
        val width = pattern.width
        val cx = center % width
        val cy = center / width
        val r = BRUSH_SIZE / 2
        val cells = ArrayList<Int>(BRUSH_SIZE * BRUSH_SIZE)
        for (y in cy - r..cy + r) {
            for (x in cx - r..cx + r) {
                if (x in 0 until width && y in 0 until pattern.height) cells += y * width + x
            }
        }
        val ordered = cells.sortedWith(compareBy<Int> { distance2(it, center, width) }.thenBy { it }).toIntArray()
        return ToolCells(ordered, IntArray(ordered.size))
    }

    /**
     * The region around [start]: every cell of [slot] 4-connected to it through cells of [slot], placed or not, so a
     * half-placed patch still fills whole. Ordered ring by ring outward from [start] (breadth-first steps), each ring
     * in reading order. Empty when [start] isn't [slot]'s.
     */
    fun region(pattern: Pattern, start: Int, slot: Int): ToolCells {
        val cells = pattern.cells
        if (start !in cells.indices || cells[start] != slot) return ToolCells(IntArray(0), IntArray(0))
        val width = pattern.width
        val height = pattern.height
        val seen = BooleanArray(cells.size)
        val order = ArrayList<Int>()
        val rings = ArrayList<Int>()
        var front = intArrayOf(start)
        seen[start] = true
        var ring = 0
        while (front.isNotEmpty()) {
            for (cell in front) {
                order += cell
                rings += ring
            }
            val next = ArrayList<Int>()
            for (cell in front) {
                val x = cell % width
                val y = cell / width
                fun visit(n: Int) {
                    if (!seen[n] && cells[n] == slot) {
                        seen[n] = true
                        next += n
                    }
                }
                if (y > 0) visit(cell - width)
                if (x > 0) visit(cell - 1)
                if (x < width - 1) visit(cell + 1)
                if (y < height - 1) visit(cell + width)
            }
            front = next.toIntArray().apply { sort() }
            ring++
        }
        return ToolCells(order.toIntArray(), rings.toIntArray())
    }

    /**
     * Fill color: every unplaced cell of [slot], nearest to [from] first (then reading order), one ring each, so the
     * last specks go in one after another.
     */
    fun fillColor(pattern: Pattern, isPlaced: (Int) -> Boolean, slot: Int, from: Int): ToolCells {
        val width = pattern.width
        val left = pattern.cells.indices.filter { pattern.cells[it] == slot && !isPlaced(it) }
        val ordered = left.sortedWith(compareBy<Int> { distance2(it, from, width) }.thenBy { it }).toIntArray()
        return ToolCells(ordered, IntArray(ordered.size) { it })
    }

    /**
     * Places [tool]'s cells in order, each through [CanvasState.place] with [selectedSlot]: placed gems and cells of
     * other colors are skipped and change nothing. The selection doesn't move during a batch, so it never spills into
     * the next color; once the color is finished nothing more can be placed, so it stops there.
     */
    fun place(state: CanvasState, tool: ToolCells, selectedSlot: Int): Batch {
        val placed = ArrayList<Int>()
        val rings = ArrayList<Int>()
        var colorFinished: GameEvent.ColorFinished? = null
        var pictureFinished = false
        for (i in tool.cells.indices) {
            val result = state.place(tool.cells[i], selectedSlot) as? PlaceResult.Placed ?: continue
            placed += tool.cells[i]
            rings += tool.rings[i]
            for (event in result.events) {
                when (event) {
                    is GameEvent.ColorFinished -> colorFinished = event
                    GameEvent.PictureFinished -> pictureFinished = true
                    is GameEvent.GemPlaced -> Unit
                }
            }
            if (colorFinished != null) break
        }
        return Batch(placed.toIntArray(), rings.toIntArray(), colorFinished, pictureFinished)
    }

    private fun distance2(a: Int, b: Int, width: Int): Int {
        val dx = a % width - b % width
        val dy = a / width - b / width
        return dx * dx + dy * dy
    }
}

/**
 * How a batch shows (#20, founder pick "as mocked"): it's placed at once, and the canvas reveals it ring by ring,
 * [RING_MS] a ring, squeezed to fit [MAX_MS]. A light haptic sweep and a short run of clicks play across it; the
 * color-done, milestone and finish cues wait for its end. Under reduced motion it shows at once.
 */
object Sweep {
    const val RING_MS = 16f
    const val MAX_MS = 600

    /** Gap between haptic ticks, and their scale from the first to the last. */
    const val TICK_GAP_MS = 60
    const val TICK_FROM = 0.3f
    const val TICK_TO = 0.6f

    /** Clicks across a sweep, each a semitone above the last. */
    const val CLICKS = 5

    /** Milliseconds per ring for a batch whose last ring is [lastRing]. */
    fun ringMs(lastRing: Int): Float = if (lastRing <= 0) 0f else minOf(RING_MS, MAX_MS.toFloat() / lastRing)

    /** When ring [lastRing], the batch's last, shows: the sweep's length (0 for a single ring). */
    fun durationMs(lastRing: Int): Int = (ringMs(lastRing) * lastRing).toInt()

    /** The haptic sweep: a tick every [TICK_GAP_MS] across [durationMs], swelling from [TICK_FROM] to [TICK_TO]. */
    fun hapticTicks(durationMs: Int): List<FinishCelebration.Tick> {
        val count = durationMs / TICK_GAP_MS + 1
        return List(count) { i ->
            val t = if (count == 1) 1f else i.toFloat() / (count - 1)
            FinishCelebration.Tick(delayMs = if (i == 0) 0 else TICK_GAP_MS, scale = TICK_FROM + (TICK_TO - TICK_FROM) * t)
        }
    }

    /** When each of the [CLICKS] clicks plays (ms from the start), evenly across [durationMs]; the first at once. */
    fun clickTimes(durationMs: Int): IntArray = IntArray(CLICKS) { it * durationMs / CLICKS }
}
