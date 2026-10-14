package com.brewingbytes.gemmory.play

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasZoom
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * The play canvas renderer (eng finding 13, design system Play canvas). Below 8dp cells it draws one baked
 * 1 px-per-cell bitmap of the whole canvas, kept current one pixel per placement. From 8dp it visits only the
 * cells on screen and draws each as its color's cached sprite for the current zoom bucket, with raised edges on
 * placed gems from 8dp (D18), numbers on unplaced cells from 16dp and facets from 20dp. On top: the placement glint
 * (180ms, from 20dp) and the Find pulse (400ms). Main thread only.
 */
class CanvasRenderer(context: Context, private val pattern: Pattern) {
    private val colors = CellColors(pattern)
    private val labels = Array(pattern.palette.size) { (it + 1).toString() }
    private val bakeBitmap = createBitmap(pattern.width, pattern.height)
    private val bake = CanvasBake(pattern, colors, BitmapSink(bakeBitmap, pattern.width))
    private var density = 0f
    private val sprites = SpriteCache<Atlas, Int>(
        pattern.palette.size,
        newBucket = { spec, recycled -> Atlas.of(spec.px, pattern.palette.size, recycled) },
        make = { atlas, spec, slot, kind -> drawSprite(atlas, spec, slot, kind) },
    )
    private val src = Rect()

    private val bakePaint = Paint().apply { isFilterBitmap = false }
    private val spritePaint = Paint().apply { isFilterBitmap = true }
    private val dst = RectF()

