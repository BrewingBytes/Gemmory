package com.brewingbytes.gemmory.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasZoom
import com.brewingbytes.gemmory.play.CanvasBake
import com.brewingbytes.gemmory.play.CanvasEffects
import com.brewingbytes.gemmory.play.CanvasViewport
import com.brewingbytes.gemmory.play.CellColors
import com.brewingbytes.gemmory.play.CellQuads
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.play.SpriteCache
import com.brewingbytes.gemmory.play.SpriteKind
import com.brewingbytes.gemmory.play.SpriteSpec
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** What [CanvasRenderer.draw] reads from the game: the play session on Android, the spike's game on iOS. */
interface CanvasGame {
    val selectedSlot: Int
    val placedCount: Int

    /** The cell placed [index]th, in log order. */
    fun placedAt(index: Int): Int

    fun isPlaced(cell: Int): Boolean
}

/**
 * The play canvas renderer (eng finding 13, design system Play canvas) in common Compose `Canvas` code (iOS Stage 1a,
 * T62), so Android and iOS draw the same. Below 8dp cells it draws one baked 1 px-per-cell bitmap of the whole canvas,
 * kept current one pixel per placement ([bakedBitmap]). From 8dp it visits only the cells on screen and draws them all
 * in one vertex batch (#117, [drawCellBatch]), each as its color's cached sprite in the current zoom bucket's atlas:
 * placed gems as table cuts from 8dp (T40), numbers on unplaced cells from 16dp and the table's catchlight from 20dp.
 * The selected color stands out per the player's [HighlightStyle] (#19): stronger tint, the D17 ring from 8dp, or the
 * other colors dimmed. On top: the placement glint (180ms, from 20dp) and the Find pulse (400ms). A tool's batch (#20)
 * is placed at once and revealed ring by ring: its cells keep their unplaced look until their ring's time.
 *
 * Numbers are laid out by [textMeasurer] in [RenderAssets.numbers] at 700, centered on the font's ascent and descent
 * as Android's `Paint` centers them. Main thread only.
 */
