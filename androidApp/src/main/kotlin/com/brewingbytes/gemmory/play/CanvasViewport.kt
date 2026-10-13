package com.brewingbytes.gemmory.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.save.ViewStateCodec
import kotlin.math.floor

/**
 * Zoom and pan of the play canvas (design system, Play canvas). [zoomDp] is the on-screen cell size in dp, and
 * [centerX]/[centerY] are the canvas point (in cells) at the middle of the viewport, so the same spot stays centered
 * across rotation and fold changes. Min zoom fits the whole canvas; max is 48dp cells.
 *
 * Snapshot state, so the canvas redraws on every change. Lives in the play session, which outlives rotation.
 */
class CanvasViewport(val columns: Int, val rows: Int) {
    var zoomDp by mutableFloatStateOf(0f)
        private set
    var centerX by mutableFloatStateOf(columns / 2f)
        private set
    var centerY by mutableFloatStateOf(rows / 2f)
        private set
    var width by mutableIntStateOf(0)
        private set
    var height by mutableIntStateOf(0)
        private set
    private var density = 1f
    private var pending: ViewState? = null

    val isLaidOut: Boolean get() = width > 0 && height > 0
    val cellPx: Float get() = zoomDp * density
    val minZoomDp: Float get() = minOf(minOf(width / columns.toFloat(), height / rows.toFloat()) / density, MAX_ZOOM_DP)

    /** Left and top edge of the canvas in viewport px. */
    val offsetX: Float get() = width / 2f - centerX * cellPx
    val offsetY: Float get() = height / 2f - centerY * cellPx

    /** Applies a saved view once the viewport size is known; null opens at fit. */
    fun restore(view: ViewState?) {
        pending = view
        if (isLaidOut) applyPending()
    }

    fun onLayout(width: Int, height: Int, density: Float) {
        if (width <= 0 || height <= 0) return
        if (width == this.width && height == this.height && density == this.density) return
        val first = !isLaidOut
        this.width = width
        this.height = height
        this.density = density
        if (first) applyPending() else set(zoomDp, centerX, centerY)
    }

    /** Clamps to the zoom range and keeps the canvas covering the viewport (or centered when it's smaller). */
    fun set(zoomDp: Float, centerX: Float, centerY: Float) {
        val zoom = zoomDp.coerceIn(minZoomDp, MAX_ZOOM_DP)
        this.zoomDp = zoom
        val px = zoom * density
        this.centerX = clampCenter(centerX, columns, width / px)
        this.centerY = clampCenter(centerY, rows, height / px)
    }

    /** One pinch/pan step: zooms by [zoomFactor] around the fingers' centroid, then moves with the fingers. */
    fun transform(centroidX: Float, centroidY: Float, panX: Float, panY: Float, zoomFactor: Float) {
        val oldPx = cellPx
        val pointX = centerX + (centroidX - width / 2f) / oldPx
        val pointY = centerY + (centroidY - height / 2f) / oldPx
        val zoom = (zoomDp * zoomFactor).coerceIn(minZoomDp, MAX_ZOOM_DP)
        val newPx = zoom * density
        set(zoom, pointX - (centroidX - width / 2f + panX) / newPx, pointY - (centroidY - height / 2f + panY) / newPx)
    }

    /** The canvas point (in cells) under viewport position ([x], [y]). */
    fun pointAt(x: Float, y: Float): Pair<Float, Float> = Pair((x - offsetX) / cellPx, (y - offsetY) / cellPx)

    /** The cell under viewport position ([x], [y]), or null outside the canvas. */
    fun cellAt(x: Float, y: Float): Int? {
        val (cx, cy) = pointAt(x, y)
        val col = floor(cx).toInt()
        val row = floor(cy).toInt()
        return if (col in 0 until columns && row in 0 until rows) row * columns + col else null
    }

    /** Pan is the viewport center in dp from the canvas's top-left, so it doesn't depend on the screen it's restored on. */
    fun toViewState(slot: Int): ViewState =
        ViewState(zoom = zoomDp, panX = centerX * zoomDp, panY = centerY * zoomDp, slot = slot)

    private fun applyPending() {
        val view = pending
        pending = null
        if (view == null) {
            set(minZoomDp, columns / 2f, rows / 2f)
            return
        }
        set(view.zoom, view.panX / view.zoom, view.panY / view.zoom)
    }

    private fun clampCenter(center: Float, cells: Int, visibleCells: Float): Float =
        if (visibleCells >= cells) cells / 2f else center.coerceIn(visibleCells / 2f, cells - visibleCells / 2f)

    companion object {
        const val MAX_ZOOM_DP = ViewStateCodec.MAX_ZOOM_DP

        /** Numbers show and taps place from this cell size up; below it a tap zooms in (design system zoom table). */
        const val PLACE_MIN_DP = 16f

        /** Where tap-to-zoom lands. */
        const val TAP_ZOOM_DP = 24f
    }
}
