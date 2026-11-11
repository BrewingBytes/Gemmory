package com.brewingbytes.gemmory.spike

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * The baked 1 px-per-cell canvas (R3-12): Compose has no common pixel write, so each platform keeps its own bitmap
 * and hands it to common code as [image]. A placement writes one pixel ([set]); nothing is re-uploaded whole.
 */
internal interface BakedBitmap : PixelSink {
    val image: ImageBitmap
}

/** A [width] × [height] [BakedBitmap] (eng R3-12's `bakedBitmap`). */
internal expect fun bakedBitmap(width: Int, height: Int): BakedBitmap

/**
 * Draws [quads] as one triangle mesh textured by [paint]'s atlas shader, the #117 batch (R3-12, OV-8). It passes
 * the raw arrays to the platform canvas: Android's `drawVertices` takes counts, and iOS's Skia takes whole arrays,
 * so the tail is zeroed first ([CellQuads.zeroTail]).
 */
internal expect fun drawCellBatch(canvas: Canvas, quads: CellQuads, paint: Paint)

/**
 * A Gaussian blur of what [this] paint draws, with Android's `BlurMaskFilter` radius ([radiusPx]); Compose has no
 * common mask filter. Both platforms are Skia's blur underneath (the end card's shadow, T53).
 */
internal expect fun Paint.blurMask(radiusPx: Float)
