package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import java.io.File

/**
 * What one time-lapse is made from: the finished canvas's grid, its placement log in order, the band title, and for
 * a photo canvas its cropped source photo for the 1 s intro (design D26).
 */
class EncodeRequest(
    val ref: CanvasRef,
    val pattern: Pattern,
    val log: IntArray,
    val title: String,
    val source: File?,
    val watermark: Boolean = WATERMARK_UNTIL_VIP,
) {
    val medium: ShareLink.Medium
        get() = if (ref.kind == CanvasKind.PHOTO) ShareLink.Medium.PHOTO else ShareLink.Medium.BUNDLED

    /** The MP4 cache key (eng D2): canvas id + log length + watermark flag. */
    val key: String get() = "${ref.id}.${log.size}.${if (watermark) "wm" else "clean"}"

    companion object {
        /** There is no VIP until #8, so every video carries "Made with Gemmory". */
        const val WATERMARK_UNTIL_VIP = true
    }
}
