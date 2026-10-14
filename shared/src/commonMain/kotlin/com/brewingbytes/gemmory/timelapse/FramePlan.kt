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
}

/**
 * The 15 s, 30 fps time-lapse (design D26, eng E1): 30 intro frames, 360 build frames replaying the placement log in
 * order, then 60 hold frames. Gems are spread evenly over the build, so the last build frame shows all of them
 * whatever the canvas size (768 for the starter, up to ~15k for Big).
 *
 * Photo canvases show their cropped source photo for the first third of the intro, then crossfade it to the empty
 * tint grid. Bundled pictures show the tint grid only.
 */
class FramePlan(val logSize: Int, val photoIntro: Boolean) {
    init {
        require(logSize >= 0) { "logSize must not be negative" }
    }

    val frameCount: Int get() = TOTAL_FRAMES

    fun frame(index: Int): Frame {
        require(index in 0 until TOTAL_FRAMES) { "frame $index out of 0 until $TOTAL_FRAMES" }
        return when {
            index < INTRO_FRAMES -> Frame.Intro(if (photoIntro) photoAlpha(index) else 0f)
            index < INTRO_FRAMES + BUILD_FRAMES -> Frame.Build(placedThrough(index - INTRO_FRAMES))
            else -> Frame.Hold
        }
    }

    /** Gems on the canvas at the end of build frame [buildFrame] (0-based): an even share, all of them at the last. */
    fun placedThrough(buildFrame: Int): Int = ((logSize.toLong() * (buildFrame + 1)) / BUILD_FRAMES).toInt()

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
        const val TOTAL_FRAMES = INTRO_FRAMES + BUILD_FRAMES + HOLD_FRAMES

        /** The photo shows alone for a third of a second before it starts to crossfade. */
        const val PHOTO_HOLD_FRAMES = 10

        const val DURATION_US = TOTAL_FRAMES * 1_000_000L / FPS
    }
}
