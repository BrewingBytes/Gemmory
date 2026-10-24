package com.brewingbytes.gemmory.timelapse

import kotlin.coroutines.cancellation.CancellationException

/** One video size the chain tries, portrait 9:16 at 30 fps. */
data class EncodeTarget(val width: Int, val height: Int, val fps: Int = FramePlan.FPS) {
    override fun toString(): String = "${width}x$height@$fps"

    /** AVC bitrate for this size. */
    val bitrate: Int get() = if (width >= 1080) 8_000_000 else 4_000_000

    /**
     * Free space an attempt needs before it starts: the longest player share (16.8 s with the end card) at [bitrate],
     * plus half again for the muxer. A founder clip (#144) runs 18 s; the half covers its extra 1.2 s (and still
     * leaves 40% for the muxer).
     */
    val neededBytes: Long get() = bitrate.toLong() * FramePlan.MAX_DURATION_US / 8_000_000 * 3 / 2

    companion object {
        val FULL_HD = EncodeTarget(1080, 1920)
        val HD = EncodeTarget(720, 1280)

        /** Eng E1: 1080×1920 first, then 720×1280. A landscape-rotated step was considered and not taken. */
        val CHAIN = listOf(FULL_HD, HD)
    }
}

/** Where in an attempt something went wrong, for the non-fatal report. */
enum class EncodeStage { PROBE, CONFIGURE, ENCODE, MUX, PICTURE }

/** Thrown by an [EncodeBackend] step; [stage] says which. Carries ids and error classes only, never photo data. */
class EncodeFailure(val stage: EncodeStage, cause: Throwable) : Exception("$stage failed: ${cause::class.simpleName}", cause)

/** One failed attempt, as reported as a non-fatal (eng E1: codec name, target, stage, error class). */
data class AttemptFailure(val target: EncodeTarget?, val codec: String?, val stage: EncodeStage, val error: Throwable)

/**
 * The platform side of the chain: probing, encoding one target to a partial file, the before/after picture, and
 * cleaning up. Every method may block; [EncodeBackend.encode] polls the chain's cancel check between frames.
 */
interface EncodeBackend {
    /** `areSizeAndRateSupported` + width/height alignment. Null if no encoder takes [target]; else its codec name. */
    fun probe(target: EncodeTarget): String?

    /**
     * Encodes every frame of the plan for [target] into its partial file. Calls [onFrame] after each frame and
     * [checkCancelled] between frames. Throws [EncodeFailure] (or anything else) on error.
     */
    fun encode(target: EncodeTarget, codec: String, onFrame: (done: Int, total: Int) -> Unit, checkCancelled: () -> Unit)

    /** Renders the 1080×1920 before/after PNG to its partial file. */
    fun renderPicture()

    /** Deletes the partial file of [target] (null: the picture). Never throws. */
    fun discard(target: EncodeTarget?)

    /** True when [error] (or the phone's free space right now) means storage is full. */
    fun isDiskFull(error: Throwable): Boolean

    /** Free bytes where the partial files go. */
    fun freeBytes(): Long
}

sealed interface ChainOutcome {
    /** The video for [target] is complete in its partial file, ready to be published. */
    data class Video(val target: EncodeTarget) : ChainOutcome

    /** No video target worked; the before/after picture is in its partial file. */
    data object Picture : ChainOutcome

    /** The video targets and the picture all failed: "Couldn't make the video. Try again." */
    data object Failed : ChainOutcome

    /** Storage ran out: "Not enough space on this phone to make the video." Nothing more is tried. */
    data object DiskFull : ChainOutcome
}

/**
 * Eng E1. Tries [targets] in order: probe, then encode. Any error while configuring, encoding or muxing deletes the
 * partial file, is reported as a non-fatal and moves on to the next target; the drop is silent to the player. After
 * the last video target it renders the before/after picture. Running out of space stops the chain at once, since a
 * smaller target or a picture would fill the same disk.
 *
 * Before each video target the free space is checked against [EncodeTarget.neededBytes]: a target that doesn't fit
 * is skipped silently while a smaller one remains, and when the smallest doesn't fit either it's [ChainOutcome.DiskFull]
 * (the before/after picture would fit, but its "isn't supported" message would be wrong).
 *
 * Cancellation (a [CancellationException] from `checkCancelled`) deletes the partial file and propagates: no
 * fallback, no report.
 *
 * Without [picture] (the feed cut, T51, which is a video or nothing) the chain ends [ChainOutcome.Failed] after the
 * last video target instead of rendering the picture.
 */
class FallbackChain(
    private val backend: EncodeBackend,
    private val report: (AttemptFailure) -> Unit,
    private val targets: List<EncodeTarget> = EncodeTarget.CHAIN,
    private val picture: Boolean = true,
) {
    /** [onProgress] gets the current attempt's frame count; a fallback starts again from 0. */
    fun run(onProgress: (done: Int, total: Int) -> Unit, checkCancelled: () -> Unit): ChainOutcome {
        val smallest = targets.minByOrNull { it.width * it.height }
        for (target in targets) {
            checkCancelled()
            if (backend.freeBytes() < target.neededBytes) {
                if (target == smallest) return ChainOutcome.DiskFull
                continue
            }
            val codec = try {
                backend.probe(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(AttemptFailure(target, null, EncodeStage.PROBE, e))
                null
            } ?: continue
            try {
                backend.encode(target, codec, onProgress, checkCancelled)
                return ChainOutcome.Video(target)
            } catch (e: CancellationException) {
                backend.discard(target)
                throw e
            } catch (e: Exception) {
                backend.discard(target)
                val stage = (e as? EncodeFailure)?.stage ?: EncodeStage.ENCODE
                report(AttemptFailure(target, codec, stage, e))
                if (backend.isDiskFull(e)) return ChainOutcome.DiskFull
            }
        }
        checkCancelled()
        if (!picture) return ChainOutcome.Failed
        return try {
            backend.renderPicture()
            ChainOutcome.Picture
        } catch (e: CancellationException) {
            backend.discard(null)
            throw e
        } catch (e: Exception) {
            backend.discard(null)
            report(AttemptFailure(null, null, EncodeStage.PICTURE, e))
            if (backend.isDiskFull(e)) ChainOutcome.DiskFull else ChainOutcome.Failed
        }
    }
}
