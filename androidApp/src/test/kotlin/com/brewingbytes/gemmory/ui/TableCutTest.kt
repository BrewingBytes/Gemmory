package com.brewingbytes.gemmory.ui

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableCutTest {
    private val d = 2.625f

    @Test
    fun theBevelFollowsTheZoomTable() {
        assertEquals(0f, TableCut.bevelPx(7.9f * d, 7.9f, d), "flat below 8dp")
        assertEquals(8 * d * 0.24f, TableCut.bevelPx(8 * d, 8f, d), 1e-4f)
        assertEquals(15.9f * d * 0.24f, TableCut.bevelPx(15.9f * d, 15.9f, d), 1e-4f)
        assertEquals(16 * d * 0.20f, TableCut.bevelPx(16 * d, 16f, d), 1e-4f)
        assertEquals(48 * d * 0.20f, TableCut.bevelPx(48 * d, 48f, d), 1e-4f)
    }

    @Test
    fun theBevelIsNeverUnder1Point2dp() {
        // An 8px cell off the canvas (density 1): 24% would be 1.92px, above the floor.
        assertEquals(1.92f, TableCut.bevelPx(8f, 8f, 1f), 1e-4f)
        // A small sprite at a high density: 24% of 8dp at 0.5 density would be under 1.2dp.
        assertEquals(1.2f * 3f, TableCut.bevelPx(8f, 8f, 3f), 1e-4f)
    }

    @Test
    fun theCatchlightShowsFrom20dp() {
        assertEquals(0f, TableCut.catchlightPx(19.9f, d))
        assertEquals(0.8f * d, TableCut.catchlightPx(20f, d), 1e-4f)
        assertEquals(1f, TableCut.catchlightPx(20f, 1f), "at least 1px")
    }

    @Test
    fun aGemAveragesToItsTrueColorAtMidTonesAndStaysCloseAtTheEnds() {
        // Each bevel is a trapezoid of area b(1 - b) of the unit cell; an overlay of alpha a in white or black moves a
        // channel c by a(255 - c) or -a·c over it. The lit and shaded bevels cancel at mid tones (within 2 levels from
        // 104 to 128); at the ends a white overlay can't lighten white or a black one darken black, so a white gem
        // averages a little darker and a black one a little lighter, never more than 18 levels (about 7%).
        for (share in listOf(TableCut.WIDE_BEVEL, TableCut.NARROW_BEVEL)) {
            val area = share * (1 - share)
            val lit = alpha(TableCut.TOP) + alpha(TableCut.LEFT)
            val shade = alpha(TableCut.RIGHT) + alpha(TableCut.BOTTOM)
            fun shift(c: Int) = area * (lit * (255 - c) - shade * c)
            for (c in 104..128 step 4) assertTrue(abs(shift(c)) <= 2f, "channel $c at bevel $share moves by ${shift(c)}")
            for (c in 0..255) assertTrue(abs(shift(c)) <= 18f, "channel $c at bevel $share moves by ${shift(c)}")
        }
    }

    @Test
    fun theOverlaysAreTheSpecsAlphas() {
        assertEquals(0.24f, alpha(TableCut.TOP), 0.005f)
        assertEquals(0.09f, alpha(TableCut.LEFT), 0.005f)
        assertEquals(0.14f, alpha(TableCut.RIGHT), 0.005f)
        assertEquals(0.24f, alpha(TableCut.BOTTOM), 0.005f)
        assertEquals(0.38f, alpha(TableCut.CATCHLIGHT), 0.005f)
    }

    private fun alpha(argb: Int) = ((argb ushr 24) and 0xFF) / 255f
}
