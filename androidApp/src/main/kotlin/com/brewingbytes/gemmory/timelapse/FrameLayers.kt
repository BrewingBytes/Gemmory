package com.brewingbytes.gemmory.timelapse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.render.FrameRequest
import com.brewingbytes.gemmory.ui.AppArtAssets
import com.brewingbytes.gemmory.render.FrameArt as SharedFrameArt

/**
 * One time-lapse's layers as [GlFrameWriter] uploads them: [FrameArt]'s or, since iOS Stage 1a (T62), the shared
 * `render.FrameArt`'s. [of] picks by `BuildConfig.SHARED_RENDERER`, the hotfix switch (R10-8).
 */
interface FrameLayers {
    /** The static frame behind the canvas: the band. */
    val background: Bitmap

    /** The canvas as of the last [apply]. */
    val canvas: Bitmap

    /** Drawn over the canvas at the photo's opacity: the photo, or a founder clip's hook; null otherwise. */
    val source: Bitmap?

    /** The title, app line and row (player's share), or the title and lockup (founder clip), at [overlayRect]. */
    val overlay: Bitmap?
    val overlayRect: Rect?

    /** The end card's layer; null without an end card. */
    val card: Bitmap?

    /** The canvas's place in the frame. */
    val boardRect: Rect

    /** Brings [canvas] up to [frame]: whether it changed, and [source]'s opacity. */
    fun apply(frame: Frame): Pair<Boolean, Float>

    /** Where the end card draws at [alpha]; null without one. */
    fun endCard(alpha: Float): EndCardRects?

    fun release()

    companion object {
        fun of(context: Context, request: EncodeRequest, width: Int, height: Int): FrameLayers =
            if (BuildConfig.SHARED_RENDERER) SharedFrameLayers(context, request, width, height) else FrameArt(context, request, width, height)

        /** The before/after picture (eng E1 fallback), drawn by the renderer the switch picks. */
        fun beforeAfter(context: Context, request: EncodeRequest): Bitmap {
            if (!BuildConfig.SHARED_RENDERER) return FrameArt.beforeAfter(context, request)
            return SharedFrameArt.beforeAfter(
                request.frameRequest(),
                AppArtAssets(context),
                measurer(context),
                photo = { w, h -> request.source?.let { FrameAssets.decodeSource(it, w, h) }?.asImageBitmap() },
                release = { it.asAndroidBitmap().recycle() },
            ).asAndroidBitmap()
        }

        /** Lays text out in px, as the frame's sizes are. */
        internal fun measurer(context: Context) = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
    }
}

/** What the shared frame draws from: this request without its files. */
fun EncodeRequest.frameRequest() = FrameRequest(pattern, log, title, watermark, frame)

/** The shared `render.FrameArt` (T62) as Android bitmaps and rects: the same layers the old [FrameArt] gives. */
private class SharedFrameLayers(context: Context, request: EncodeRequest, width: Int, height: Int) : FrameLayers {
    private val art = SharedFrameArt(request.frameRequest(), AppArtAssets(context), FrameLayers.measurer(context), width, height) { w, h ->
        request.source?.let { FrameAssets.decodeSource(it, w, h) }?.asImageBitmap()
    }

    override val background: Bitmap = art.background.asAndroidBitmap()
    override val canvas: Bitmap = art.canvas.asAndroidBitmap()
    override val source: Bitmap? = art.source?.asAndroidBitmap()
    override val overlay: Bitmap? = art.overlay?.asAndroidBitmap()
    override val overlayRect: Rect? = art.overlayRect?.toRect()
    override val card: Bitmap? = art.card?.asAndroidBitmap()
    override val boardRect: Rect = art.boardRect.toRect()

    override fun apply(frame: Frame): Pair<Boolean, Float> = art.apply(frame)

    override fun endCard(alpha: Float): EndCardRects? =
        art.endCard(alpha)?.let { EndCardRects(it.alpha, it.picture.toRect(), it.content.toRect(), it.dim) }

    override fun release() {
        art.images.forEach { it.asAndroidBitmap().recycle() }
    }
}

private fun BoardRect.toRect() = Rect(left, top, right, bottom)
