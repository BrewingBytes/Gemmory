package com.brewingbytes.gemmory.timelapse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withTranslation
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.ui.GemRenderer
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The 9:16 time-lapse frame (design system, 9:16 time-lapse frame), drawn at 1080×1920 and scaled by width for
 * smaller targets: the canvas at full width under a 36px top margin, over a band made of the finished canvas
 * blurred and darkened, with the title, the app line and the watermark row.
 *
 * Built once per attempt. The canvas lives in one bitmap that starts as the empty tint grid and gets each frame's
 * new gems drawn onto it, so a whole video costs O(gems + frames) draws (eng finding 12).
 */
class FrameArt(context: Context, private val request: EncodeRequest, val width: Int, val height: Int) {
    private val style = FrameStyle(context, width)
    private val pattern = request.pattern
    private val boardTop = style.px(BOARD_TOP)
    private val boardHeight = (width.toLong() * pattern.height / pattern.width).toInt()
    private val painter = GemRenderer.GemPainter(pattern, width, boardHeight)
    private val board: Bitmap = createBitmap(width, boardHeight)
    private val boardCanvas = Canvas(board)
    private val chrome: Bitmap = createBitmap(width, height)
    private val photo: Bitmap? = request.source?.let { decodeSource(it, width, boardHeight) }
    private var drawn = 0

    /** The canvas's place in the frame. */
    val boardRect = Rect(0, boardTop, width, boardTop + boardHeight)

    /** The static frame behind the canvas: band, title, app line and watermark row. */
    val background: Bitmap get() = chrome

    /** The canvas as of the last [apply]: the tint grid plus every gem placed so far. */
    val canvas: Bitmap get() = board

    /** The cropped source photo at the canvas's size, for the photo intro; null for bundled pictures. */
    val source: Bitmap? get() = photo

    init {
        for (cell in pattern.cells.indices) painter.drawCell(boardCanvas, cell, isPlaced = false)
        val c = Canvas(chrome)
        style.drawBand(c, pattern, height)
        style.drawText(c, request.title, boardTop + boardHeight, height, request.watermark, request.progressLine)
    }

    /**
     * Brings [canvas] up to [frame] by drawing only the gems placed since the last call. Returns whether it changed
     * (so it needs uploading again) and the photo's opacity over it.
     */
    fun apply(frame: Frame): Pair<Boolean, Float> {
        val before = drawn
        when (frame) {
            is Frame.Build -> advanceTo(frame.placed)
            Frame.Hold -> advanceTo(request.log.size)
            is Frame.Intro -> Unit
        }
        val alpha = if (frame is Frame.Intro && photo != null) frame.photoAlpha else 0f
        return (drawn != before) to alpha
    }

    fun release() {
        board.recycle()
        chrome.recycle()
        photo?.recycle()
    }

    private fun advanceTo(placed: Int) {
        val log = request.log
        while (drawn < placed && drawn < log.size) painter.drawCell(boardCanvas, log[drawn++], isPlaced = true)
    }

    companion object {
        const val BOARD_TOP = 36

        /** The cropped source at the canvas's size; null if it's missing or unreadable (the intro is then tints only). */
        fun decodeSource(file: File, width: Int, height: Int): Bitmap? {
            if (!file.isFile) return null
            val decoded = BitmapFactory.decodeFile(file.path) ?: return null
            if (decoded.width == width && decoded.height == height) return decoded
            return decoded.scale(width, height).also { if (it !== decoded) decoded.recycle() }
        }

        /**
         * The before/after picture (eng E1 fallback), 1080×1920: the cropped photo (or, for a bundled picture, the
         * empty tint grid) above the finished canvas, on the same band with the same text. A progress share (#36)
         * shows the canvas as it stands, labelled "So far".
         */
        fun beforeAfter(context: Context, request: EncodeRequest): Bitmap {
            val width = 1080
            val height = 1920
            val style = FrameStyle(context, width)
            val out = createBitmap(width, height)
            val canvas = Canvas(out)
            style.drawBand(canvas, request.pattern, height)
            val pattern = request.pattern
            val cellPx = max(4, (width + pattern.width - 1) / pattern.width)
            val placed = BooleanArray(pattern.cells.size).also { for (cell in request.log) it[cell] = true }
            val after = GemRenderer.render(pattern, cellPx) { placed[it] }
            val before = request.source?.let { decodeSource(it, after.width, after.height) }
                ?: GemRenderer.render(pattern, cellPx) { false }
            val boxW = width - 2 * style.px(FrameStyle.MARGIN)
            val boxH = 640
            val label = style.labelPaint()
            var top = 48
            for ((name, bitmap) in listOf(BEFORE to before, afterLabel(request) to after)) {
                val scale = min(boxW.toFloat() / bitmap.width, boxH.toFloat() / bitmap.height)
                val w = (bitmap.width * scale).roundToInt()
                val h = (bitmap.height * scale).roundToInt()
                val left = (width - w) / 2
                val imageTop = top + 44 + (boxH - h) / 2
                canvas.drawText(name, left.toFloat(), (imageTop - 14).toFloat(), label)
                canvas.drawBitmap(bitmap, null, Rect(left, imageTop, left + w, imageTop + h), Paint(Paint.FILTER_BITMAP_FLAG))
                top += 44 + boxH + 40
            }
            before.recycle()
            after.recycle()
            style.drawText(canvas, request.title, top, height, request.watermark, request.progressLine)
            return out
        }

        const val BEFORE = "Before"

        /** "After" for a finished picture, "So far" for a progress share (#36). */
        fun afterLabel(request: EncodeRequest): String = if (request.inProgress) "So far" else "After"
    }
}

