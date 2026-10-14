package com.brewingbytes.gemmory.timelapse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramePlanTest {
    /** Starter (24×32), Quick, Medium and Big at their usual gem counts (design doc, size table). */
    private val sizes = listOf(768, 56 * 56, 78 * 104, 106 * 142)

    @Test
    fun fifteenSecondsAtThirtyFps() {
        val plan = FramePlan(768, photoIntro = false)
        assertEquals(450, plan.frameCount)
        assertEquals(30, (0 until 450).count { plan.frame(it) is Frame.Intro })
        assertEquals(360, (0 until 450).count { plan.frame(it) is Frame.Build })
        assertEquals(60, (0 until 450).count { plan.frame(it) is Frame.Hold })
        assertEquals(15_000_000L, FramePlan.DURATION_US)
        assertEquals(0L, plan.presentationTimeUs(0))
        assertEquals(1_000_000L, plan.presentationTimeUs(30))
        assertEquals(14_966_666L, plan.presentationTimeUs(449))
    }

    @Test
    fun introThenBuildThenHoldInOrder() {
        val plan = FramePlan(3136, photoIntro = true)
        val kinds = (0 until 450).map { plan.frame(it)::class }
        assertEquals(List(30) { Frame.Intro::class } + List(360) { Frame.Build::class } + List(60) { Frame.Hold::class }, kinds)
    }

    @Test
    fun buildReplaysEveryGemEvenlyAndEndsComplete() {
        for (gems in sizes) {
            val plan = FramePlan(gems, photoIntro = false)
            val placed = (30 until 390).map { (plan.frame(it) as Frame.Build).placed }
            assertEquals(gems, placed.last(), "all $gems gems by the last build frame")
            assertTrue(placed.zipWithNext().all { (a, b) -> b >= a }, "never goes backwards")
            val perFrame = listOf(placed.first()) + placed.zipWithNext { a, b -> b - a }
            val even = gems / 360
            assertTrue(perFrame.all { it == even || it == even + 1 }, "$gems gems: $even or ${even + 1} per frame, got ${perFrame.toSet()}")
            assertEquals(gems, perFrame.sum())
        }
    }

    @Test
    fun bigCanvasPlacesAboutFortyTwoGemsPerFrame() {
        val plan = FramePlan(15_052, photoIntro = true)
        assertEquals(41, plan.placedThrough(0))
        assertEquals(15_052, plan.placedThrough(359))
    }

    @Test
    fun photoIntroHoldsThePhotoThenCrossfadesToTheTintGrid() {
        val plan = FramePlan(8112, photoIntro = true)
        val alphas = (0 until 30).map { (plan.frame(it) as Frame.Intro).photoAlpha }
        assertTrue(alphas.take(FramePlan.PHOTO_HOLD_FRAMES).all { it == 1f })
        assertTrue(alphas.zipWithNext().all { (a, b) -> b <= a }, "the photo only fades out")
        assertTrue(alphas[FramePlan.PHOTO_HOLD_FRAMES] < 1f)
        assertEquals(0f, alphas.last(), "the build starts from the bare tint grid")
    }

    @Test
    fun bundledIntroIsTheTintGridOnly() {
        val plan = FramePlan(768, photoIntro = false)
        assertTrue((0 until 30).all { (plan.frame(it) as Frame.Intro).photoAlpha == 0f })
    }

    @Test
    fun emptyLogStillPlansAllFrames() {
        val plan = FramePlan(0, photoIntro = false)
        assertTrue((30 until 390).all { (plan.frame(it) as Frame.Build).placed == 0 })
    }
}
