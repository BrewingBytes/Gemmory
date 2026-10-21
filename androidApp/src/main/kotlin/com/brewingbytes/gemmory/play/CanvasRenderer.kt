package com.brewingbytes.gemmory.play

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
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
import com.brewingbytes.gemmory.ui.TableCut
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * The play canvas renderer (eng finding 13, design system Play canvas). Below 8dp cells it draws one baked
 * 1 px-per-cell bitmap of the whole canvas, kept current one pixel per placement. From 8dp it visits only the
 * cells on screen and draws them all in one vertex batch (#117), each as its color's cached sprite in the current zoom
 * bucket's atlas: placed gems as table cuts
 * from 8dp (T40), numbers on unplaced cells from 16dp and the table's catchlight from 20dp. The selected color stands
 * out per the player's [HighlightStyle] (#19): stronger tint, the D17 ring from 8dp, or the other colors dimmed. On
 * top: the placement glint (180ms, from 20dp) and the Find pulse (400ms). A tool's batch (#20) is placed at once and
 * revealed ring by ring: its cells keep their unplaced look until their ring's time. Main thread only.
 */
class CanvasRenderer(context: Context, private val pattern: Pattern, mystery: Boolean = false) {
    private val colors = CellColors(pattern, mystery)
    private val labels = Array(pattern.palette.size) { (it + 1).toString() }
    private val bakeBitmap = createBitmap(pattern.width, pattern.height)
    private val bake = CanvasBake(pattern, colors, BitmapSink(bakeBitmap, pattern.width))
    private var density = 0f
    private var style = HighlightStyle.DEFAULT
    private val sprites = SpriteCache<Atlas, Int>(
        pattern.palette.size,
        newBucket = { spec, recycled -> Atlas.of(spec.px, pattern.palette.size, recycled) },
        make = { atlas, spec, slot, kind -> drawSprite(atlas, spec, slot, kind) },
    )
    private val src = Rect()
    private val quads = CellQuads()

    private val bakePaint = Paint().apply { isFilterBitmap = false }
    private val spritePaint = Paint().apply { isFilterBitmap = true }
    private val dst = RectF()

    // Sprite drawing.
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seam = Paint()
    private val tableCut = TableCut.Painter()
    private val ringOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CellColors.ON_BONE
    }
    private val ringInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = LAMP
    }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        val mono = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_mono) ?: Typeface.MONOSPACE
        typeface = Typeface.create(mono, 700, false)
    }
    private val ringRect = RectF()

    // Glint: a soft white band swept diagonally across the gem.
    private val glintShader = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glintShader }
    private val glintMatrix = Matrix()

    fun draw(
        canvas: Canvas,
        session: PlaySession,
        density: Float,
        style: HighlightStyle,
        effects: CanvasEffects,
        nowNanos: Long,
    ) {
        val selectedSlot = session.selectedSlot
        bake.sync(session.placedCount, session::placedAt, session::isPlaced, selectedSlot, style)
        val viewport = session.viewport
        val cell = viewport.cellPx
        if (cell <= 0f) return
        if (density != this.density || style != this.style) {
            this.density = density
            this.style = style
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
                // A direct call, not a stored (Int) -> Boolean: no boxing per visible cell per frame. A cell the sweep
                // hasn't reached yet still looks like the selected color it was placed as.
                val kind = if (effects.hidden(index, nowNanos)) SpriteKind.SELECTED else kindOf(session.isPlaced(index), slot, selectedSlot)
                atlasSource(spec, slot, kind)
                quads.add(ox + col * cell, top, cell, src.left, src.top, spec.px)
                if (quads.full) drawQuads(canvas)
                index++
            }
        }
        drawQuads(canvas)
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
            val size = cell
            canvas.withSave {
                clipRect(left, top, left + size, top + size)
                // The band (half the gem wide) crosses from top-left to bottom-right, perpendicular to the diagonal.
                translate(left + size / 2f, top + size / 2f)
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
        val atlas = atlasSource(spec, slot, kind)
        canvas.drawBitmap(atlas.bitmap, src, dst, spritePaint)
    }

    /** Sets [src] to [slot]'s [kind] sprite in the bucket's atlas, drawing it there first if it's new. */
    private fun atlasSource(spec: SpriteSpec, slot: Int, kind: SpriteKind): Atlas {
        val index = sprites.get(spec, slot, kind)
        val atlas = sprites.payload!!
        atlas.source(index, src)
        return atlas
    }

    /** Draws the batched cells as one op from the bucket's atlas, instead of one drawBitmap per cell. */
    private fun drawQuads(canvas: Canvas) {
        if (quads.count == 0) return
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, quads.floatCount, quads.positions, 0, quads.texs, 0, null, 0,
            quads.indices, 0, quads.indexCount, sprites.payload!!.paint,
        )
        quads.reset()
    }

    /**
     * One cell at [SpriteSpec.px] into its spot in the bucket's atlas: its face over the whole cell (also filling the
     * 1px padding, so filtering never bleeds a neighbour in), then a placed gem's table cut, or an unplaced cell's
     * right and bottom seam, the Outline ring and its number.
     */
    private fun drawSprite(atlas: Atlas, spec: SpriteSpec, slot: Int, kind: SpriteKind): Int {
        val index = slot * SpriteCache.KINDS + kind.ordinal
        val canvas = atlas.canvas
        val px = spec.px.toFloat()
        atlas.source(index, src)
        val ringed = CellColors.ringed(kind, style, spec)
        val look = CellColors.look(kind, style, ringed)
        fill.color = colors.face(slot, look)
        canvas.drawRect(src.left - 1f, src.top - 1f, src.right + 1f, src.bottom + 1f, fill)
        // Inside withSave, `density` would be the Canvas's own (an Int).
        val density = density
        canvas.withSave {
            translate(src.left.toFloat(), src.top.toFloat())
            if (kind == SpriteKind.PLACED) {
                if (spec.raised) {
                    // The bucket can be up to a step bigger than the cell, so the zoom table's bands come from the
                    // spec, not from the sprite's size: 24% from 8dp, 20% from 16dp (with numbers), catchlight from 20dp.
                    val bevel = TableCut.bevelPx(px, if (spec.numbers) TableCut.NARROW_DP else TableCut.MIN_DP, density)
                    val catchlight = if (spec.facets) TableCut.catchlightPx(TableCut.CATCHLIGHT_DP, density) else 0f
                    tableCut.draw(this, 0f, 0f, px, bevel, catchlight)
                }
                return@withSave
            }
            if (spec.raised) {
                seam.color = colors.seam(slot, look)
                val line = maxOf(1f, SEAM_DP * density)
                drawRect(px - line, 0f, px, px, seam)
                drawRect(0f, px - line, px - line, px, seam)
            }
            if (ringed) drawRing(this, px)
            if (spec.numbers) {
                number.color = colors.number(slot, look)
                number.textSize = px * NUMBER_SCALE
                val baseline = -(number.ascent() + number.descent()) / 2f
                drawText(labels[slot], px / 2f, px / 2f + baseline, number)
            }
        }
        return index
    }

    /**
     * D17's two-tone ring: a 1.5dp on-bone outer ring 0.6dp inside the cell edge and a 1dp lamp ring inside it, with a
     * 4% corner radius (T40). It stays inside the cell, so the centered number is never covered.
     */
    private fun drawRing(canvas: Canvas, px: Float) {
        val outer = RING_OUTER_DP * density
        val inner = RING_INNER_DP * density
        val inset = minOf(RING_INSET_DP * density, px * 0.1f)
        val radius = px * RING_RADIUS
        ringOuter.strokeWidth = outer
        ringInner.strokeWidth = inner
        val o = inset + outer / 2f
        ringRect.set(o, o, px - o, px - o)
        canvas.drawRoundRect(ringRect, maxOf(0f, radius - outer / 2f), maxOf(0f, radius - outer / 2f), ringOuter)
        val i = inset + outer + inner / 2f
        ringRect.set(i, i, px - i, px - i)
        canvas.drawRoundRect(ringRect, maxOf(0f, radius - outer - inner / 2f), maxOf(0f, radius - outer - inner / 2f), ringInner)
    }

    /**
     * One zoom bucket's sprites in a single bitmap, so a frame draws from one texture: [COLUMNS] sprites per row,
     * each [px] square inside a 1px padding. A dropped bucket's bitmap is reused when it's big enough.
     */
    private class Atlas(val bitmap: Bitmap, private val px: Int) {
        val canvas = Canvas(bitmap)

        /** Samples the atlas for [drawQuads]: texture coordinates are atlas pixels. */
        val paint = Paint().apply {
            isFilterBitmap = true
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
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
        const val RING_INSET_DP = 0.6f
        const val RING_RADIUS = 0.04f
        const val SEAM_DP = 0.5f
        const val NUMBER_SCALE = 0.46f
        const val PULSE_GROW = 0.3f
        const val RING_OUTER_DP = 1.5f
        const val RING_INNER_DP = 1f
        const val LAMP = 0xFFF4E3B1.toInt()
    }
}

