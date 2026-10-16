package com.brewingbytes.gemmory.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinishCelebrationTest {
    @Test
    fun aTileIsNeverSmallerThanTheMinimum() {
        assertEquals(1, FinishCelebration.tileCells(cellPx = 30f, minTilePx = 11f), "a Quick gem glints on its own")
        assertEquals(2, FinishCelebration.tileCells(cellPx = 9.4f, minTilePx = 11f), "a Big gem at fit zoom glints in 2×2")
        assertEquals(4, FinishCelebration.tileCells(cellPx = 3f, minTilePx = 11f))
        assertEquals(1, FinishCelebration.tileCells(cellPx = 0f, minTilePx = 11f))
    }

    @Test
    fun everyTileGlintsOnceAndAllAreDoneAtTheEndOfTheSweep() {
        for ((cols, rows) in listOf(1 to 1, 20 to 30, 105 to 144)) {
            for (row in 0 until rows) for (col in 0 until cols) {
                val samples = (0..400).map { FinishCelebration.glint(it / 400f, col, row, cols, rows) }
                assertEquals(0f, samples.first(), "($col, $row) of $cols×$rows is dark before the sweep")
                assertEquals(0f, samples.last(), "($col, $row) of $cols×$rows is dark at the end")
                assertTrue(samples.max() > 0.95f, "($col, $row) of $cols×$rows reaches full glint")
                assertTrue(samples.all { it in 0f..1f })
                // One rise and one fall: once it has gone dark after glinting, it stays dark.
                val lit = samples.indices.filter { samples[it] > 0f }
                assertEquals(lit.last() - lit.first() + 1, lit.size, "($col, $row) glints in one run")
            }
        }
    }

    @Test
    fun theWaveRunsFromTopLeftToBottomRight() {
        val firstLit = { col: Int, row: Int ->
            (0..1_000).first { FinishCelebration.glint(it / 1_000f, col, row, 40, 40) > 0f }
        }
        assertTrue(firstLit(0, 0) < firstLit(20, 20))
        assertTrue(firstLit(20, 20) < firstLit(39, 39))
    }

    @Test
    fun confettiOnlyUsesThePicturesOwnColorsInProportion() {
        val counts = intArrayOf(900, 100, 0, 0)
        val gems = FinishCelebration.confetti(counts, 16f, 40f, 328f, 437f, roomWidth = 360f, seed = 1)
        assertEquals(180, gems.size)
        assertTrue(gems.none { it.slot >= 2 }, "a color the picture doesn't have never shows")
        val share = gems.count { it.slot == 0 } / gems.size.toFloat()
        assertTrue(share in 0.8f..0.97f, "slot 0 is 90% of the picture, was $share of the confetti")
    }

    @Test
    fun confettiScalesWithTheRoomUpToTwice() {
        val counts = intArrayOf(1)
        assertEquals(180, FinishCelebration.confetti(counts, 0f, 0f, 10f, 10f, roomWidth = 320f, seed = 1).size)
        assertEquals(270, FinishCelebration.confetti(counts, 0f, 0f, 10f, 10f, roomWidth = 540f, seed = 1).size)
        assertEquals(360, FinishCelebration.confetti(counts, 0f, 0f, 10f, 10f, roomWidth = 1_280f, seed = 1).size)
        assertTrue(FinishCelebration.confetti(IntArray(3), 0f, 0f, 10f, 10f, roomWidth = 360f, seed = 1).isEmpty())
    }

    @Test
    fun confettiIsTheSameForASeed() {
        val a = FinishCelebration.confetti(intArrayOf(5, 5), 0f, 0f, 100f, 100f, 360f, seed = 7)
        val b = FinishCelebration.confetti(intArrayOf(5, 5), 0f, 0f, 100f, 100f, 360f, seed = 7)
        assertEquals(a.map { listOf(it.x, it.y, it.vx, it.vy, it.slot) }, b.map { listOf(it.x, it.y, it.vx, it.vy, it.slot) })
    }

    @Test
    fun popsLeaveFromUnderThePictureAndRainStartsAboveTheScreen() {
        val gems = FinishCelebration.confetti(intArrayOf(1), 16f, 40f, 328f, 437f, roomWidth = 360f, seed = 3)
        val pops = gems.take(108)
        val rain = gems.drop(108)
        assertTrue(pops.all { it.y == 40f + 437f - 6f && it.x in 16f..344f && it.vy <= 0f })
        assertTrue(rain.all { it.y < 0f && it.vy > 0f })
    }

    @Test
    fun everyGemHasFadedOutWithinTheHold() {
        val gems = FinishCelebration.confetti(intArrayOf(3, 4), 16f, 40f, 328f, 437f, 360f, seed = 9)
        for (gem in gems) {
            assertNull(FinishCelebration.at(gem, gem.delayMs - 1f), "not launched yet")
            assertNotNull(FinishCelebration.at(gem, gem.delayMs + 10f))
            assertNull(FinishCelebration.at(gem, FinishCelebration.CONFETTI_MS.toFloat()), "gone before the text fades in")
        }
    }

    @Test
    fun aGemFallsUnderGravity() {
        val gem = FinishCelebration.Gem(100f, 100f, vx = 50f, vy = -100f, size = 6f, angle = 0f, spin = 0f, slot = 0, delayMs = 0)
        val start = FinishCelebration.at(gem, 0f)!!
        val later = FinishCelebration.at(gem, 1_000f)!!
        assertEquals(100f, start.x)
        assertEquals(150f, later.x, 0.01f)
        assertEquals(100f - 100f + 260f, later.y, 0.01f)
        assertEquals(1f, later.alpha)
        assertEquals(0.5f, FinishCelebration.at(gem, 1_800f)!!.alpha, 0.01f)
    }

    @Test
    fun theTicksFillTheSweepSpeedingUpAndSwelling() {
        val ticks = FinishCelebration.crescendoTicks()
        assertEquals(FinishCelebration.MAX_TICKS, ticks.size)
        assertEquals(FinishCelebration.SWEEP_MS, ticks.sumOf { it.delayMs }, "the last tick lands on the chime")
        assertTrue(ticks.zipWithNext().all { (a, b) -> b.delayMs <= a.delayMs + 1 }, "gaps shrink")
        assertTrue(ticks.zipWithNext().all { (a, b) -> b.scale > a.scale }, "ticks swell")
        assertTrue(ticks.all { it.scale in 0f..1f })
    }

    @Test
    fun theWaveformRisesPeaksOnTheChimeAndFalls() {
        val (timings, amplitudes) = FinishCelebration.crescendoWaveform()
        assertEquals(timings.size, amplitudes.size)
        assertTrue(amplitudes.all { it in 1..255 })
        val peak = amplitudes.indexOf(255)
        assertEquals(FinishCelebration.SWEEP_MS.toLong(), timings.take(peak).sum(), "the peak starts on the chime")
        assertTrue(amplitudes.take(peak).last() > amplitudes.take(peak).first() * 3, "it rises")
        assertTrue(amplitudes.drop(peak).zipWithNext().all { (a, b) -> b <= a }, "it falls")
        assertTrue(timings.sum() <= FinishCelebration.SWEEP_MS + 500L)
    }
}
