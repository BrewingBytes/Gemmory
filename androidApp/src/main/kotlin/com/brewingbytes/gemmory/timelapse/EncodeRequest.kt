package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import java.io.File

/** Which 9:16 frame a time-lapse is drawn in. */
enum class ShareFrame {
    /** The player's share (T53): the picture fitted above the Gemmory row, and the end card. */
    PLAYER,

    /** The founder clips (T47): their own feed-safe frame (#144), with the store lockup, the hook and the end card. */
    CLIP,

    /**
     * The feed cut (T51): 7 s in the founder clip's feed-safe frame, with no end card, made beside a finished non-VIP
     * player share for the apps in [FeedTargets].
     */
    FEED,
}

/**
 * What one time-lapse is made from: the canvas's grid, its placement log in order, the band title, and for a photo
 * canvas its cropped source photo for the 1 s intro (design D26). [watermark]: for everyone but VIP (tier table), the
 * Gemmory row on every frame and the end card (T53); a founder clip ([ShareFrame.CLIP], #144) has its own lockup. A
 * log shorter than the grid is a progress share (#36): it ends on the canvas as it stands.
 */
class EncodeRequest(
    val ref: CanvasRef,
    val pattern: Pattern,
    val log: IntArray,
    val title: String,
    val source: File?,
    val watermark: Boolean,
    val frame: ShareFrame = ShareFrame.PLAYER,
) {
    val medium: ShareLink.Medium
        get() = if (ref.kind == CanvasKind.PHOTO) ShareLink.Medium.PHOTO else ShareLink.Medium.BUNDLED

    /** The picture's id for the share link (T50): a bundled or remote picture's; never a photo canvas's. */
    val pictureId: String?
        get() = ref.id.takeIf { ref.kind != CanvasKind.PHOTO }

    /** Shared before the last gem (#36). */
    val inProgress: Boolean get() = log.size < pattern.cells.size

    /** A non-VIP player share ends on the Gemmory end card (T53): 504 frames instead of 450. */
    val endCard: Boolean get() = frame == ShareFrame.PLAYER && watermark

    /**
     * A finished non-VIP player share also makes the feed cut (T51) for TikTok, Instagram and YouTube. VIP keeps one
     * clean video, and a progress share has no finished picture to open on.
     */
    val feedCut: Boolean get() = frame == ShareFrame.PLAYER && watermark && !inProgress

    /** The feed cut of this share (T51). */
    fun feed() = EncodeRequest(ref, pattern, log, title, source, watermark, ShareFrame.FEED)

    /** The band's "47% done" line for a progress share; null for a finished picture. */
    val progressLine: ProgressLine? get() = if (inProgress) ProgressShare.line(log.size, pattern.cells.size) else null

    /**
     * The MP4 cache key (eng D2): canvas id + log length + watermark flag + frame version. A progress video's key says
     * so (#36), so it's never taken for the finished one, and every new gem makes a new one. The version keeps a video
     * cached before T53 from being shared in the old frame. The feed cut (T51) has its own, versioned apart.
     */
    val key: String
        get() = if (frame == ShareFrame.FEED) {
            "${ref.id}.${log.size}.$FEED.$FEED_VERSION"
        } else {
            "${ref.id}.${if (inProgress) "$PROGRESS." else ""}${log.size}.${if (watermark) "wm" else "clean"}.$FRAME_VERSION"
        }

    /** The same video with or without the watermark: decided at Share time, so a player who just became VIP gets none. */
    fun withWatermark(watermark: Boolean) = EncodeRequest(ref, pattern, log, title, source, watermark, frame)

    companion object {
        /** Marks a progress share's files in `cacheDir/timelapse/`. */
        const val PROGRESS = "progress"

        /** Bumped when the player's frame changes, so cached MP4s in an older frame aren't reused (T53: v2). */
        const val FRAME_VERSION = "v2"

        /** Marks the feed cut's files (T51). */
        const val FEED = "feed"

        /** Bumped when the feed cut's frame changes. */
        const val FEED_VERSION = "v1"
    }
}
