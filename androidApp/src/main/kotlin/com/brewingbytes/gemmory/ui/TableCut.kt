package com.brewingbytes.gemmory.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max

/**
 * The square table-cut gem (design system, Shapes, T40): the whole cell in the exact color with four bevel overlays
 * from a flat table to the cell edge, top 24% white, left 9% white, right 14% black, bottom 24% black, and from 20dp
 * a 0.8dp catchlight at 38% white along the table's top edge. The lit and shaded bevels cancel out, so the gem
 * averages to its true color when zoomed out. The play canvas, [GemRenderer], the confetti and the tray heaps all
 * draw it from here.
 */
object TableCut {
    /** Below this a gem is a flat square in its exact color. */
    const val MIN_DP = 8f

    /** From this the bevel is 20% of the cell instead of 24%. */
    const val NARROW_DP = 16f

    /** From this the catchlight shows. */
    const val CATCHLIGHT_DP = 20f

    const val WIDE_BEVEL = 0.24f
    const val NARROW_BEVEL = 0.20f
    private const val MIN_BEVEL_DP = 1.2f
    private const val CATCHLIGHT_THICK_DP = 0.8f

    const val TOP = 0x3DFFFFFF
    const val LEFT = 0x17FFFFFF
    const val RIGHT = 0x24000000
    const val BOTTOM = 0x3D000000
    const val CATCHLIGHT = 0x61FFFFFF

    /** The bevel's share of a [cellDp] cell: 24% from 8 to 15dp, 20% from 16dp. */
    fun bevelShare(cellDp: Float): Float = if (cellDp >= NARROW_DP) NARROW_BEVEL else WIDE_BEVEL

    /** The bevel width of a [cellPx] cell shown at [cellDp], never under 1.2dp; 0 below 8dp (a flat square). */
    fun bevelPx(cellPx: Float, cellDp: Float, density: Float): Float =
        if (cellDp < MIN_DP) 0f else max(MIN_BEVEL_DP * density, cellPx * bevelShare(cellDp))

    /** The catchlight's thickness at [cellDp] (0.8dp, at least 1px), or 0 below 20dp. */
    fun catchlightPx(cellDp: Float, density: Float): Float =
        if (cellDp < CATCHLIGHT_DP) 0f else max(1f, CATCHLIGHT_THICK_DP * density)

    /** Draws the bevels and catchlight of an [size] px gem at ([left], [top]) whose face is already filled. */
    class Painter {
        private val path = Path()
        private val top = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TOP }
        private val left = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEFT }
        private val right = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RIGHT }
        private val bottom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BOTTOM }
        private val catchlight = Paint().apply { color = CATCHLIGHT }

        fun draw(canvas: Canvas, left: Float, top: Float, size: Float, bevel: Float, catchlightPx: Float) {
            if (bevel <= 0f) return
            val r = left + size
            val b = top + size
            quad(canvas, left, top, r, top, r - bevel, top + bevel, left + bevel, top + bevel, this.top)
            quad(canvas, left, top, left + bevel, top + bevel, left + bevel, b - bevel, left, b, this.left)
            quad(canvas, r, top, r, b, r - bevel, b - bevel, r - bevel, top + bevel, right)
            quad(canvas, left, b, left + bevel, b - bevel, r - bevel, b - bevel, r, b, bottom)
            if (catchlightPx > 0f) canvas.drawRect(left + bevel, top + bevel, r - bevel, top + bevel + catchlightPx, catchlight)
        }

        @Suppress("LongParameterList")
        private fun quad(
            canvas: Canvas,
            x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
            paint: Paint,
        ) {
            path.reset()
            path.moveTo(x0, y0)
            path.lineTo(x1, y1)
            path.lineTo(x2, y2)
            path.lineTo(x3, y3)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    /**
     * A unit gem's bevels (and catchlight) centered on the origin, for Compose: [bevel] and the catchlight are shares of
     * the gem's size ([catchlightShare]: 0 for none). Each path pairs with its overlay color ([TOP], [LEFT], [RIGHT], [BOTTOM], [CATCHLIGHT]).
     */
    class UnitPaths(bevel: Float, catchlightShare: Float = 0f) {
        private val l = -0.5f
        private val r = 0.5f
        private val i = bevel
        val top = quad(l, l, r, l, r - i, l + i, l + i, l + i)
        val left = quad(l, l, l + i, l + i, l + i, r - i, l, r)
        val right = quad(r, l, r, r, r - i, r - i, r - i, l + i)
        val bottom = quad(l, r, l + i, r - i, r - i, r - i, r, r)
        val catchlight: androidx.compose.ui.graphics.Path? =
            if (catchlightShare <= 0f) null else quad(l + i, l + i, r - i, l + i, r - i, l + i + catchlightShare, l + i, l + i + catchlightShare)

        /** Each path with its overlay color as ARGB. */
        val layers: List<Pair<androidx.compose.ui.graphics.Path, Int>> =
            listOfNotNull<Pair<androidx.compose.ui.graphics.Path, Int>>(
                top to TOP, left to LEFT, right to RIGHT, bottom to BOTTOM, catchlight?.let { it to CATCHLIGHT },
            )

        private fun quad(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, y0)
                lineTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x3, y3)
                close()
            }
    }
}
