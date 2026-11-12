package com.brewingbytes.gemmory.render

import com.brewingbytes.gemmory.play.CellQuads
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaPaint
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import org.jetbrains.skia.VertexMode

internal actual fun bakedBitmap(width: Int, height: Int): BakedBitmap = SkiaBakedBitmap(width, height)

private class SkiaBakedBitmap(private val width: Int, height: Int) : BakedBitmap {
    // Allocated outside `apply`: inside it, `width` and `height` would be the empty Bitmap's own (0×0).
    private val bitmap = Bitmap().also {
        check(it.allocN32Pixels(width, height, opaque = true)) { "can't allocate a ${width}x$height bake" }
    }
    override val image: ImageBitmap = bitmap.asComposeImageBitmap()

    /** Once per open: one erase per cell is ~15k native calls on the largest canvas, well under a frame's work. */
    override fun setAll(pixels: IntArray) {
        for (cell in pixels.indices) erase(cell, pixels[cell])
        bitmap.notifyPixelsChanged()
    }

    override fun set(cell: Int, argb: Int) {
        erase(cell, argb)
        bitmap.notifyPixelsChanged()
    }

    private fun erase(cell: Int, argb: Int) {
        val x = cell % width
        val y = cell / width
        bitmap.erase(argb, IRect.makeXYWH(x, y, 1, 1))
    }
}

internal actual fun drawCellBatch(canvas: Canvas, quads: CellQuads, paint: Paint) {
    if (quads.count == 0) return
    quads.zeroTail()
    canvas.skiaCanvas.drawVertices(
        VertexMode.TRIANGLES, quads.positions, null, quads.texs, quads.indices, BlendMode.MODULATE,
        paint.skiaPaint,
    )
}

/** Android's BlurMaskFilter turns a radius into Skia's sigma as radius × 0.57735 + 0.5 (SkBlurMask::ConvertRadiusToSigma). */
internal actual fun Paint.blurMask(radiusPx: Float) {
    skiaPaint.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, radiusPx * 0.57735f + 0.5f)
}
