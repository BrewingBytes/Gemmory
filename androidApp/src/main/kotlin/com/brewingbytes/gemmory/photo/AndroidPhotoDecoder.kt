package com.brewingbytes.gemmory.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The photo flow's [PhotoDecoder] on Android (iOS Stage 1c, T64): the same `ImageDecoder` and `Bitmap` calls the
 * ViewModel made before it moved to shared code, so the same photo and crop still give the same canvas.
 */
class AndroidPhotoDecoder(private val resolver: ContentResolver) : PhotoDecoder {
    /** Software-allocated, so the converter can read its pixels; `ImageDecoder` applies the EXIF orientation. */
    override fun decode(uri: String, maxEdgePx: Int): ImageBitmap {
        val source = ImageDecoder.createSource(resolver, uri.toUri())
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > maxEdgePx) {
                val scale = maxEdgePx.toDouble() / longEdge
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).roundToInt()),
                    max(1, (info.size.height * scale).roundToInt()),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    }

    override fun crop(image: ImageBitmap, rect: IntRect): ImageBitmap =
        Bitmap.createBitmap(image.asAndroidBitmap(), rect.left, rect.top, rect.width, rect.height).asImageBitmap()

    override fun jpeg(image: ImageBitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
