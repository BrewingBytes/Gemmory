package com.brewingbytes.gemmory.game

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/** On-screen cell sizes (dp) where the play canvas changes what it draws and what a tap does (design system zoom table). */
object CanvasZoom {
    /** Placed gems get raised edges; below this cells are flat. */
    const val RAISED_MIN_DP = 8f

    /** Numbers show and taps place from here up; below it a tap zooms in and a one-finger drag pans. */
    const val PLACE_MIN_DP = 16f

    /** Facets and the placement glint. */
    const val FACET_MIN_DP = 20f

    /** Where tap-to-zoom, double-tap and Find land. */
    const val TAP_ZOOM_DP = 24f

    const val MAX_DP = 48f

    /** Double-tap (design D23): back to fit when zoomed in, else to 24dp cells (never below fit). */
    fun doubleTapTarget(zoomDp: Float, fitDp: Float): Float =
        if (zoomDp > fitDp * ZOOMED_IN_MARGIN) fitDp else maxOf(TAP_ZOOM_DP, fitDp).coerceAtMost(MAX_DP)

    private const val ZOOMED_IN_MARGIN = 1.05f
}

/** What a single tap does (design system zoom table). */
enum class TapAction { ZOOM_IN, PLACE, NONE }

/** The gesture rules of design D23, apart from the pointer tracking itself. */
object CanvasGestures {
    fun tapAction(zoomDp: Float, cell: Int?): TapAction = when {
        cell == null -> TapAction.NONE
        zoomDp < CanvasZoom.PLACE_MIN_DP -> TapAction.ZOOM_IN
        else -> TapAction.PLACE
    }

    /**
     * The start cell decides: a one-finger drag paints only when it starts at placement zoom on an unplaced cell of
     * the selected color. Anything else pans.
     */
    fun startsPaint(zoomDp: Float, cell: Int?, state: CanvasState, selectedSlot: Int): Boolean =
        zoomDp >= CanvasZoom.PLACE_MIN_DP && cell != null && cell in 0 until state.cellCount &&
            !state.isPlaced(cell) && state.pattern.cells[cell] == selectedSlot

    /**
     * The cells a straight finger path from ([x0], [y0]) to ([x1], [y1]) crosses, in order, in canvas cell units
     * (column, row as floats). Every cell the segment touches is listed, so a fast drag doesn't skip any; cells
     * outside the [columns] × [rows] grid are left out.
     */
    fun cellsAlong(x0: Float, y0: Float, x1: Float, y1: Float, columns: Int, rows: Int): IntArray {
        var cx = floor(x0).toInt()
        var cy = floor(y0).toInt()
        val ex = floor(x1).toInt()
        val ey = floor(y1).toInt()
        val dx = x1 - x0
        val dy = y1 - y0
        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        var tMaxX = if (dx != 0f) ((if (dx > 0) cx + 1 else cx) - x0) / dx else Float.POSITIVE_INFINITY
        var tMaxY = if (dy != 0f) ((if (dy > 0) cy + 1 else cy) - y0) / dy else Float.POSITIVE_INFINITY
        val tDeltaX = if (dx != 0f) abs(1f / dx) else Float.POSITIVE_INFINITY
        val tDeltaY = if (dy != 0f) abs(1f / dy) else Float.POSITIVE_INFINITY
        val steps = abs(ex - cx) + abs(ey - cy)
        val out = IntArray(steps + 1)
        var count = 0
        fun add() {
            if (cx in 0 until columns && cy in 0 until rows) out[count++] = cy * columns + cx
        }
        add()
        repeat(steps) {
            if (tMaxX < tMaxY) {
                cx += stepX
                tMaxX += tDeltaX
            } else {
                cy += stepY
                tMaxY += tDeltaY
            }
            add()
        }
        return out.copyOf(count)
    }
}

/** What the second tap of a double-tap does. */
enum class DoubleTap {
    /** The first tap already started a tap-to-zoom; the second one adds nothing. */
    SWALLOW,

    /** Zoom to [CanvasZoom.doubleTapTarget]. */
    TOGGLE_ZOOM,
}

/** How a single tap ended, for [TapChain]. */
enum class TapOutcome { PLACED, ZOOMED_IN, OTHER }

/**
 * Double-tap without delaying placement (founder call 2026-10-14): every tap acts right away, and a second tap
 * close in time and place makes a double-tap only when neither tap places a gem. Two quick taps that each place
 * just place two gems, and a quick corrective tap after a miss places its gem instead of zooming out. The second
 * tap of a tap-to-zoom is swallowed, so the zoom finishes.
 */
class TapChain(private val timeoutMillis: Long, private val slopPx: Float) {
    private var outcome: TapOutcome? = null
    private var upAt = 0L
    private var x = 0f
    private var y = 0f

    /**
     * Called when a second tap lifts: its finger went down at [downAt] at ([x], [y]), and [wouldPlace] says whether
     * it would place a gem as a single tap ([CanvasGestures.startsPaint]). Null means "handle it as a single tap".
     * Clears the chain.
     */
    fun second(downAt: Long, x: Float, y: Float, wouldPlace: Boolean): DoubleTap? {
        val first = outcome ?: return null
        outcome = null
        if (downAt - upAt > timeoutMillis || hypot(x - this.x, y - this.y) > slopPx) return null
        return when (first) {
            TapOutcome.PLACED -> null
            TapOutcome.ZOOMED_IN -> DoubleTap.SWALLOW
            TapOutcome.OTHER -> if (wouldPlace) null else DoubleTap.TOGGLE_ZOOM
        }
    }

    /** Records a single tap that lifted at [upAt]; a placing tap never starts a double-tap. */
    fun first(upAt: Long, x: Float, y: Float, outcome: TapOutcome) {
        this.outcome = outcome.takeIf { it != TapOutcome.PLACED }
        this.upAt = upAt
        this.x = x
        this.y = y
    }

    fun clear() {
        outcome = null
    }
}