    // Sprite drawing.
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seam = Paint()
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
    private val edgeLight = Paint().apply { color = 0x1FFFFFFF }
    private val edgeDark = Paint().apply { color = 0x2E000000 }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        val mono = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_mono) ?: Typeface.MONOSPACE
        typeface = Typeface.create(mono, 700, false)
    }
    private val rect = RectF()
    private val path = Path()

    // Glint: a soft white band swept diagonally across the gem.
    private val glintShader = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glintShader }
    private val glintMatrix = Matrix()

    fun draw(canvas: Canvas, session: PlaySession, density: Float, effects: CanvasEffects, nowNanos: Long) {
        val selectedSlot = session.selectedSlot
        bake.sync(session.placedCount, session::placedAt, session::isPlaced, selectedSlot)
        val viewport = session.viewport
        val cell = viewport.cellPx
        if (cell <= 0f) return
        if (density != this.density) {
            this.density = density
            sprites.clear()
        }
        val ox = viewport.offsetX
        val oy = viewport.offsetY
        val zoomDp = viewport.zoomDp
        if (zoomDp < CanvasZoom.RAISED_MIN_DP) {
            dst.set(ox, oy, ox + pattern.width * cell, oy + pattern.height * cell)
            canvas.drawBitmap(bakeBitmap, null, dst, bakePaint)
            return
        }
        val spec = SpriteSpec.of(zoomDp, cell)
        val col0 = maxOf(0, floor(-ox / cell).toInt())
        val col1 = minOf(pattern.width - 1, floor((viewport.width - ox) / cell).toInt())
        val row0 = maxOf(0, floor(-oy / cell).toInt())
        val row1 = minOf(pattern.height - 1, floor((viewport.height - oy) / cell).toInt())
        val cells = pattern.cells
        for (row in row0..row1) {
            val top = oy + row * cell
            var index = row * pattern.width + col0
            for (col in col0..col1) {
                val slot = cells[index]
                // A direct call, not a stored (Int) -> Boolean: no boxing per visible cell per frame.
                val kind = kindOf(session.isPlaced(index), slot, selectedSlot)
                val left = ox + col * cell
                dst.set(left, top, left + cell, top + cell)
                drawSprite(canvas, spec, slot, kind)
                index++
            }
        }
        if (spec.facets) drawGlints(canvas, viewport, effects, nowNanos)
        drawPulse(canvas, session, spec, effects, nowNanos)
    }

    private fun kindOf(placed: Boolean, slot: Int, selectedSlot: Int): SpriteKind = when {
        placed -> SpriteKind.PLACED
        slot == selectedSlot -> SpriteKind.SELECTED
        else -> SpriteKind.UNPLACED
    }

    private fun drawGlints(canvas: Canvas, viewport: CanvasViewport, effects: CanvasEffects, nowNanos: Long) {
        val cell = viewport.cellPx
        effects.forEachGlint(nowNanos) { index, progress ->
            val left = viewport.offsetX + (index % pattern.width) * cell
            val top = viewport.offsetY + (index / pattern.width) * cell
            if (left > viewport.width || top > viewport.height || left + cell < 0 || top + cell < 0) return@forEachGlint
            val inset = gemInset(cell)
            val size = cell - 2 * inset
            canvas.withSave {
                rect.set(left + inset, top + inset, left + inset + size, top + inset + size)
                path.reset()
                path.addRoundRect(rect, size * GEM_RADIUS, size * GEM_RADIUS, Path.Direction.CW)
                clipPath(path)
                // The band (half the gem wide) crosses from top-left to bottom-right, perpendicular to the diagonal.
                translate(rect.centerX(), rect.centerY())
                rotate(45f)
                val band = size * 0.5f
                val travel = size * 1.42f + band
                val x = -travel / 2f + travel * progress - band / 2f
                glintMatrix.setScale(band, 1f)
                glintMatrix.postTranslate(x, 0f)
                glintShader.setLocalMatrix(glintMatrix)
                drawRect(x, -size, x + band, size, glintPaint)
            }
        }
    }

    /** Find (design D24): the found cell grows ~30% and settles back over 400ms. */
    private fun drawPulse(canvas: Canvas, session: PlaySession, spec: SpriteSpec, effects: CanvasEffects, nowNanos: Long) {
        val (index, progress) = effects.pulse(nowNanos) ?: return
        val viewport = session.viewport
        val cell = viewport.cellPx
        val grow = 1f + PULSE_GROW * sin(PI * progress).toFloat()
        val cx = viewport.offsetX + (index % pattern.width + 0.5f) * cell
        val cy = viewport.offsetY + (index / pattern.width + 0.5f) * cell
        val half = cell * grow / 2f
        dst.set(cx - half, cy - half, cx + half, cy + half)
        val slot = pattern.cells[index]
        drawSprite(canvas, spec, slot, kindOf(session.isPlaced(index), slot, session.selectedSlot))
    }

    /** Draws [slot]'s [kind] sprite into [dst]. */
    private fun drawSprite(canvas: Canvas, spec: SpriteSpec, slot: Int, kind: SpriteKind) {
        val index = sprites.get(spec, slot, kind)
        val atlas = sprites.payload!!
        atlas.source(index, src)
        canvas.drawBitmap(atlas.bitmap, src, dst, spritePaint)
    }

    private fun gemInset(cellPx: Float) = minOf(GEM_INSET_DP * density, cellPx * 0.1f)

    /**
     * One cell at [SpriteSpec.px] into its spot in the bucket's atlas: its seam (also filling the 1px padding, so
     * filtering never bleeds a neighbour in), the cushion, then raised edges, facets or its number.
     */
    private fun drawSprite(atlas: Atlas, spec: SpriteSpec, slot: Int, kind: SpriteKind): Int {
        val index = slot * SpriteCache.KINDS + kind.ordinal
        val canvas = atlas.canvas
        val px = spec.px.toFloat()
        atlas.source(index, src)
        seam.color = colors.seam(slot, kind)
        canvas.drawRect(src.left - 1f, src.top - 1f, src.right + 1f, src.bottom + 1f, seam)
        canvas.withSave {
            translate(src.left.toFloat(), src.top.toFloat())
            val inset = gemInset(px)
            val size = px - 2 * inset
            val radius = size * GEM_RADIUS
            rect.set(inset, inset, inset + size, inset + size)
            fill.color = colors.face(slot, kind)
            drawRoundRect(rect, radius, radius, fill)
            if (kind == SpriteKind.PLACED) {
                if (spec.raised) drawRaisedEdges(this, size, radius)
                if (spec.facets) drawFacets(this, size, radius)
            } else if (spec.numbers) {
                number.color = colors.number(slot, kind)
                number.textSize = px * NUMBER_SCALE
                val baseline = -(number.ascent() + number.descent()) / 2f
                drawText(labels[slot], px / 2f, px / 2f + baseline, number)
            }
        }
        return index
    }

    /** D18: a 1dp top-left edge at 12% white and a 1dp bottom-right edge at 18% black, inside the cushion. */
    private fun drawRaisedEdges(canvas: Canvas, size: Float, radius: Float) {
        val edge = maxOf(1f, EDGE_DP * density)
        val l = rect.left
        val t = rect.top
        canvas.withSave {
            path.reset()
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            clipPath(path)
            drawRect(l, t, l + size, t + edge, edgeLight)
            drawRect(l, t + edge, l + edge, t + size, edgeLight)
            drawRect(l, t + size - edge, l + size, t + size, edgeDark)
            drawRect(l + size - edge, t, l + size, t + size - edge, edgeDark)
        }
    }

    /** Two-facet highlight: top-left triangle at 20% white, bottom-right at 14% black. */
    private fun drawFacets(canvas: Canvas, size: Float, radius: Float) {
        val l = rect.left
        val t = rect.top
        canvas.withSave {
            path.reset()
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            clipPath(path)
            path.reset()
            path.moveTo(l, t)
            path.lineTo(l + size * 0.5f, t)
            path.lineTo(l, t + size * 0.5f)
            path.close()
            drawPath(path, light)
            path.reset()
            path.moveTo(l + size, t + size)
            path.lineTo(l + size, t + size * 0.5f)
            path.lineTo(l + size * 0.5f, t + size)
            path.close()
            drawPath(path, dark)
        }
    }

    /**
     * One zoom bucket's sprites in a single bitmap, so a frame draws from one texture: [COLUMNS] sprites per row,
     * each [px] square inside a 1px padding. A dropped bucket's bitmap is reused when it's big enough.
     */
    private class Atlas(val bitmap: Bitmap, private val px: Int) {
        val canvas = Canvas(bitmap)
        private val pitch = px + 2

        fun source(index: Int, out: Rect) {
            val left = (index % COLUMNS) * pitch + 1
            val top = (index / COLUMNS) * pitch + 1
            out.set(left, top, left + px, top + px)
        }

        companion object {
            const val COLUMNS = 12

            fun of(px: Int, slots: Int, recycled: Atlas?): Atlas {
                val count = slots * SpriteCache.KINDS
                val width = minOf(count, COLUMNS) * (px + 2)
                val height = (count + COLUMNS - 1) / COLUMNS * (px + 2)
                val old = recycled?.bitmap
                val bitmap = if (old != null && old.allocationByteCount >= width * height * 4) {
                    old.reconfigure(width, height, Bitmap.Config.ARGB_8888)
                    old
                } else {
                    createBitmap(width, height)
                }
                return Atlas(bitmap, px)
            }
        }
    }

    private class BitmapSink(private val bitmap: Bitmap, private val width: Int) : PixelSink {
        override fun setAll(pixels: IntArray) = bitmap.setPixels(pixels, 0, width, 0, 0, width, bitmap.height)

        override fun set(cell: Int, argb: Int) = bitmap.setPixel(cell % width, cell / width, argb)
    }

    private companion object {
        const val GEM_INSET_DP = 0.6f
        const val GEM_RADIUS = 0.18f
        const val NUMBER_SCALE = 0.46f
        const val EDGE_DP = 1f
        const val PULSE_GROW = 0.3f
    }
}

