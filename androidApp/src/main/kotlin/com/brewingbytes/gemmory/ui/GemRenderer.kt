package com.brewingbytes.gemmory.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.math.max

/**
 * Draws a pattern as square cushion-cut gems (design system: Shapes) for the size picker, Preview, gallery tiles and
 * the completion screen. The play canvas has its own renderer.
 */
object GemRenderer {
    private const val FELT_DEEP = 0xFF3E4741.toInt()
    private const val BONE = 0xFFE9E4D8.toInt()

    /** Below this a cell is a flat square: a cushion that small only blurs. */
    private const val CUSHION_MIN_PX = 6

    /**
     * Placed cells as gems (every cell when [placed] is null) and the others as pale tints, the gem color mixed 78%
     * toward bone, as on the canvas (design system, Canvas cell colors). Gems have an 18% corner radius and a small
     * inset; the seam behind each is its own color, slightly darker, so the grid reads without dark gaps, with the
     * two-facet highlight when [cellPx] is at least 20.
     */
    fun render(pattern: Pattern, cellPx: Int, placed: ((Int) -> Boolean)? = null): Bitmap {
        val bitmap = createBitmap(pattern.width * cellPx, pattern.height * cellPx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(FELT_DEEP)
        val inset = max(0.5f, cellPx * 0.025f)
        val radius = (cellPx - 2 * inset) * 0.18f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val seam = Paint()
        val seamColors = IntArray(pattern.palette.size) { darken(pattern.palette[it].argb, SEAM_SHADE) }
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
        val facets = cellPx >= 20
        val cushions = cellPx >= CUSHION_MIN_PX
        val tints = IntArray(pattern.palette.size) { mix(pattern.palette[it].argb, BONE, 0.78f) }
        val rect = RectF()
        val path = Path()
        for (y in 0 until pattern.height) {
            for (x in 0 until pattern.width) {
                val left = x * cellPx + inset
                val top = y * cellPx + inset
                val size = cellPx - 2 * inset
                val slot = pattern.slotAt(x, y)
                val isPlaced = placed?.invoke(y * pattern.width + x) ?: true
                if (!cushions || !isPlaced) {
                    seam.color = if (isPlaced) pattern.palette[slot].argb else tints[slot]
                    canvas.drawRect(
                        (x * cellPx).toFloat(), (y * cellPx).toFloat(), ((x + 1) * cellPx).toFloat(), ((y + 1) * cellPx).toFloat(), seam,
                    )
                    continue
                }
                seam.color = seamColors[slot]
                canvas.drawRect(
                    (x * cellPx).toFloat(), (y * cellPx).toFloat(), ((x + 1) * cellPx).toFloat(), ((y + 1) * cellPx).toFloat(), seam,
                )
                rect.set(left, top, left + size, top + size)
                fill.color = pattern.palette[slot].argb
                canvas.drawRoundRect(rect, radius, radius, fill)
                if (facets) {
                    canvas.withSave {
                        path.reset()
                        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
                        clipPath(path)
                        path.reset()
                        path.moveTo(left, top)
                        path.lineTo(left + size * 0.5f, top)
                        path.lineTo(left, top + size * 0.5f)
                        path.close()
                        drawPath(path, light)
                        path.reset()
                        path.moveTo(left + size, top + size)
                        path.lineTo(left + size, top + size * 0.5f)
                        path.lineTo(left + size * 0.5f, top + size)
                        path.close()
                        drawPath(path, shade)
                    }
                }
            }
        }
        return bitmap
    }

    private const val SEAM_SHADE = 0.8f

    fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount + 0.5f).toInt()
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun darken(argb: Int, factor: Float): Int {
        val r = (((argb shr 16) and 0xFF) * factor).toInt()
        val g = (((argb shr 8) and 0xFF) * factor).toInt()
        val b = ((argb and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