/** Band, type and watermark of the 9:16 frame. Sizes are given at 1080 wide and scaled by [width]. */
internal class FrameStyle(context: Context, private val width: Int) {
    private val s = width / 1080f
    private val bricolage = ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.DEFAULT
    private val figtree = ResourcesCompat.getFont(context, R.font.figtree) ?: Typeface.DEFAULT

    fun px(at1080: Int): Int = (at1080 * s).roundToInt()

    /** The finished canvas scaled to cover the frame, blurred (~40px at 1080) and darkened to 45% brightness. */
    fun drawBand(canvas: Canvas, pattern: Pattern, height: Int) {
        val finished = GemRenderer.render(pattern, cellPx = 2)
        // Blur at 1/8 scale: a 5px box there is ~40px at full size, and upscaling with filtering smooths it.
        val sw = max(1, width / 8)
        val sh = max(1, height / 8)
        val small = createBitmap(sw, sh)
        val cover = max(sw.toFloat() / finished.width, sh.toFloat() / finished.height)
        val dw = finished.width * cover
        val dh = finished.height * cover
        Canvas(small).drawBitmap(
            finished,
            null,
            RectF((sw - dw) / 2f, (sh - dh) / 2f, (sw + dw) / 2f, (sh + dh) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        finished.recycle()
        boxBlur(small, radius = 5, passes = 3)
        val darken = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(BAND_BRIGHTNESS, BAND_BRIGHTNESS, BAND_BRIGHTNESS, 1f) })
        }
        canvas.drawBitmap(small, null, Rect(0, 0, width, height), darken)
        small.recycle()
    }

    /**
     * The title (Bricolage Grotesque 600, 60px, at most 2 lines then an ellipsis) and "Gemmory · Photo Diamond Art"
     * (Figtree 36px) from [textTop], left-aligned at 48px; the watermark row at the bottom (Figtree 600 30px, 70%,
     * bottom-right, over a 25% scrim band), so the title never collides with it. A progress share (#36) has its
     * [progressLine] ("47% done", Figtree 600 36px) between the title and the app line.
     */
    fun drawText(canvas: Canvas, title: String, textTop: Int, height: Int, watermark: Boolean, progressLine: String? = null) {
        val margin = px(MARGIN).toFloat()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bricolage
            fontVariationSettings = "'wght' 600"
            textSize = 60 * s
            letterSpacing = -0.01f
            color = FRAME_TEXT
        }
        val textWidth = (width - 2 * margin).toInt()
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val top = textTop + px(40).toFloat()
        canvas.withTranslation(margin, top) { titleLayout.draw(this) }

        val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = figtree
            textSize = 36 * s
            color = FRAME_TEXT
        }
        var appTop = top + titleLayout.height + px(8)
        if (progressLine != null) {
            val progressPaint = TextPaint(appPaint).apply { fontVariationSettings = "'wght' 600" }
            canvas.drawText(progressLine, margin, appTop - progressPaint.fontMetrics.ascent, progressPaint)
            appTop += progressPaint.fontSpacing + px(2)
        }
        canvas.drawText(APP_LINE, margin, appTop - appPaint.fontMetrics.ascent, appPaint)

        if (!watermark) return
        val rowHeight = px(WATERMARK_ROW)
        val scrim = Paint().apply { color = SCRIM; alpha = (255 * 0.25f).roundToInt() }
        canvas.drawRect(0f, (height - rowHeight).toFloat(), width.toFloat(), height.toFloat(), scrim)
        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = figtree
            fontVariationSettings = "'wght' 600"
            textSize = 30 * s
            color = FRAME_TEXT
            alpha = (255 * 0.7f).roundToInt()
            textAlign = Paint.Align.RIGHT
        }
        val fm = markPaint.fontMetrics
        val baseline = height - rowHeight / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(WATERMARK, width - margin, baseline, markPaint)
    }

    fun labelPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = figtree
        textSize = 30 * s
        color = FRAME_TEXT
    }

    companion object {
        const val MARGIN = 48
        const val WATERMARK_ROW = 78
        const val BAND_BRIGHTNESS = 0.45f
        const val APP_LINE = "Gemmory · Photo Diamond Art"
        const val WATERMARK = "Made with Gemmory"
        private const val FRAME_TEXT = 0xFFF2EEE4.toInt()
        private const val SCRIM = 0xFF141815.toInt()

        /** In place, on a small software bitmap: [passes] horizontal + vertical box blurs approach a Gaussian. */
        fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
            val w = bitmap.width
            val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            repeat(passes) {
                blurLine(px, tmp, w, h, radius, horizontal = true)
                blurLine(tmp, px, w, h, radius, horizontal = false)
            }
            bitmap.setPixels(px, 0, w, 0, 0, w, h)
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
