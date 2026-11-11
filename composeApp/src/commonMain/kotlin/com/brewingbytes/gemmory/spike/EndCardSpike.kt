package com.brewingbytes.gemmory.spike

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.timelapse.ShareLayout
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Stage 0 end-card spike (T59): androidApp's `FrameStyle.drawBand` and `drawCard` (the non-VIP player share's
 * end card, T53) in common code, so the time-lapse's layers draw the same on Android and iOS. Compositing stays per
 * platform (eng R1-4). Left out: the Play badge, which is Android-only until Stage 5 (CEO D13).
 */
internal class EndCardSpike(
    private val width: Int,
    private val height: Int,
    private val measurer: TextMeasurer,
    /** Bricolage Grotesque 600 (Latin display). */
    private val display: FontFamily,
    /** Figtree at its default instance (Latin body). */
    private val body: FontFamily,
) {
    private val s = width / 1080f

    fun px(at1080: Int): Int = (at1080 * s).roundToInt()

    /** The finished canvas scaled to cover the frame, blurred (~40px at 1080) and darkened to 45% brightness. */
    fun band(pattern: Pattern): ImageBitmap {
        val finished = finished(pattern)
        val sw = max(1, width / 8)
        val sh = max(1, height / 8)
        val small = ImageBitmap(sw, sh)
        val cover = max(sw.toFloat() / finished.width, sh.toFloat() / finished.height)
        Canvas(small).apply {
            save()
            translate((sw - finished.width * cover) / 2f, (sh - finished.height * cover) / 2f)
            scale(cover, cover)
            drawImage(finished, Offset.Zero, Paint().apply { filterQuality = FilterQuality.Low })
            restore()
        }
        val pixels = IntArray(sw * sh).also { small.readPixels(it) }
        boxBlur(pixels, sw, sh, radius = 5, passes = 3)
        val blurred = bakedBitmap(sw, sh).apply { setAll(pixels) }.image
        val out = ImageBitmap(width, height)
        Canvas(out).apply {
            save()
            scale(width.toFloat() / sw, height.toFloat() / sh)
            drawImage(
                blurred,
                Offset.Zero,
                Paint().apply {
                    filterQuality = FilterQuality.Low
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(BAND, BAND, BAND, 1f) })
                },
            )
            restore()
        }
        return out
    }

    /** `GemRenderer.render(pattern, cellPx = 2)`: every cell a flat 2px square on felt-deep. */
    private fun finished(pattern: Pattern): ImageBitmap {
        val image = ImageBitmap(pattern.width * 2, pattern.height * 2)
        val canvas = Canvas(image)
        val paint = Paint().apply { isAntiAlias = false }
        paint.color = Color(FELT_DEEP)
        canvas.drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), paint)
        for (cell in pattern.cells.indices) {
            val x = (cell % pattern.width) * 2f
            val y = (cell / pattern.width) * 2f
            paint.color = Color(pattern.palette[pattern.cells[cell]].argb or (0xFF shl 24))
            canvas.drawRect(x, y, x + 2f, y + 2f, paint)
        }
        return image
    }

    /** Where the card layer sits in the frame: from the shadow's room above the picture to the card's bottom. */
    fun cardTop(card: ShareLayout.Card): Int = card.top - px(CARD_SHADOW_ROOM)

    /**
     * The end card's layer: the picture's shadow, the icon, "Gemmory" (Bricolage Grotesque 600, 104px) and the tagline
     * (Figtree 44px), centered, as androidApp's `FrameStyle.drawCard` (without the badge).
     */
    fun card(card: ShareLayout.Card, icon: ImageBitmap?, name: String, tagline: String): ImageBitmap {
        val top = cardTop(card)
        val image = ImageBitmap(width, card.bottom - top)
        val canvas = Canvas(image)
        canvas.translate(0f, -top.toFloat())
        val p = card.picture
        val shadow = Paint().apply {
            color = Color(0x80000000.toInt())
            blurMask(px(CARD_SHADOW_BLUR).toFloat())
        }
        val dy = px(CARD_SHADOW_Y).toFloat()
        canvas.drawRect(p.left.toFloat(), p.top + dy, p.right.toFloat(), p.bottom + dy, shadow)
        if (icon != null) {
            val at = card.icon
            val rect = Rect(at.left.toFloat(), at.top.toFloat(), at.right.toFloat(), at.bottom.toFloat())
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(RoundRect(rect, card.iconRadius.toFloat(), card.iconRadius.toFloat())) })
            canvas.save()
            canvas.translate(rect.left, rect.top)
            canvas.scale(rect.width / icon.width, rect.height / icon.height)
            canvas.drawImage(icon, Offset.Zero, Paint().apply { filterQuality = FilterQuality.Low })
            canvas.restore()
            canvas.restore()
        }
        centered(canvas, name, display, 104, card.nameBaseline)
        centered(canvas, tagline, body, 44, card.taglineBaseline)
        return image
    }

    private fun centered(canvas: Canvas, text: String, family: FontFamily, size: Int, baseline: Int) {
        val layout = measurer.measure(text, TextStyle(fontFamily = family, fontSize = (size * s).sp), density = PX)
        val w = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        CanvasDrawScope().draw(PX, LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawText(layout, color = Color(FRAME_TEXT), topLeft = Offset(width / 2f - w / 2f, baseline - layout.firstBaseline))
        }
    }

    companion object {
        private val PX = Density(1f, 1f)
        const val BAND = 0.45f
        const val CARD_SHADOW_BLUR = 52
        const val CARD_SHADOW_Y = 24
        const val CARD_SHADOW_ROOM = 100
        const val FELT_DEEP = 0xFF3E4741.toInt()
        const val FRAME_TEXT = 0xFFF2EEE4.toInt()

        /** androidApp's `FrameStyle.boxBlur`: [passes] box blurs of [radius] each way, clamped at the edges. */
        fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int) {
            val tmp = IntArray(w * h)
            repeat(passes) {
                blurLine(px, tmp, w, h, radius, horizontal = true)
                blurLine(tmp, px, w, h, radius, horizontal = false)
            }
        }

        private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val length = if (horizontal) w else h
            val window = 2 * radius + 1
            for (line in 0 until lines) {
                fun at(i: Int): Int {
                    val c = i.coerceIn(0, length - 1)
                    return if (horizontal) src[line * w + c] else src[c * w + line]
                }
                var r = 0
                var g = 0
                var b = 0
                for (i in -radius..radius) {
                    val p = at(i)
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                }
                for (i in 0 until length) {
                    val out = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                    if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                    val add = at(i + radius + 1)
                    val drop = at(i - radius)
                    r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                    g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                    b += (add and 0xFF) - (drop and 0xFF)
                }
            }
        }
    }
}
