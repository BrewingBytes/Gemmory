package com.brewingbytes.gemmory.timelapse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A founder clip's timing (#144, H1): hook, the player's 15 s, crossfade, end card. */
class ClipFramePlanTest {
    @Test
    fun eighteenSecondsIn540Frames() {
        val plan = ClipFramePlan(logSize = 768)
        assertEquals(540, plan.frameCount)
        assertEquals(18_000_000L, plan.durationUs)
        assertEquals(17_966_666L, plan.presentationTimeUs(539))
        assertFailsWith<IllegalArgumentException> { plan.frame(540) }
    }

    @Test
    fun theHookShowsTheFinishedCanvasThenCutsToTheTintGrid() {
        val plan = ClipFramePlan(logSize = 768)
        for (i in 0 until 15) {
            assertEquals(Frame.Hook, plan.frame(i), "frame $i")
            assertEquals(768, plan.placedAt(i), "frame $i")
        }
        // A hard cut: the first intro frame shows no photo and no gems.
        assertEquals(Frame.Intro(0f), plan.frame(15))
        assertEquals(0, plan.placedAt(15))
    }

    @Test
    fun theMiddleIsThePlayersPlanShiftedByTheHook() {
        for (log in listOf(0, 1, 768, 15_052)) {
            val clip = ClipFramePlan(log)
            val player = FramePlan(log, photoIntro = false)
            for (i in 0 until FramePlan.TOTAL_FRAMES) {
                assertEquals(player.frame(i), clip.frame(i + 15), "log $log frame $i")
                assertEquals(player.placedAt(i), clip.placedAt(i + 15), "log $log frame $i")
            }
        }
        val plan = ClipFramePlan(768)
        for (i in 405..464) assertEquals(Frame.Hold, plan.frame(i), "frame $i")
        assertEquals(Frame.Build(768), plan.frame(404))
    }

    @Test
    fun aCrossfadeThenTheHeldEndCard() {
        val plan = ClipFramePlan(logSize = 768)
        for (i in 465..473) {
            val alpha = (plan.frame(i) as Frame.EndCard).alpha
            assertEquals((i - 464) / 9f, alpha, 1e-6f, "frame $i")
        }
        for (i in 474..539) assertEquals(Frame.EndCard(1f), plan.frame(i), "frame $i")
        assertEquals(768, plan.placedAt(539))
        assertEquals(75, plan.frameCount - ClipFramePlan.CARD_START)
    }
}
