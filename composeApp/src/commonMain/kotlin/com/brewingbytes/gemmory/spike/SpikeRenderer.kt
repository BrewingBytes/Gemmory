package com.brewingbytes.gemmory.spike

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasZoom
import kotlin.math.floor
import kotlin.math.max

/**
 * The Stage 0 spike of the play canvas renderer (T59): androidApp's `CanvasRenderer` in common Compose `Canvas`
 * code, so the same drawing runs on Android and iOS. Below 8dp it draws the baked 1 px-per-cell bitmap
 * ([bakedBitmap]); from 8dp it draws the visible cells as one vertex batch from the zoom bucket's sprite atlas
 * ([drawCellBatch]), with table cuts, seams, the Outline ring and numbers drawn into the atlas as on Android.
 * Left out of the spike: the placement glint, the Find pulse and a tool batch's reveal (Stage 1a ports them).
 *
 * Numbers are laid out by [textMeasurer] in [numberFont] (Atkinson Hyperlegible Mono Bold on the device), centered
 * on the cell's box, which matches Android's centering on ascent and descent.
 */
internal class SpikeRenderer(
    private val pattern: Pattern,
    private val textMeasurer: TextMeasurer,
    private val numberFont: FontFamily,
    mystery: Boolean = false,
) {
    private val colors = CellColors(pattern, mystery)
    private val labels = Array(pattern.palette.size) { (it + 1).toString() }
    val bake = bakedBitmap(pattern.width, pattern.height)
    private val canvasBake = CanvasBake(pattern, colors, bake)
    private var density = 0f
    private var style = HighlightStyle.DEFAULT
    private val sprites = SpriteCache<Atlas, Int>(
        pattern.palette.size,
        newBucket = { spec, _ -> Atlas.of(spec.px, pattern.palette.size) },
        make = { atlas, spec, slot, kind -> drawSprite(atlas, spec, slot, kind) },
    )
    private val quads = CellQuads()
    private val src = IntArray(2)

    private val bakePaint = Paint().apply { filterQuality = FilterQuality.None }
    private val fill = Paint()
    private val seam = Paint().apply { isAntiAlias = false }
    private val tableCut = TableCutPainter()
    private val ringOuter = Paint().apply {
        style = PaintingStyle.Stroke
        color = Color(CellColors.ON_BONE)
    }
    private val ringInner = Paint().apply {
        style = PaintingStyle.Stroke
        color = Color(LAMP)
    }
    private val numberLayouts = HashMap<Long, TextLayoutResult>()

    /** What [draw] needs from the game: the same reads `PlaySession` gives the Android renderer. */
    interface Game {
        val selectedSlot: Int
        val placedCount: Int

        fun placedAt(index: Int): Int

        fun isPlaced(cell: Int): Boolean
    }

    fun draw(canvas: Canvas, game: Game, viewport: CanvasViewport, density: Float, style: HighlightStyle) {
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
                atlasSource(spec, slot, kindOf(game.isPlaced(index), slot, selectedSlot))
                quads.add(ox + col * cell, top, cell, src[0], src[1], spec.px)
                if (quads.full) drawQuads(canvas)
                index++
            }
        }
        drawQuads(canvas)
    }

    private fun kindOf(placed: Boolean, slot: Int, selectedSlot: Int): SpriteKind = when {
        placed -> SpriteKind.PLACED
        slot == selectedSlot -> SpriteKind.SELECTED
        else -> SpriteKind.UNPLACED
    }

    private fun atlasSource(spec: SpriteSpec, slot: Int, kind: SpriteKind) {
        val index = sprites.get(spec, slot, kind)
        sprites.payload!!.source(index, src)
    }

    private fun drawQuads(canvas: Canvas) {
        if (quads.count == 0) return
        drawCellBatch(canvas, quads, sprites.payload!!.paint())
        quads.reset()
    }

    /** One cell at [SpriteSpec.px] into its spot in the bucket's atlas, as androidApp's `CanvasRenderer.drawSprite`. */
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
                val bevel = TableCutPainter.bevelPx(px, if (spec.numbers) TableCutPainter.NARROW_DP else TableCutPainter.MIN_DP, density)
                val catchlight = if (spec.facets) TableCutPainter.catchlightPx(TableCutPainter.CATCHLIGHT_DP, density) else 0f
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
        if (spec.numbers) drawNumber(canvas, slot, colors.number(slot, look), px)
        canvas.restore()
        return index
    }

    private fun drawNumber(canvas: Canvas, slot: Int, argb: Int, px: Float) {
        val size = px * NUMBER_SCALE
        // Laid out once per slot and size, in px (density 1), as Android's Paint.textSize is.
        val key = (slot.toLong() shl 32) or size.toRawBits().toLong().and(0xFFFFFFFFL)
        val layout = numberLayouts.getOrPut(key) {
            textMeasurer.measure(
                labels[slot],
                TextStyle(fontFamily = numberFont, fontWeight = FontWeight.Bold, fontSize = size.sp),
                density = PX,
            )
        }
        // The baseline goes where Android's Paint puts it, centered on the font's float ascent and descent: Compose's
        // line metrics are whole pixels, which put ~17 px digits a pixel low at 18dp.
        val baseline = px / 2f + (NUMBER_ASCENT - NUMBER_DESCENT) / 2f * size
        val width = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        CanvasDrawScope().draw(PX, LayoutDirection.Ltr, canvas, Size(px, px)) {
            drawText(layout, color = Color(argb), topLeft = Offset((px - width) / 2f, baseline - layout.firstBaseline))
        }
    }

    /** D17's two-tone ring, as androidApp's `CanvasRenderer.drawRing`. */
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

    /** One zoom bucket's sprites in a single image, as androidApp's `CanvasRenderer.Atlas`. */
    private class Atlas(val image: ImageBitmap, private val px: Int) {
        val canvas = Canvas(image)

        private val paint = Paint().apply { filterQuality = FilterQuality.Low }
        private var stale = true

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
        private val pitch = px + 2

        /** Writes the sprite's left and top into [out]. */
        fun source(index: Int, out: IntArray) {
            out[0] = (index % COLUMNS) * pitch + 1
            out[1] = (index / COLUMNS) * pitch + 1
        }

        companion object {
            const val COLUMNS = 12

            fun of(px: Int, slots: Int): Atlas {
                val count = slots * SpriteCache.KINDS
                val width = minOf(count, COLUMNS) * (px + 2)
                val height = (count + COLUMNS - 1) / COLUMNS * (px + 2)
                return Atlas(ImageBitmap(width, height), px)
            }
        }
    }

    private companion object {
        val PX = Density(1f, 1f)
        const val RING_INSET_DP = 0.6f
        const val RING_RADIUS = 0.04f
        const val SEAM_DP = 0.5f
        const val NUMBER_SCALE = 0.46f

        /** Atkinson Hyperlegible Mono's hhea ascender and descender per em (984 and -316 of 1000; typo metrics agree). */
        const val NUMBER_ASCENT = 0.984f
        const val NUMBER_DESCENT = 0.316f
        const val RING_OUTER_DP = 1.5f
        const val RING_INNER_DP = 1f
        const val LAMP = 0xFFF4E3B1.toInt()
    }
}

