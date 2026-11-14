package com.brewingbytes.gemmory.render

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.brewingbytes.gemmory.policy.CardDay
import kotlin.math.min
import kotlin.random.Random

/**
 * The milestone share card (#104, G14, mockup streak-d) in common code (iOS Stage 1a, T62): a 1080×1920 image on
 * `felt-deep` with the grain, the tier flame, the streak-hero number, the milestone line, the milestone week (the
 * picture finished each day, else the lit flame; empty outlines after the milestone) and "Gemmory". Sizes are the
 * mockup's at ×4. It is public, so the only art it draws is what [render]'s `art` gives, which the platform keeps to
 * bundled and remote pictures (AGENTS.md: user photos never leave the phone).
 */
class StreakCard(private val assets: ArtAssets, measurer: TextMeasurer) {
    private val faces = assets.faces
    private val text = DrawnText(measurer, assets.language)

    /**
     * The card for a streak of [length] days: [title] is the milestone line, [days] the week's seven days and [letters]
     * their weekday letters. [art] gives a picture day's finished art (null draws the flame).
     */
    fun render(length: Int, title: String, days: List<CardDay>, letters: List<String>, art: (CardDay.Picture) -> ImageBitmap?): ImageBitmap {
        val out = ImageBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(out)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = Color(FELT_DEEP) })
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { shader = ImageShader(grain(), TileMode.Repeated, TileMode.Repeated) })

        drawFlame(canvas, length, (WIDTH - FLAME) / 2f, TOP.toFloat(), FLAME.toFloat())
        val numberTop = TOP + FLAME + NUMBER_GAP
        val number = DrawnText.Pen(Face(assets.numbers, FontWeight.Bold, NUMBER_METRICS), NUMBER_SIZE.toFloat(), BONE)
        centerText(canvas, length.toString(), number, numberTop.toFloat(), NUMBER_SIZE.toFloat())
        // One line at 104px; a longer language's title shrinks to the card's width instead (T49), never below 64px.
        var titlePen = DrawnText.Pen(faces.display, TITLE_SIZE.toFloat(), BONE)
        val room = WIDTH - 2 * SIDE
        val wide = text.width(title, titlePen)
        if (wide > room) titlePen = titlePen.withSize(maxOf(TITLE_MIN_SIZE.toFloat(), titlePen.sizePx * room / wide))
        val titleTop = numberTop + NUMBER_SIZE + TITLE_GAP
        val titleHeight = titlePen.metrics.spacing
        centerText(canvas, title, titlePen, titleTop.toFloat(), titleHeight)

        val weekTop = titleTop + titleHeight + WEEK_GAP
        val cell = (WIDTH - 2 * SIDE - 6 * CELL_GAP) / 7f
        val letterPen = DrawnText.Pen(faces.body, LETTER_SIZE.toFloat(), TEXT_MUTED)
        val letterHeight = letterPen.metrics.spacing
        val cellTop = weekTop + letterHeight + LETTER_GAP
        for ((i, day) in days.withIndex()) {
            val left = SIDE + i * (cell + CELL_GAP)
            letters.getOrNull(i)?.let { centerText(canvas, it, letterPen, weekTop, letterHeight, left + cell / 2) }
            drawDay(canvas, day, length, Rect(left, cellTop, left + cell, cellTop + cell), art)
        }

        val brand = DrawnText.Pen(faces.display, BRAND_SIZE.toFloat(), FrameStyle.FRAME_TEXT)
        val spacing = brand.metrics.spacing
        centerText(canvas, assets.words.appName, brand, HEIGHT - BOTTOM - spacing, spacing)
        return out
    }

    private fun drawDay(canvas: Canvas, day: CardDay, length: Int, box: Rect, art: (CardDay.Picture) -> ImageBitmap?) {
        val radius = CELL_RADIUS.toFloat()
        if (day == CardDay.Empty) {
            val outline = Paint().apply {
                style = PaintingStyle.Stroke
                strokeWidth = OUTLINE
                color = Color(BONE).copy(alpha = 0.25f)
            }
            val inset = OUTLINE / 2
            canvas.drawRoundRect(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset, radius, radius, outline)
            return
        }
        canvas.drawRoundRect(box.left, box.top, box.right, box.bottom, radius, radius, Paint().apply { color = Color(FELT) })
        val image = (day as? CardDay.Picture)?.let(art)
        if (image == null) {
            val size = CELL_FLAME.toFloat()
            drawFlame(canvas, length, box.center.x - size / 2, box.center.y - size / 2, size)
            return
        }
        // Center-cropped square, as the calendar draws a finished day.
        val side = min(image.width, image.height)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(RoundRect(box, radius, radius)) })
        canvas.translate(box.left, box.top)
        canvas.scale(box.width / side, box.height / side)
        canvas.drawImageRect(
            image, IntOffset((image.width - side) / 2, (image.height - side) / 2), IntSize(side, side), IntOffset.Zero, IntSize(side, side),
            Paint().apply { filterQuality = FilterQuality.Low },
        )
        canvas.restore()
    }

    /**
     * The lit flame in [length]'s tier color (G1) at the whole-pixel bounds the widget's drawable was given, from the
     * same paths as androidApp's `Flame`.
     */
    private fun drawFlame(canvas: Canvas, length: Int, left: Float, top: Float, size: Float) {
        val l = left.toInt()
        val t = top.toInt()
        val w = (left + size).toInt() - l
        val h = (top + size).toInt() - t
        canvas.save()
        canvas.translate(l.toFloat(), t.toFloat())
        canvas.scale(w / 24f, h / 24f)
        canvas.drawPath(FlamePaths.outer, Paint().apply { color = Color(flameColor(length)) })
        canvas.drawPath(FlamePaths.core, Paint().apply { color = Color(FLAME_CORE) })
        canvas.restore()
    }

    /** [text] centered on [x] and vertically in the box from [top], [height] tall. */
    private fun centerText(canvas: Canvas, text: String, pen: DrawnText.Pen, top: Float, height: Float, x: Float = WIDTH / 2f) {
        val m = pen.metrics
        this.text.draw(canvas, text, pen, x, top + height / 2 - (m.ascent + m.descent) / 2, DrawnText.Align.CENTER)
    }

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920

        // The mockup's card (270×480) at ×4.
        private const val TOP = 224
        private const val SIDE = 80f
        private const val BOTTOM = 96
        private const val FLAME = 440
        private const val NUMBER_GAP = 48
        private const val NUMBER_SIZE = 384
        private const val TITLE_GAP = 16
        private const val TITLE_SIZE = 104
        private const val TITLE_MIN_SIZE = 64
        private const val WEEK_GAP = 88
        private const val LETTER_SIZE = 48
        private const val LETTER_GAP = 24
        private const val CELL_GAP = 24f
        private const val CELL_RADIUS = 8
        private const val CELL_FLAME = 88
        private const val OUTLINE = 4f
        private const val BRAND_SIZE = 64

        // Design system tokens (androidApp's GemColors).
        private const val FELT = 0xFF4F5A52.toInt()
        private const val FELT_DEEP = 0xFF3E4741.toInt()
        private const val BONE = 0xFFE9E4D8.toInt()
        private const val TEXT_MUTED = 0xFFCDD3C9.toInt()
        private const val FLAME_CORE = 0xFFFCE3A6.toInt()

        /** Atkinson Hyperlegible Mono's hhea ascender, descender and head bounds per em (see `CanvasRenderer`). */
        private val NUMBER_METRICS = LineMetrics.perEm(ascent = 0.984f, descent = 0.316f, lineGap = 0f, yMax = 0.879f, yMin = -0.232f)

        /** The lit flame's color for a streak of [length] days (G1): amber, orange from 30, red-orange from 100. */
        fun flameColor(length: Int): Int = when {
            length >= 100 -> 0xFFE0563B.toInt()
            length >= 30 -> 0xFFF08A2C.toInt()
            else -> 0xFFF5A623.toInt()
        }

        /** The felt's ~9% grain, as androidApp's `grainBitmap`: 96px of seeded noise at alpha 23. */
        fun grain(): ImageBitmap {
            val size = 96
            val random = Random(0x6E6D)
            val pixels = IntArray(size * size) {
                val v = random.nextInt(256)
                (23 shl 24) or (v shl 16) or (v shl 8) or v
            }
            return bakedBitmap(size, size).apply { setAll(pixels) }.image
        }
    }
}

/** The flame's two shapes on a 24-unit square (mockup v3), as androidApp's `Flame` and the widget's drawables. */
private object FlamePaths {
    val outer: Path = PathParser().parsePathString(
        "M12 2c1 3.5-1.5 5-1.5 7.5 0 1.6 1.2 2.6 2.4 2.6 1.8 0 2.6-1.6 2.1-3.6C17.6 10.4 19 13 19 15.3 19 19 15.9 22 12 22s-7-3-7-6.7c0-4.4 4.5-6.2 7-13.3z",
    ).toPath()
    val core: Path = PathParser().parsePathString(
        "M12 13.5c.4 1.4-.8 2.2-.8 3.4 0 .9.7 1.5 1.5 1.5 1.2 0 1.9-1 1.6-2.4.9.7 1.4 1.7 1.4 2.6 0 1.9-1.6 3.4-3.6 3.4s-3.6-1.5-3.6-3.4c0-2.2 2.2-3.2 3.5-5.1z",
    ).toPath()
}
