package com.brewingbytes.gemmory.spike

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativePaint
import android.graphics.Bitmap

internal actual fun bakedBitmap(width: Int, height: Int): BakedBitmap = AndroidBakedBitmap(width, height)

private class AndroidBakedBitmap(private val width: Int, height: Int) : BakedBitmap {
    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    override val image: ImageBitmap = bitmap.asImageBitmap()

    override fun setAll(pixels: IntArray) = bitmap.setPixels(pixels, 0, width, 0, 0, width, bitmap.height)

    override fun set(cell: Int, argb: Int) = bitmap.setPixel(cell % width, cell / width, argb)
}

internal actual fun drawCellBatch(canvas: Canvas, quads: CellQuads, paint: Paint) {
    if (quads.count == 0) return
    canvas.nativeCanvas.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES, quads.floatCount, quads.positions, 0, quads.texs, 0, null, 0,
        quads.indices, 0, quads.indexCount, paint.nativePaint,
    )
}

internal actual fun Paint.blurMask(radiusPx: Float) {
    nativePaint.maskFilter = android.graphics.BlurMaskFilter(radiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
}
