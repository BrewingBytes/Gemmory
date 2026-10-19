package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Pattern
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The frost (design system, Hidden pictures; T36 teaser, T43 mystery): the one look for a picture the player may not
 * see yet. The finished picture averaged into [COLUMNS] blocks across (rows by its aspect), scaled back up with a blur
 * of 1/[BLUR_DIVISOR] of its width, then covered with `felt-deep` at [OVERLAY_ALPHA] (the UI lays that on, since the
 * token follows the theme). Its mood and colors show; its subject doesn't. Pure, so the phone draws it from the grid
 * and nothing is shipped as an image.
 */
object Frost {
    const val COLUMNS = 6
    const val BLUR_DIVISOR = 14
    const val OVERLAY_ALPHA = 0.45f

    /** The averaged blocks, row-major, opaque ARGB. */
    class Blocks(val columns: Int, val rows: Int, val argb: IntArray)

    fun blocks(pattern: Pattern): Blocks {
        val rows = max(1, (COLUMNS * pattern.height / pattern.width.toFloat()).roundToInt())
        val sums = LongArray(COLUMNS * rows * 3)
        val counts = IntArray(COLUMNS * rows)
        for (y in 0 until pattern.height) {
            val by = y * rows / pattern.height
            for (x in 0 until pattern.width) {
                val b = by * COLUMNS + x * COLUMNS / pattern.width
                val c = pattern.palette[pattern.slotAt(x, y)].argb
                sums[b * 3] += (c shr 16 and 0xFF).toLong()
                sums[b * 3 + 1] += (c shr 8 and 0xFF).toLong()
                sums[b * 3 + 2] += (c and 0xFF).toLong()
                counts[b]++
            }
        }
        val argb = IntArray(COLUMNS * rows) { b ->
            val n = max(1, counts[b])
            argb((sums[b * 3] / n).toInt(), (sums[b * 3 + 1] / n).toInt(), (sums[b * 3 + 2] / n).toInt())
        }
        return Blocks(COLUMNS, rows, argb)
    }

    /**
     * The frost of [pattern] as a [width] × [height] image (opaque ARGB, row-major), for a square thumbnail or the
     * picture's own aspect: blocks scaled up smoothly, box-blurred three times (close to a Gaussian) by a radius of
     * 1/[BLUR_DIVISOR] of [width], then [overlay] (opaque ARGB) laid on at [OVERLAY_ALPHA] when given.
     */
    fun image(pattern: Pattern, width: Int, height: Int, overlay: Int? = null): IntArray {
        require(width > 0 && height > 0)
        val blocks = blocks(pattern)
        val r = FloatArray(width * height)
        val g = FloatArray(width * height)
        val b = FloatArray(width * height)
        for (y in 0 until height) {
            val fy = ((y + 0.5f) * blocks.rows / height - 0.5f).coerceIn(0f, blocks.rows - 1f)
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, blocks.rows - 1)
            val ty = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) * blocks.columns / width - 0.5f).coerceIn(0f, blocks.columns - 1f)
                val x0 = fx.toInt()
                val x1 = minOf(x0 + 1, blocks.columns - 1)
                val tx = fx - x0
                val i = y * width + x
                fun channel(shift: Int): Float {
                    fun at(bx: Int, by: Int) = (blocks.argb[by * blocks.columns + bx] shr shift and 0xFF).toFloat()
                    val top = at(x0, y0) + (at(x1, y0) - at(x0, y0)) * tx
                    val bottom = at(x0, y1) + (at(x1, y1) - at(x0, y1)) * tx
                    return top + (bottom - top) * ty
                }
                r[i] = channel(16)
                g[i] = channel(8)
                b[i] = channel(0)
            }
        }
        val radius = max(1, width / BLUR_DIVISOR)
        for (plane in listOf(r, g, b)) repeat(3) { boxBlur(plane, width, height, radius) }
        val alpha = if (overlay == null) 0f else OVERLAY_ALPHA
        val cover = overlay ?: 0
        val or = (cover shr 16 and 0xFF) * alpha
        val og = (cover shr 8 and 0xFF) * alpha
        val ob = (cover and 0xFF) * alpha
        val keep = 1f - alpha
        return IntArray(width * height) { i ->
            argb((r[i] * keep + or).roundToInt(), (g[i] * keep + og).roundToInt(), (b[i] * keep + ob).roundToInt())
        }
    }

    /** One horizontal then one vertical box pass of [radius], edges clamped. */
    private fun boxBlur(plane: FloatArray, width: Int, height: Int, radius: Int) {
        val line = FloatArray(max(width, height))
        fun pass(length: Int, lines: Int, index: (Int, Int) -> Int) {
            for (l in 0 until lines) {
                for (k in 0 until length) line[k] = plane[index(l, k)]
                var sum = 0f
                for (k in -radius..radius) sum += line[k.coerceIn(0, length - 1)]
                for (k in 0 until length) {
                    plane[index(l, k)] = sum / (2 * radius + 1)
                    sum += line[(k + radius + 1).coerceAtMost(length - 1)] - line[(k - radius).coerceAtLeast(0)]
                }
            }
        }
        pass(width, height) { y, x -> y * width + x }
        pass(height, width) { x, y -> y * width + x }
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
