package com.brewingbytes.gemmory.streak

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.policy.CardDay
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.AppFonts
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemRenderer
import com.brewingbytes.gemmory.ui.grainBitmap
import kotlin.math.min

/**
 * The milestone share card (#104, G14, mockup streak-d): a 1080×1920 PNG on `felt-deep` with the grain, the tier
 * flame, the streak-hero number, the milestone line, the milestone week (the picture finished each day, else the lit
 * flame; empty outlines after the milestone) and "Gemmory". Sizes are the mockup's at ×4. It is public, so the only
 * art it draws is what its thumbnail loader gives for a [CardDay.Picture], which is never a photo canvas.
 */
class StreakShareCardRenderer(private val context: Context) {
    private val mono = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_mono) ?: Typeface.MONOSPACE
    /** The faces of the app's language (T49): Bricolage Grotesque and Figtree for Latin scripts. */
    private val fonts = AppFonts.forLanguage(context.strings.locale.language)
    private val display = fonts.display.typeface(context)
    private val body = fonts.body.typeface(context)

    /**
     * The card for a streak of [length] days: [title] is the milestone line, [days] the week's seven days and [letters]
     * their weekday letters. [thumbnail] loads a picture's finished art (null draws the flame).
     */
    fun render(length: Int, title: String, days: List<CardDay>, letters: List<String>, thumbnail: (CanvasRef) -> Bitmap?): Bitmap {
        val out = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(out)
        canvas.drawColor(GemColors.FeltDeep.toArgb())
        val grain = grainBitmap()
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) })
        grain.recycle()

        drawFlame(canvas, length, (WIDTH - FLAME) / 2f, TOP.toFloat(), FLAME.toFloat())
        val numberTop = TOP + FLAME + NUMBER_GAP
        val number = textPaint(mono, NUMBER_SIZE, GemColors.Bone).apply {
            fontVariationSettings = "'wght' 700"
            fontFeatureSettings = "tnum"
        }
        centerText(canvas, length.toString(), number, numberTop.toFloat(), NUMBER_SIZE.toFloat())
        val titlePaint = textPaint(display, TITLE_SIZE, GemColors.Bone).apply {
            fonts.display.variation?.let { fontVariationSettings = it }
            // One line at 104px; a longer language's title shrinks to the card's width instead (T49), never below 64px.
            val room = WIDTH - 2 * SIDE
            val wide = measureText(title)
            if (wide > room) textSize = maxOf(TITLE_MIN_SIZE.toFloat(), textSize * room / wide)
        }
        val titleTop = numberTop + NUMBER_SIZE + TITLE_GAP
        val titleHeight = titlePaint.fontSpacing
        centerText(canvas, title, titlePaint, titleTop.toFloat(), titleHeight)

        val weekTop = titleTop + titleHeight + WEEK_GAP
        val cell = (WIDTH - 2 * SIDE - 6 * CELL_GAP) / 7f
        val letterPaint = textPaint(body, LETTER_SIZE, GemColors.TextMuted).apply {
            if (fonts != AppFonts.LATIN) fonts.body.variation?.let { fontVariationSettings = it }
        }
        val letterHeight = letterPaint.fontSpacing
        val cellTop = weekTop + letterHeight + LETTER_GAP
        for ((i, day) in days.withIndex()) {
            val left = SIDE + i * (cell + CELL_GAP)
            letters.getOrNull(i)?.let { centerText(canvas, it, letterPaint, weekTop, letterHeight, left + cell / 2) }
            drawDay(canvas, day, length, RectF(left, cellTop, left + cell, cellTop + cell), thumbnail)
        }

        val brand = textPaint(display, BRAND_SIZE, GemColors.FrameText).apply { fonts.display.variation?.let { fontVariationSettings = it } }
        centerText(canvas, context.strings.text(R.string.app_name), brand, HEIGHT - BOTTOM - brand.fontSpacing, brand.fontSpacing)
        return out
    }

    private fun drawDay(canvas: Canvas, day: CardDay, length: Int, box: RectF, thumbnail: (CanvasRef) -> Bitmap?) {
        val radius = CELL_RADIUS.toFloat()
        if (day == CardDay.Empty) {
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = OUTLINE
                color = GemColors.Bone.copy(alpha = 0.25f).toArgb()
            }
            val inset = OUTLINE / 2
            canvas.drawRoundRect(RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), radius, radius, outline)
            return
        }
        canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GemColors.Felt.toArgb() })
        // A photo canvas is never asked for, whatever the days or the loader say.
        val art = (day as? CardDay.Picture)?.let { picture ->
            CardPictures.refOf(picture.canvas)?.takeIf { it.kind != CanvasKind.PHOTO }?.let(thumbnail)
        }
        if (art == null) {
            val size = CELL_FLAME.toFloat()
            drawFlame(canvas, length, box.centerX() - size / 2, box.centerY() - size / 2, size)
            return
        }
        // Center-cropped square, as the calendar draws a finished day.
        val side = min(art.width, art.height)
        val src = Rect((art.width - side) / 2, (art.height - side) / 2, (art.width + side) / 2, (art.height + side) / 2)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(art, src, box, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** The lit flame in [length]'s tier color (G1), from the widget's drawables (the same paths as [Flame]). */
    private fun drawFlame(canvas: Canvas, length: Int, left: Float, top: Float, size: Float) {
        val res = when {
            length >= 100 -> R.drawable.widget_flame_100
            length >= 30 -> R.drawable.widget_flame_30
            else -> R.drawable.widget_flame
        }
        val drawable = ContextCompat.getDrawable(context, res) ?: return
        drawable.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        drawable.draw(canvas)
    }

    private fun textPaint(face: Typeface, size: Int, color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size.toFloat()
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
    }

    /** [text] centered on [x] and vertically in the box from [top], [height] tall. */
    private fun centerText(canvas: Canvas, text: String, paint: Paint, top: Float, height: Float, x: Float = WIDTH / 2f) {
        val metrics = paint.fontMetrics
        canvas.drawText(text, x, top + height / 2 - (metrics.ascent + metrics.descent) / 2, paint)
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
    }
}

/**
 * Which canvases the share card may show (#104): bundled and remote pictures, drawn finished from their grid. A photo
 * canvas is never drawn (AGENTS.md: user photos never leave the phone).
 */
object CardPictures {
    fun refOf(canvas: String): CanvasRef? = CanvasStore.refOfReminderKey(canvas)

    /** A bundled or remote picture, never a photo canvas: the card's `isPicture`. */
    fun isPicture(canvas: String): Boolean = refOf(canvas)?.let { it.kind != CanvasKind.PHOTO } ?: false

    /** [pattern] finished, at least [minPx] on its short side; null for a photo canvas. */
    fun art(ref: CanvasRef, pattern: Pattern, minPx: Int = CELL_PX): Bitmap? {
        if (ref.kind == CanvasKind.PHOTO) return null
        val cellPx = (minPx + min(pattern.width, pattern.height) - 1) / min(pattern.width, pattern.height)
        return GemRenderer.render(pattern, cellPx)
    }

    /** A week cell is about 111px on the card. */
    private const val CELL_PX = 112
}
