package com.brewingbytes.gemmory.timelapse

/** What one frame of the time-lapse shows (design D26). */
sealed interface Frame {
    /**
     * The 1 s intro: the empty tint grid, with the cropped source photo over it at [photoAlpha] (photo canvases
     * only; always 0 for bundled pictures).
     */
    data class Intro(val photoAlpha: Float) : Frame

    /** The 12 s build: the first [placed] gems of the placement log are on the canvas. */
    data class Build(val placed: Int) : Frame

    /** The silent 2 s still hold of the finished picture. */
    data object Hold : Frame

    /**
     * The 1.8 s Gemmory end card after the hold (T53): it fades in over its first 0.3 s ([alpha] 1/9 → 1) over the
     * held canvas, then holds. Non-VIP player shares only.
     */
    data class EndCard(val alpha: Float) : Frame
}

/**
 * The 15 s, 30 fps time-lapse (design D26, eng E1): 30 intro frames, 360 build frames replaying the placement log in
 * order, then 60 hold frames. Gems are spread evenly over the build, so the last build frame shows all of them
 * whatever the canvas size (768 for the starter, up to ~15k for Big).
 *
 * Photo canvases show their cropped source photo for the first third of the intro, then crossfade it to the empty
 * tint grid. Bundled pictures show the tint grid only.
 *
 * A progress share (#36) is the same plan over the log so far: [logSize] is the gems placed, so the build replays
 * only those and the hold shows the canvas as it stands.
 *
 * With [endCard] (T53: a non-VIP player share) 54 end-card frames follow the hold, 504 in all (16.8 s).
 */
class FramePlan(val logSize: Int, val photoIntro: Boolean, val endCard: Boolean = false) {
    init {
        require(logSize >= 0) { "logSize must not be negative" }
    }

    val frameCount: Int get() = if (endCard) TOTAL_FRAMES + END_CARD_FRAMES else TOTAL_FRAMES

    /** The video's length in microseconds. */
    val durationUs: Long get() = frameCount * 1_000_000L / FPS

    fun frame(index: Int): Frame {
        require(index in 0 until frameCount) { "frame $index out of 0 until $frameCount" }
        return when {
            index < INTRO_FRAMES -> Frame.Intro(if (photoIntro) photoAlpha(index) else 0f)
            index < INTRO_FRAMES + BUILD_FRAMES -> Frame.Build(placedThrough(index - INTRO_FRAMES))
            index < TOTAL_FRAMES -> Frame.Hold
            else -> Frame.EndCard(minOf(1f, (index - TOTAL_FRAMES + 1).toFloat() / END_CARD_FADE_FRAMES))
        }
    }

    /** Gems on the canvas at the end of build frame [buildFrame] (0-based): an even share, all of them at the last. */
    fun placedThrough(buildFrame: Int): Int = ((logSize.toLong() * (buildFrame + 1)) / BUILD_FRAMES).toInt()

    /** Gems on the canvas in frame [index]: none in the intro, the build's share, then the whole log from the hold. */
    fun placedAt(index: Int): Int = when (val frame = frame(index)) {
        is Frame.Intro -> 0
        is Frame.Build -> frame.placed
        Frame.Hold, is Frame.EndCard -> logSize
    }

    /** Presentation time of frame [index] in microseconds. */
    fun presentationTimeUs(index: Int): Long = index * 1_000_000L / FPS

    private fun photoAlpha(index: Int): Float {
        if (index < PHOTO_HOLD_FRAMES) return 1f
        val fade = INTRO_FRAMES - PHOTO_HOLD_FRAMES
        return 1f - (index - PHOTO_HOLD_FRAMES + 1).toFloat() / fade
    }

    companion object {
        const val FPS = 30
        const val INTRO_FRAMES = 30
        const val BUILD_FRAMES = 360
        const val HOLD_FRAMES = 60

        /** Intro, build and hold: the whole video without the end card (VIP, and the founder clips). */
        const val TOTAL_FRAMES = INTRO_FRAMES + BUILD_FRAMES + HOLD_FRAMES

        /** The end card (T53): 1.8 s, the first 0.3 s a fade-in. */
        const val END_CARD_FRAMES = 54
        const val END_CARD_FADE_FRAMES = 9

        /** The photo shows alone for a third of a second before it starts to crossfade. */
        const val PHOTO_HOLD_FRAMES = 10

        const val DURATION_US = TOTAL_FRAMES * 1_000_000L / FPS

        /** The longest video, with the end card: what the free-space check plans for. */
        const val MAX_DURATION_US = (TOTAL_FRAMES + END_CARD_FRAMES) * 1_000_000L / FPS
    }
}
