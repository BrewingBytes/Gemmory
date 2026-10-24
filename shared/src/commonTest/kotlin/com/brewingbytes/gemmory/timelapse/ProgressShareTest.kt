package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.PlaceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The progress share (#36): replays the save log so far, ends on the canvas as it stands, says how far along it is. */
class ProgressShareTest {
    /** 30×40 in four colors, like a small Quick canvas. */
    private val pattern = Pattern(
        width = 30,
        height = 40,
        palette = List(4) { PaletteColor(it + 1, 0xFF000000.toInt() or it, Lab(it * 20.0, 0.0, 0.0)) },
        cells = IntArray(1_200) { (it * 7 + it / 30) % 4 },
    )

    /** A half-done canvas played the way a player would: color by color, bottom to top, so the log isn't row-major. */
    private fun halfDone(): CanvasState {
        val state = CanvasState.empty(pattern)
        val order = (3 downTo 0).flatMap { slot -> (pattern.cells.indices.reversed()).filter { pattern.cells[it] == slot } }
        for (cell in order.take(564)) assertIs<PlaceResult.Placed>(state.place(cell, pattern.cells[cell]))
        return state
    }

    @Test
    fun replayOrderMatchesTheSaveLog() {
        val log = halfDone().log()
        val plan = FramePlan(log.size, photoIntro = true)
        val replayed = mutableListOf<Int>()
        var drawn = 0
        for (index in 0 until plan.frameCount) {
            val placed = plan.placedAt(index)
            assertTrue(placed >= drawn, "frame $index never takes a gem back")
            while (drawn < placed) replayed += log[drawn++]
        }
        assertEquals(log.toList(), replayed, "every gem in the order it was placed, each once")
    }

    @Test
    fun theLastFrameIsTheCurrentState() {
        val state = halfDone()
        val log = state.log()
        val plan = FramePlan(log.size, photoIntro = false)
        val last = log.take(plan.placedAt(plan.frameCount - 1)).toSet()
        assertEquals((0 until state.cellCount).filter(state::isPlaced).toSet(), last, "exactly the placed gems, no more")
        assertEquals(log.size, plan.placedAt(FramePlan.INTRO_FRAMES + FramePlan.BUILD_FRAMES - 1), "the build ends on it too")
        assertEquals(0, plan.placedAt(FramePlan.INTRO_FRAMES - 1), "the intro is the empty canvas")
    }

    @Test
    fun thePercentageLineRoundsDownAndNeverSaysDoneEarly() {
        assertEquals(ProgressLine.Done(47), ProgressShare.line(564, 1_200))
        assertEquals(ProgressLine.Done(1), ProgressShare.line(12, 1_200))
        assertEquals(ProgressLine.Done(99), ProgressShare.line(14_883, 14_884))
    }

    @Test
    fun underOnePercentItJustStarted() {
        assertEquals(ProgressLine.JustStarted, ProgressShare.line(1, 1_200), "one gem, not \"0% done\"")
        assertEquals(ProgressLine.JustStarted, ProgressShare.line(11, 1_200))
        assertEquals(ProgressLine.JustStarted, ProgressShare.line(148, 14_884), "a Big canvas under 1%")
    }

    @Test
    fun offeredFromTheFirstGemUntilTheLastOne() {
        assertFalse(ProgressShare.isOffered(0, 1_200), "a blank canvas")
        assertTrue(ProgressShare.isOffered(1, 1_200), "one gem")
        assertTrue(ProgressShare.isOffered(1_199, 1_200), "one gem left")
        assertFalse(ProgressShare.isOffered(1_200, 1_200), "finished: the completion screen's Share instead")
    }
}
