package com.brewingbytes.gemmory.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MilestonesTest {
    @Test
    fun thresholdsRoundUp() {
        assertEquals(3, Milestones.threshold(25, 10))
        assertEquals(5, Milestones.threshold(50, 10))
        assertEquals(8, Milestones.threshold(75, 10))
        assertEquals(3_780, Milestones.threshold(25, 15_120))
        assertEquals(7_560, Milestones.threshold(50, 15_120))
        assertEquals(11_340, Milestones.threshold(75, 15_120))
    }

    @Test
    fun eachMilestonePlaysOnceOnTheWayUp() {
        val total = 15_120
        val crossed = (0 until total).mapNotNull { Milestones.crossed(it, it + 1, total) }
        assertEquals(listOf(25, 50, 75), crossed)
        assertEquals(25, Milestones.crossed(3_779, 3_780, total))
        assertNull(Milestones.crossed(3_778, 3_779, total), "one gem short")
        assertNull(Milestones.crossed(3_780, 3_781, total), "one gem past")
    }

    @Test
    fun aCanvasReopenedPastAMilestoneDoesNotReplayIt() {
        val total = 768
        // Reopened at 52%: the next placements cross nothing until 75%.
        val reopenedAt = 400
        val crossed = (reopenedAt until total).mapNotNull { Milestones.crossed(it, it + 1, total) }
        assertEquals(listOf(75), crossed)
        // Reopened exactly on a threshold: that milestone already played.
        assertNull(Milestones.crossed(Milestones.threshold(50, total), Milestones.threshold(50, total) + 1, total))
    }

    @Test
    fun finishingThePictureIsNotAMilestone() {
        assertNull(Milestones.crossed(9, 10, 10))
        // 3 gems: 25% is the 1st, 50% the 2nd, and 75% is the last, which finishes the picture.
        assertEquals(listOf(25, 50, null), (0 until 3).map { Milestones.crossed(it, it + 1, 3) })
        // 2 gems: 25% and 50% are both the 1st; the higher one plays.
        assertEquals(50, Milestones.crossed(0, 1, 2))
        assertNull(Milestones.crossed(0, 1, 1))
    }

    @Test
    fun noPlacementNoMilestone() {
        assertNull(Milestones.crossed(3_780, 3_780, 15_120))
        assertNull(Milestones.crossed(3_781, 3_780, 15_120))
    }
}