class CanvasRenderer(
    private val pattern: Pattern,
    private val textMeasurer: TextMeasurer,
    private val assets: RenderAssets,
    mystery: Boolean = false,
) {
    private val colors = CellColors(pattern, mystery)
    private val labels = Array(pattern.palette.size) { (it + 1).toString() }
    private val bake = bakedBitmap(pattern.width, pattern.height)
    private val canvasBake = CanvasBake(pattern, colors, bake)
    private var density = 0f
    private var style = HighlightStyle.DEFAULT
    private val sprites = SpriteCache<Atlas, Int>(
        pattern.palette.size,
        newBucket = { spec, recycled -> Atlas.of(spec.px, pattern.palette.size, recycled) },
        make = { atlas, spec, slot, kind -> drawSprite(atlas, spec, slot, kind) },
    )
    private val quads = CellQuads()
    private val src = IntArray(2)

    private val bakePaint = Paint().apply { filterQuality = FilterQuality.None }
    private val spritePaint = Paint().apply { filterQuality = FilterQuality.Low }
    private val fill = Paint()
    private val seam = Paint().apply { isAntiAlias = false }
    private val tableCut = TableCut.Painter()
    private val ringOuter = Paint().apply {
        style = PaintingStyle.Stroke
        color = Color(CellColors.ON_BONE)
    }
    private val ringInner = Paint().apply {
        style = PaintingStyle.Stroke
        color = Color(LAMP)
    }
    private val numberScope = CanvasDrawScope()

    // Glint: a soft white band swept diagonally across the gem. The gradient spans x 0..1 and is drawn scaled to the
    // band, so no shader is made per frame.
    private val glintPaint = Paint().apply {
        shader = LinearGradientShader(
            Offset.Zero, Offset(1f, 0f),
            listOf(Color(0x00FFFFFF), Color(0x66FFFFFF), Color(0x00FFFFFF)),
            listOf(0f, 0.5f, 1f),
            TileMode.Clamp,
        )
    }

    fun draw(
        canvas: Canvas,
        game: CanvasGame,
        viewport: CanvasViewport,
        density: Float,
        style: HighlightStyle,
        effects: CanvasEffects,
        nowNanos: Long,
    ) {
        val selectedSlot = game.selectedSlot
        canvasBake.sync(game.placedCount, game::placedAt, game::isPlaced, selectedSlot, style)
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
            // Compose's common drawImageRect takes whole-pixel destinations, so scale the canvas instead.
            canvas.save()
            canvas.translate(ox, oy)
            canvas.scale(cell, cell)
            canvas.drawImage(bake.image, Offset.Zero, bakePaint)
            canvas.restore()
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
                // A cell the sweep hasn't reached yet still looks like the selected color it was placed as.
                val kind = if (effects.hidden(index, nowNanos)) SpriteKind.SELECTED else kindOf(game.isPlaced(index), slot, selectedSlot)
                atlasSource(spec, slot, kind)
                quads.add(ox + col * cell, top, cell, src[0], src[1], spec.px)
                if (quads.full) drawQuads(canvas)
                index++
            }
        }
        drawQuads(canvas)
        if (spec.facets) drawGlints(canvas, viewport, effects, nowNanos)
        drawPulse(canvas, game, viewport, spec, effects, nowNanos)
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
            canvas.save()
            canvas.clipRect(left, top, left + size, top + size)
            // The band (half the gem wide) crosses from top-left to bottom-right, perpendicular to the diagonal.
            canvas.translate(left + size / 2f, top + size / 2f)
            canvas.rotate(45f)
            val band = size * 0.5f
            val travel = size * 1.42f + band
            val x = -travel / 2f + travel * progress - band / 2f
            canvas.translate(x, 0f)
            canvas.scale(band, 1f)
            canvas.drawRect(0f, -size, 1f, size, glintPaint)
            canvas.restore()
        }
    }

    /** Find (design D24): the found cell grows ~30% and settles back over 400ms. */
    @Suppress("LongParameterList")
    private fun drawPulse(canvas: Canvas, game: CanvasGame, viewport: CanvasViewport, spec: SpriteSpec, effects: CanvasEffects, nowNanos: Long) {
        val (index, progress) = effects.pulse(nowNanos) ?: return
        val cell = viewport.cellPx
        val grow = 1f + PULSE_GROW * sin(PI * progress).toFloat()
        val cx = viewport.offsetX + (index % pattern.width + 0.5f) * cell
        val cy = viewport.offsetY + (index / pattern.width + 0.5f) * cell
        val half = cell * grow / 2f
        val slot = pattern.cells[index]
        atlasSource(spec, slot, kindOf(game.isPlaced(index), slot, game.selectedSlot))
        val atlas = sprites.payload!!
        // Whole-pixel drawImageRect again: draw the sprite at its own size, scaled into place.
        canvas.save()
        canvas.translate(cx - half, cy - half)
        canvas.scale(half * 2f / spec.px, half * 2f / spec.px)
        canvas.drawImageRect(atlas.image, IntOffset(src[0], src[1]), IntSize(spec.px, spec.px), IntOffset.Zero, IntSize(spec.px, spec.px), spritePaint)
        canvas.restore()
    }

    /** Sets [src] to [slot]'s [kind] sprite in the bucket's atlas, drawing it there first if it's new. */
    private fun atlasSource(spec: SpriteSpec, slot: Int, kind: SpriteKind) {
        val index = sprites.get(spec, slot, kind)
        sprites.payload!!.source(index, src)
    }

    /** Draws the batched cells as one op from the bucket's atlas, instead of one drawImage per cell. */
    private fun drawQuads(canvas: Canvas) {
        if (quads.count == 0) return
        drawCellBatch(canvas, quads, sprites.payload!!.paint())
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
        val left = src[0].toFloat()
        val top = src[1].toFloat()
        val ringed = CellColors.ringed(kind, style, spec)
        val look = CellColors.look(kind, style, ringed)
        atlas.changed()
        fill.color = Color(colors.face(slot, look))
        canvas.drawRect(left - 1f, top - 1f, left + px + 1f, top + px + 1f, fill)
        canvas.save()
        canvas.translate(left, top)
        if (kind == SpriteKind.PLACED) {
            if (spec.raised) {
                // The bucket can be up to a step bigger than the cell, so the zoom table's bands come from the spec, not
                // from the sprite's size: 24% from 8dp, 20% from 16dp (with numbers), catchlight from 20dp.
                val bevel = TableCut.bevelPx(px, if (spec.numbers) TableCut.NARROW_DP else TableCut.MIN_DP, density)
                val catchlight = if (spec.facets) TableCut.catchlightPx(TableCut.CATCHLIGHT_DP, density) else 0f
                tableCut.draw(canvas, 0f, 0f, px, bevel, catchlight)
            }
            canvas.restore()
            return index
        }
        if (spec.raised) {
            seam.color = Color(colors.seam(slot, look))
            val line = maxOf(1f, SEAM_DP * density)
            canvas.drawRect(px - line, 0f, px, px, seam)
            canvas.drawRect(0f, px - line, px - line, px, seam)
        }
        if (ringed) drawRing(canvas, px)
        if (spec.numbers) drawNumber(atlas, canvas, slot, colors.number(slot, look), px)
        canvas.restore()
        return index
    }

    private fun drawNumber(atlas: Atlas, canvas: Canvas, slot: Int, argb: Int, px: Float) {
        val size = px * NUMBER_SCALE
        // Laid out once per slot in this bucket, in px (density 1), as Android's Paint.textSize is.
        val layout = atlas.numbers.getOrPut(slot) {
            textMeasurer.measure(
                labels[slot],
                TextStyle(fontFamily = assets.numbers, fontWeight = FontWeight.Bold, fontSize = size.sp),
                density = PX,
            )
        }
        // The baseline goes where Android's Paint puts it, centered on the font's float ascent and descent: Compose's
        // line metrics are whole pixels, which put ~17 px digits a pixel low at 18dp.
        val baseline = px / 2f + (NUMBER_ASCENT - NUMBER_DESCENT) / 2f * size
        val width = layout.advance()
        numberScope.draw(PX, LayoutDirection.Ltr, canvas, Size(px, px)) {
            drawText(layout, color = Color(argb), topLeft = Offset((px - width) / 2f, baseline - layout.firstBaseline))
        }
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
        val ro = maxOf(0f, radius - outer / 2f)
        canvas.drawRoundRect(o, o, px - o, px - o, ro, ro, ringOuter)
        val i = inset + outer + inner / 2f
        val ri = maxOf(0f, radius - outer - inner / 2f)
        canvas.drawRoundRect(i, i, px - i, px - i, ri, ri, ringInner)
    }

    /**
     * One zoom bucket's sprites in a single image, so a frame draws from one texture: [COLUMNS] sprites per row, each
     * [px] square inside a 1px padding.
     */
    private class Atlas(val image: ImageBitmap, private val px: Int) {
        val canvas = Canvas(image)

        /**
         * The numbers laid out for this bucket's sprites, by slot: they go with the bucket, so a pinch across zoom
         * buckets keeps only the two buckets' layouts (#182 review), not every size it passed through.
         */
        val numbers = HashMap<Int, TextLayoutResult>()

        private val paint = Paint().apply { filterQuality = FilterQuality.Low }
        private var stale = true
        private val pitch = px + 2

        /** A sprite was drawn into the atlas, so the shader must sample it again. */
        fun changed() {
            stale = true
        }

        /**
         * Samples the atlas for [drawCellBatch]: texture coordinates are atlas pixels. On iOS a Compose ImageShader is a
         * snapshot of the image when it's made (Skia makes an Image of the bitmap), unlike Android's live
         * BitmapShader, so it's remade after new sprites: only when a zoom bucket fills in, never per frame.
         */
        fun paint(): Paint {
            if (stale) {
                paint.shader = ImageShader(image, TileMode.Clamp, TileMode.Clamp)
                stale = false
            }
            return paint
        }

        /** Writes the sprite's left and top into [out]. */
        fun source(index: Int, out: IntArray) {
            out[0] = (index % COLUMNS) * pitch + 1
            out[1] = (index / COLUMNS) * pitch + 1
        }

        companion object {
            const val COLUMNS = 12

            /**
             * An atlas for [slots] colors' sprites at [px]. It draws into the dropped bucket's bitmap ([recycled]) when
             * that's big enough, as the old Android renderer did with `Bitmap.reconfigure`, so a pinch across zoom
             * buckets doesn't allocate a new atlas every step. Texture coordinates are atlas pixels, so a bigger bitmap
             * works as is, and its old pixels are never sampled: each sprite fills its spot and padding before use.
             */
            fun of(px: Int, slots: Int, recycled: Atlas?): Atlas {
                val count = slots * SpriteCache.KINDS
                val width = minOf(count, COLUMNS) * (px + 2)
                val height = (count + COLUMNS - 1) / COLUMNS * (px + 2)
                val old = recycled?.image
                val image = if (old != null && old.width >= width && old.height >= height) old else ImageBitmap(width, height)
                return Atlas(image, px)
            }
        }
    }

    private companion object {
        val PX = Density(1f, 1f)
        const val RING_INSET_DP = 0.6f
        const val RING_RADIUS = 0.04f
        const val SEAM_DP = 0.5f
        const val NUMBER_SCALE = 0.46f
        const val PULSE_GROW = 0.3f

        /** Atkinson Hyperlegible Mono's hhea ascender and descender per em (984 and -316 of 1000; typo metrics agree). */
        const val NUMBER_ASCENT = 0.984f
        const val NUMBER_DESCENT = 0.316f
        const val RING_OUTER_DP = 1.5f
        const val RING_INNER_DP = 1f
        const val LAMP = 0xFFF4E3B1.toInt()
    }
}
