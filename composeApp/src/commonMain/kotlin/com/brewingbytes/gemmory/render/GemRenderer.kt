package com.brewingbytes.gemmory.render

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.play.CellColors

/**
 * Draws a pattern as square table-cut gems (design system, Shapes, T40) for the size picker, Preview, gallery tiles,
 * the completion screen and the time-lapse, in common code (iOS Stage 1a, T62). The play canvas has its own
 * [CanvasRenderer]; both draw the gem with [TableCut].
 */
object GemRenderer {
    /**
     * Placed cells as gems (every cell when [placed] is null) and the others as pale tints, the gem color mixed 78%
     * toward bone, as on the canvas (design system, Canvas cell colors). Off the canvas a cell's px stands in for its
     * dp: below 8px a gem is a flat square, from 8px a table cut (bevels 24%, 20% from 16px, the catchlight from 20px)
     * and an unplaced cell gets its 1px seam on the right and bottom.
     */
    fun render(pattern: Pattern, cellPx: Int, placed: ((Int) -> Boolean)? = null): ImageBitmap {
        val image = ImageBitmap(pattern.width * cellPx, pattern.height * cellPx)
        val canvas = Canvas(image)
        canvas.drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), Paint().apply { color = Color(CellColors.FELT_DEEP) })
        val painter = GemPainter(pattern, image.width, image.height)
        for (cell in pattern.cells.indices) painter.drawCell(canvas, cell, placed?.invoke(cell) ?: true)
        return image
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
        private val face = Paint().apply { isAntiAlias = false }
        private val seam = Paint().apply { isAntiAlias = false }
        private val tableCut = TableCut.Painter()
        private val gems = IntArray(pattern.palette.size) { pattern.palette[it].argb or CellColors.OPAQUE }
        private val tints = IntArray(gems.size) { CellColors.mix(pattern.palette[it].argb, CellColors.BONE, CellColors.UNPLACED_TINT) }
        private val tintSeams = IntArray(tints.size) { darken(tints[it], SEAM_SHADE) }

        fun drawCell(canvas: Canvas, cell: Int, isPlaced: Boolean) {
            val x = cell % pattern.width
            val y = cell / pattern.width
            val l = (x * widthPx / pattern.width).toFloat()
            val r = ((x + 1) * widthPx / pattern.width).toFloat()
            val t = (y * heightPx / pattern.height).toFloat()
            val b = ((y + 1) * heightPx / pattern.height).toFloat()
            val slot = pattern.cells[cell]
            face.color = Color(if (isPlaced) gems[slot] else tints[slot])
            canvas.drawRect(l, t, r, b, face)
            if (!raised) return
            if (isPlaced) {
                tableCut.draw(canvas, l, t, minOf(r - l, b - t), bevel, catchlight)
            } else {
                seam.color = Color(tintSeams[slot])
                canvas.drawRect(r - 1f, t, r, b, seam)
                canvas.drawRect(l, b - 1f, r - 1f, b, seam)
            }
        }
    }

    /** An unplaced cell's seam: its tint 8% darker, as on the canvas. */
    private const val SEAM_SHADE = 0.92f

    /** Each channel scaled by [factor] and truncated, as androidApp's `GemRenderer.darken`. */
    fun darken(argb: Int, factor: Float): Int {
        val r = (((argb shr 16) and 0xFF) * factor).toInt()
        val g = (((argb shr 8) and 0xFF) * factor).toInt()
        val b = ((argb and 0xFF) * factor).toInt()
        return CellColors.OPAQUE or (r shl 16) or (g shl 8) or b
    }
}
