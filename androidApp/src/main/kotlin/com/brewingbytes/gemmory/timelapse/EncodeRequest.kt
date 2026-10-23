package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import java.io.File

/**
 * What one time-lapse is made from: the canvas's grid, its placement log in order, the band title, and for a photo
 * canvas its cropped source photo for the 1 s intro (design D26). [watermark]: "Made with Gemmory" for everyone but
 * VIP (tier table). A log shorter than the grid is a progress share (#36): it ends on the canvas as it stands.
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

    /** The picture's id for the share link (T50): a bundled or remote picture's; never a photo canvas's. */
    val pictureId: String?
        get() = ref.id.takeIf { ref.kind != CanvasKind.PHOTO }

    /** Shared before the last gem (#36). */
    val inProgress: Boolean get() = log.size < pattern.cells.size

    /** The band's "47% done" line for a progress share; null for a finished picture. */
    val progressLine: String? get() = if (inProgress) ProgressShare.line(log.size, pattern.cells.size) else null

    /**
     * The MP4 cache key (eng D2): canvas id + log length + watermark flag. A progress video's key says so (#36), so
     * it's never taken for the finished one, and every new gem makes a new one.
     */
    val key: String
        get() = "${ref.id}.${if (inProgress) "$PROGRESS." else ""}${log.size}.${if (watermark) "wm" else "clean"}"

    /** The same video with or without the watermark: decided at Share time, so a player who just became VIP gets none. */
    fun withWatermark(watermark: Boolean) = EncodeRequest(ref, pattern, log, title, source, watermark)

    companion object {
        /** Marks a progress share's files in `cacheDir/timelapse/`. */
        const val PROGRESS = "progress"
    }
}