/** androidApp's `TableCut` (T40) in common code: the bevels and catchlight of a square gem whose face is filled. */
internal class TableCutPainter {
    private val path = Path()
    private val top = Paint().apply { color = Color(TOP) }
    private val left = Paint().apply { color = Color(LEFT) }
    private val right = Paint().apply { color = Color(RIGHT) }
    private val bottom = Paint().apply { color = Color(BOTTOM) }
    private val catchlight = Paint().apply { color = Color(CATCHLIGHT) }

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
    private fun quad(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, paint: Paint) {
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        canvas.drawPath(path, paint)
    }

    companion object {
        const val MIN_DP = 8f
        const val NARROW_DP = 16f
        const val CATCHLIGHT_DP = 20f
        private const val MIN_BEVEL_DP = 1.2f
        private const val CATCHLIGHT_THICK_DP = 0.8f
        const val TOP = 0x3DFFFFFF
        const val LEFT = 0x17FFFFFF
        const val RIGHT = 0x24000000
        const val BOTTOM = 0x3D000000
        const val CATCHLIGHT = 0x61FFFFFF

        fun bevelPx(cellPx: Float, cellDp: Float, density: Float): Float =
            if (cellDp < MIN_DP) 0f else max(MIN_BEVEL_DP * density, cellPx * if (cellDp >= NARROW_DP) 0.20f else 0.24f)

        fun catchlightPx(cellDp: Float, density: Float): Float =
            if (cellDp < CATCHLIGHT_DP) 0f else max(1f, CATCHLIGHT_THICK_DP * density)
    }
}