/**
 * Short-lived canvas effects: placement glints (design system Motion: 180ms, from 20dp, skipped under reduced
 * motion), the Find pulse (400ms, skipped under reduced motion) and a batch's reveal (#20: its cells show ring by
 * ring, never under reduced motion). Times are System.nanoTime, the frame clock's base.
 */
class CanvasEffects {
    /** Per cell, the time its ring shows, or 0 when it isn't in the running reveal; allocated on the first batch. */
    private var revealAt: LongArray? = null
    private val revealCells = ArrayList<Int>()
    private var revealEnd = 0L

    /**
     * Reveals [cells] (in log order) with their [rings], starting at [startNanos], [ringNanos] apart. A reveal still
     * running is finished first, so its cells show at once.
     */
    fun reveal(cellCount: Int, cells: IntArray, rings: IntArray, startNanos: Long, ringNanos: Long) {
        val at = revealAt ?: LongArray(cellCount).also { revealAt = it }
        clearReveal(at)
        for (i in cells.indices) {
            val time = startNanos + rings[i] * ringNanos
            at[cells[i]] = time
            revealCells += cells[i]
            if (time > revealEnd) revealEnd = time
        }
    }

    /** Whether [cell] is placed but its ring of the running reveal hasn't shown yet. */
    fun hidden(cell: Int, nowNanos: Long): Boolean {
        val at = revealAt ?: return false
        return at[cell] > nowNanos
    }

    private fun clearReveal(at: LongArray) {
        for (cell in revealCells) at[cell] = 0L
        revealCells.clear()
        revealEnd = 0L
    }

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
        revealAt?.let { if (revealCells.isNotEmpty() && nowNanos >= revealEnd) clearReveal(it) }
        return glintCells.isNotEmpty() || pulse(nowNanos) != null || revealCells.isNotEmpty()
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
