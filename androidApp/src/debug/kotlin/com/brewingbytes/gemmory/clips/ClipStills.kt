package com.brewingbytes.gemmory.clips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.FrameArt
import com.brewingbytes.gemmory.timelapse.FrameLayout
import com.brewingbytes.gemmory.timelapse.FrameStyle
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The founder clip tool's stills (T47). */
object ClipStills {
    /**
     * `<id>-before-after.png`, 1080×1350 (a 4:5 feed post): the picture as flat pixel art (each cell a square of its
     * color) beside the finished gem canvas, on the time-lapse band with its title, app line and watermark row.
     */
    fun beforeAfter(context: Context, request: EncodeRequest): Bitmap {
        val width = 1080
        val height = 1350
        val pattern = request.pattern
        val style = FrameStyle(context, width)
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        style.drawBand(canvas, pattern, height)
        val margin = style.px(FrameStyle.MARGIN)
        val boxW = (width - 2 * margin - GAP) / 2
        val boxH = height - FrameLayout.TEXT_RESERVE - LABEL_ROW - TOP
        val cellPx = max(1, (boxW + pattern.width - 1) / pattern.width)
        val before = flat(pattern, cellPx)
        val after = GemRenderer.render(pattern, cellPx)
        val scale = min(boxW.toFloat() / after.width, boxH.toFloat() / after.height)
        val w = (after.width * scale).roundToInt()
        val h = (after.height * scale).roundToInt()
        // The labels, images and text as one block, centered between the top and the watermark row.
        val top = max(TOP, (height - FrameStyle.WATERMARK_ROW - (LABEL_ROW + h + FrameLayout.TEXT_RESERVE - FrameStyle.WATERMARK_ROW)) / 2)
        val label = style.labelPaint()
        val imageTop = top + LABEL_ROW
        val (beforeLabel, afterLabel) = labels(context)
        for ((i, pair) in listOf(beforeLabel to before, afterLabel to after).withIndex()) {
            val (name, bitmap) = pair
            val left = margin + i * (boxW + GAP) + (boxW - w) / 2
            canvas.drawText(name, left.toFloat(), (imageTop - 14).toFloat(), label)
            // Pixel art stays crisp; the gems are filtered like the share's before/after.
            val paint = if (bitmap === before) Paint() else Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, null, Rect(left, imageTop, left + w, imageTop + h), paint)
        }
        before.recycle()
        after.recycle()
        style.drawText(canvas, request.title, imageTop + h, height, watermark = true)
        return out
    }

    /** `<id>-finished.png`: the finished gem canvas alone, about 1080 wide. */
    fun finished(pattern: Pattern): Bitmap = GemRenderer.render(pattern, max(1, (FINISHED_WIDTH + pattern.width - 1) / pattern.width))

    /** Each cell a flat square of its palette color. */
    private fun flat(pattern: Pattern, cellPx: Int): Bitmap {
        val bitmap = createBitmap(pattern.width * cellPx, pattern.height * cellPx)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (cell in pattern.cells.indices) {
            val x = cell % pattern.width
            val y = cell / pattern.width
            paint.color = pattern.palette[pattern.cells[cell]].argb or (0xFF shl 24)
            canvas.drawRect((x * cellPx).toFloat(), (y * cellPx).toFloat(), ((x + 1) * cellPx).toFloat(), ((y + 1) * cellPx).toFloat(), paint)
        }
        return bitmap
    }

    /** The before/after's two labels in [context]'s language (#152: "After" used to be English in every language). */
    fun labels(context: Context): Pair<String, String> {
        val strings = context.strings
        return strings.text(R.string.frame_before) to strings.text(R.string.frame_after)
    }

    private const val TOP = 48
    private const val LABEL_ROW = 44
    private const val GAP = 24
    private const val FINISHED_WIDTH = 1080
}
