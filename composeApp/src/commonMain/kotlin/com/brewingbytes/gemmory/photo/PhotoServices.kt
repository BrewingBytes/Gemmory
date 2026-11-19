package com.brewingbytes.gemmory.photo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import com.brewingbytes.gemmory.converter.FaceCheck

/**
 * The photo flow's image work on each platform (iOS Stage 1c, T64): decode, crop and JPEG. Android: `ImageDecoder`
 * and `Bitmap`, as before the move; iOS (Stage 2): `CGImageSource` with the EXIF orientation applied. The photo never
 * leaves the phone.
 */
interface PhotoDecoder {
    /**
     * The picked photo at [uri] (Android: a content URI; iOS: a file URL of the picker's copy), upright and decoded
     * straight to at most [maxEdgePx] on the long edge (eng finding 14), never at full resolution. Null when it can't
     * be opened. Blocking: call it off the main thread.
     */
    fun decode(uri: String, maxEdgePx: Int): ImageBitmap?

    /** [rect] (inside [image]) as a picture of its own. */
    fun crop(image: ImageBitmap, rect: IntRect): ImageBitmap

    /** [image] as a JPEG at [quality] (0-100), the canvas's source photo. */
    fun jpeg(image: ImageBitmap, quality: Int): ByteArray
}

/**
 * The face check on the cropped photo (design D4, R3-4), on the phone only. Android: ML Kit's bundled detector; iOS
 * (Stage 2): Vision. A failure or a check longer than 1 s counts as no face, and a failure is reported as a non-fatal.
 */
interface FaceFinder {
    /** Loads the model while the player crops, so the first real check lands inside its budget. */
    fun warmUp()

    suspend fun check(photo: ImageBitmap): FaceCheck
}
