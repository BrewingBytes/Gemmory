package com.brewingbytes.gemmory.timelapse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The feed cut's timing (T51): 0.5 s hook, 5.5 s build, 1 s hold, no end card. */
class FeedFramePlanTest {
    @Test
    fun sevenSecondsIn210Frames() {
        val plan = FeedFramePlan(logSize = 768, photoHook = false)
        assertEquals(210, plan.frameCount)
        assertEquals(7_000_000L, plan.durationUs)
        assertTrue(plan.durationUs <= 7_000_000L)
        assertEquals(6_966_666L, plan.presentationTimeUs(209))
        assertFailsWith<IllegalArgumentException> { plan.frame(210) }
        assertFailsWith<IllegalArgumentException> { FeedFramePlan(logSize = -1, photoHook = false) }
    }

    @Test
    fun aBundledHookShowsTheFinishedCanvasThenCutsToTheTintGrid() {
        val plan = FeedFramePlan(logSize = 768, photoHook = false)
        for (i in 0 until 15) {
            assertEquals(Frame.Reveal(0f), plan.frame(i), "frame $i")
            assertEquals(768, plan.placedAt(i), "frame $i")
        }
        // A hard cut: the first build frame shows only its own share of the gems.
        assertEquals(Frame.Build(4), plan.frame(15))
        assertEquals(4, plan.placedAt(15))
    }

    @Test
    fun aPhotoHookHoldsThePhotoThenTurnsItIntoGems() {
        val plan = FeedFramePlan(logSize = 768, photoHook = true)
        for (i in 0 until 5) assertEquals(Frame.Reveal(1f), plan.frame(i), "frame $i")
        val alphas = (5 until 15).map { (plan.frame(it) as Frame.Reveal).photoAlpha }
        assertEquals(0.9f, alphas.first(), 1e-6f)
        assertEquals(0f, alphas.last())
        assertTrue(alphas.zipWithNext().all { (a, b) -> b < a }, "fades out steadily: $alphas")
        assertEquals(768, plan.placedAt(14))
    }

    @Test
    fun theBuildPlacesEveryGemEvenlyAndTheHoldShowsThemAll() {
        for (log in listOf(0, 1, 768, 15_052)) {
            val plan = FeedFramePlan(log, photoHook = false)
            var last = 0
            for (i in 15 until 180) {
                val placed = plan.placedAt(i)
                assertTrue(placed >= last, "log $log frame $i")
                last = placed
            }
            assertEquals(log, plan.placedAt(179), "log $log: the last build frame")
            for (i in 180 until 210) {
                assertEquals(Frame.Hold, plan.frame(i), "log $log frame $i")
                assertEquals(log, plan.placedAt(i))
            }
        }
    }

    @Test
    fun theLastFrameShowsWhatTheFirstShowsUnderThePhotoSoItLoops() {
        val plan = FeedFramePlan(logSize = 15_052, photoHook = true)
        assertEquals(plan.placedAt(0), plan.placedAt(plan.frameCount - 1))
        assertTrue(plan.frame(plan.frameCount - 1) !is Frame.EndCard)
    }
}