/**
 * Short-lived canvas effects: placement glints (design system Motion: 180ms, from 20dp, skipped under reduced
 * motion) and the Find pulse (400ms, skipped under reduced motion). Times are System.nanoTime, the frame clock's base.
 */
class CanvasEffects {
    private val glintCells = ArrayList<Int>()
    private val glintStarts = ArrayList<Long>()
    private var pulseCell = -1
    private var pulseStart = 0L

    fun glint(cell: Int, nowNanos: Long) {
        glintCells += cell
        glintStarts += nowNanos
    }

    fun pulse(cell: Int, nowNanos: Long) {
        pulseCell = cell
        pulseStart = nowNanos
    }

    fun isActive(nowNanos: Long): Boolean {
        prune(nowNanos)
        return glintCells.isNotEmpty() || pulse(nowNanos) != null
    }

    fun forEachGlint(nowNanos: Long, block: (cell: Int, progress: Float) -> Unit) {
        prune(nowNanos)
        for (i in glintCells.indices) {
            block(glintCells[i], ((nowNanos - glintStarts[i]) / GLINT_NANOS.toFloat()).coerceIn(0f, 1f))
        }
    }

    fun pulse(nowNanos: Long): Pair<Int, Float>? {
        if (pulseCell < 0) return null
        val progress = (nowNanos - pulseStart) / PULSE_NANOS.toFloat()
        if (progress >= 1f) {
            pulseCell = -1
            return null
        }
        return pulseCell to progress.coerceAtLeast(0f)
    }

    private fun prune(nowNanos: Long) {
        while (glintStarts.isNotEmpty() && nowNanos - glintStarts[0] >= GLINT_NANOS) {
            glintStarts.removeAt(0)
            glintCells.removeAt(0)
        }
    }

    companion object {
        const val GLINT_NANOS = 180_000_000L
        const val PULSE_NANOS = 400_000_000L
    }
}
