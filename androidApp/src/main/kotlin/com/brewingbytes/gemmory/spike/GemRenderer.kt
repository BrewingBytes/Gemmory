package com.brewingbytes.gemmory.spike

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a converted pattern as square cushion-cut gems (design system: Shapes). */
object GemRenderer {
    private const val FELT_DEEP = 0xFF3E4741.toInt()
    private const val FRAME_TEXT = 0xFFF2EEE4.toInt()
    private const val TEXT_MUTED = 0xFFCDD3C9.toInt()

    /**
     * Every cell as a placed gem: 18% corner radius and a small inset. The seam behind each gem is that gem's
     * own color, slightly darker, so the grid reads without dark gaps (a felt-colored seam reads as a mesh),
     * and the two-facet highlight when [cellPx] is at least 20.
     */
    fun render(pattern: Pattern, cellPx: Int): Bitmap {
        val bitmap = createBitmap(pattern.width * cellPx, pattern.height * cellPx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(FELT_DEEP)
        val inset = max(0.5f, cellPx * 0.025f)
        val radius = (cellPx - 2 * inset) * 0.18f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val seam = Paint()
        val seamColors = IntArray(pattern.palette.size) { darken(pattern.palette[it].argb, SEAM_SHADE) }
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
        val facets = cellPx >= 20
        val rect = RectF()
        val path = Path()
        for (y in 0 until pattern.height) {
            for (x in 0 until pattern.width) {
                val left = x * cellPx + inset
                val top = y * cellPx + inset
                val size = cellPx - 2 * inset
                val slot = pattern.slotAt(x, y)
                seam.color = seamColors[slot]
                canvas.drawRect(
                    (x * cellPx).toFloat(), (y * cellPx).toFloat(), ((x + 1) * cellPx).toFloat(), ((y + 1) * cellPx).toFloat(), seam,
                )
                rect.set(left, top, left + size, top + size)
                fill.color = pattern.palette[slot].argb
                canvas.drawRoundRect(rect, radius, radius, fill)
                if (facets) {
                    canvas.withSave {
                        path.reset()
                        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
                        clipPath(path)
                        path.reset()
                        path.moveTo(left, top)
                        path.lineTo(left + size * 0.5f, top)
                        path.lineTo(left, top + size * 0.5f)
                        path.close()
                        drawPath(path, light)
                        path.reset()
                        path.moveTo(left + size, top + size)
                        path.lineTo(left + size, top + size * 0.5f)
                        path.lineTo(left + size * 0.5f, top + size)
                        path.close()
                        drawPath(path, shade)
                    }
                }
            }
        }
        return bitmap
    }

    private const val SEAM_SHADE = 0.8f

    private fun darken(argb: Int, factor: Float): Int {
        val r = (((argb shr 16) and 0xFF) * factor).toInt()
        val g = (((argb shr 8) and 0xFF) * factor).toInt()
        val b = ((argb and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 1080×1920 before/after: the cropped photo on top, the gem canvas below, one caption line.
     * Same frame size as the time-lapse fallback PNG (eng E1).
     */
    fun beforeAfter(context: Context, photo: Bitmap, pattern: Pattern, caption: String): Bitmap {
        val out = createBitmap(1080, 1920)
        val canvas = Canvas(out)
        canvas.drawColor(FELT_DEEP)

        val figtree = ResourcesCompat.getFont(context, R.font.figtree) ?: Typeface.DEFAULT
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = figtree
            textSize = 30f
            color = TEXT_MUTED
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(figtree, 600, false)
            textSize = 36f
            color = FRAME_TEXT
        }
        val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val boxW = 1008
        val boxH = 800
        drawPanel(canvas, photo, "Before", top = 48, boxW, boxH, image, labelPaint)
        val cellPx = max(8, (boxW + pattern.width - 1) / pattern.width)
        val gems = render(pattern, cellPx)
        drawPanel(canvas, gems, "After", top = 48 + boxH + 96, boxW, boxH, image, labelPaint)
        gems.recycle()
        canvas.drawText(caption, 48f, 1920f - 72f, captionPaint)
        return out
    }

    private fun drawPanel(canvas: Canvas, src: Bitmap, label: String, top: Int, boxW: Int, boxH: Int, paint: Paint, labelPaint: Paint) {
        val scale = min(boxW.toFloat() / src.width, boxH.toFloat() / src.height)
        val w = (src.width * scale).roundToInt()
        val h = (src.height * scale).roundToInt()
        val left = (1080 - w) / 2
        val panelTop = top + 44 + (boxH - h) / 2
        canvas.drawText(label, left.toFloat(), (panelTop - 14).toFloat(), labelPaint)
        canvas.drawBitmap(src, null, Rect(left, panelTop, left + w, panelTop + h), paint)
    }

    /** Writes the PNG to the phone's own Pictures/Gemmory folder. Nothing is uploaded. */
    fun saveToPictures(context: Context, bitmap: Bitmap, displayName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Gemmory")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert returned null")
        try {
            resolver.openOutputStream(uri).use { stream ->
                checkNotNull(stream) { "no output stream" }
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encode failed" }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
