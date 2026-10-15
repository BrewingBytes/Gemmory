package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import java.io.File

/**
 * What one time-lapse is made from: the finished canvas's grid, its placement log in order, the band title, and for
 * a photo canvas its cropped source photo for the 1 s intro (design D26). [watermark]: "Made with Gemmory" for
 * everyone but VIP (tier table).
 */
class EncodeRequest(
    val ref: CanvasRef,
    val pattern: Pattern,
    val log: IntArray,
    val title: String,
    val source: File?,
    val watermark: Boolean,
) {
    val medium: ShareLink.Medium
        get() = if (ref.kind == CanvasKind.PHOTO) ShareLink.Medium.PHOTO else ShareLink.Medium.BUNDLED

    /** The MP4 cache key (eng D2): canvas id + log length + watermark flag. */
    val key: String get() = "${ref.id}.${log.size}.${if (watermark) "wm" else "clean"}"

    /** The same video with or without the watermark: decided at Share time, so a player who just became VIP gets none. */
    fun withWatermark(watermark: Boolean) = EncodeRequest(ref, pattern, log, title, source, watermark)
}
