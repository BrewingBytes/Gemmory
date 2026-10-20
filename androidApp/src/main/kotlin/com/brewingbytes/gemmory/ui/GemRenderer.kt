package com.brewingbytes.gemmory.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.brewingbytes.gemmory.converter.Pattern

/**
 * Draws a pattern as square table-cut gems (design system, Shapes, T40) for the size picker, Preview, gallery tiles,
 * the completion screen and the time-lapse. The play canvas has its own renderer; both draw the gem with [TableCut].
 */
object GemRenderer {
    private const val FELT_DEEP = 0xFF3E4741.toInt()
    private const val BONE = 0xFFE9E4D8.toInt()

    /**
     * Placed cells as gems (every cell when [placed] is null) and the others as pale tints, the gem color mixed 78%
     * toward bone, as on the canvas (design system, Canvas cell colors). Off the canvas a cell's px stands in for its
     * dp: below 8px a gem is a flat square, from 8px a table cut (bevels 24%, 20% from 16px, the catchlight from 20px)
     * and an unplaced cell gets its 1px seam on the right and bottom.
     */
    fun render(pattern: Pattern, cellPx: Int, placed: ((Int) -> Boolean)? = null): Bitmap {
        val bitmap = createBitmap(pattern.width * cellPx, pattern.height * cellPx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(FELT_DEEP)
        val painter = GemPainter(pattern, bitmap.width, bitmap.height)
        for (cell in pattern.cells.indices) painter.drawCell(canvas, cell, placed?.invoke(cell) ?: true)
        return bitmap
    }

    /**
     * Draws single cells of [pattern] laid out over [widthPx] × [heightPx]. Cell edges are whole pixels (a cell is
     * `widthPx / width` wide, give or take one), so a width that isn't a multiple of the grid leaves no hairlines. The
     * time-lapse uses it to add each frame's new gems to one bitmap (eng finding 12).
     */
    class GemPainter(private val pattern: Pattern, private val widthPx: Int, private val heightPx: Int) {
        private val cellPx = widthPx.toFloat() / pattern.width
        private val bevel = TableCut.bevelPx(cellPx, cellPx, density = 1f)
        private val catchlight = TableCut.catchlightPx(cellPx, density = 1f)
        private val raised = cellPx >= TableCut.MIN_DP
        private val face = Paint()
        private val seam = Paint()
        private val tableCut = TableCut.Painter()
        private val tints = IntArray(pattern.palette.size) { mix(pattern.palette[it].argb, BONE, 0.78f) }
        private val tintSeams = IntArray(tints.size) { darken(tints[it], SEAM_SHADE) }

        fun drawCell(canvas: Canvas, cell: Int, isPlaced: Boolean) {
            val x = cell % pattern.width
            val y = cell / pattern.width
            val l = (x * widthPx / pattern.width).toFloat()
            val r = ((x + 1) * widthPx / pattern.width).toFloat()
            val t = (y * heightPx / pattern.height).toFloat()
            val b = ((y + 1) * heightPx / pattern.height).toFloat()
            val slot = pattern.cells[cell]
            face.color = if (isPlaced) pattern.palette[slot].argb or OPAQUE else tints[slot]
            canvas.drawRect(l, t, r, b, face)
            if (!raised) return
            if (isPlaced) {
                tableCut.draw(canvas, l, t, minOf(r - l, b - t), bevel, catchlight)
            } else {
                seam.color = tintSeams[slot]
                canvas.drawRect(r - 1f, t, r, b, seam)
                canvas.drawRect(l, b - 1f, r - 1f, b, seam)
            }
        }
    }

    private const val OPAQUE = 0xFF shl 24

    /** An unplaced cell's seam: its tint 8% darker, as on the canvas. */
    private const val SEAM_SHADE = 0.92f

    fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount + 0.5f).toInt()
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    internal fun darken(argb: Int, factor: Float): Int {
        val r = (((argb shr 16) and 0xFF) * factor).toInt()
        val g = (((argb shr 8) and 0xFF) * factor).toInt()
        val b = ((argb and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
