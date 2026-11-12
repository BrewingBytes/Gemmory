package com.brewingbytes.gemmory.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The golden bar's arithmetic, which every renderer golden on Android and iOS relies on (review, #167). */
class GoldenMathTest {
    private val gray = IntArray(64 * 64) { 0xFF808080.toInt() }

    @Test
    fun identicalImagesPass() {
        assertEquals(1.0, GoldenMath.ssim(gray, gray.copyOf(), 64), 1e-9)
        val diff = GoldenMath.diff(gray, gray.copyOf())
        assertEquals(0, diff.maxDiff)
        assertTrue(diff.geometryPasses)
    }

    @Test
    fun onePixelOffByMoreThan32FailsGeometry() {
        val off = gray.copyOf().also { it[0] = 0xFFA18080.toInt() } // red 0x80 → 0xA1: 33/255
        val diff = GoldenMath.diff(gray, off)
        assertEquals(33, diff.maxDiff)
        assertFalse(diff.geometryPasses)
    }

    @Test
    fun maskedPixelsDontCount() {
        val off = gray.copyOf().also { it[0] = 0xFFFFFFFF.toInt() }
        assertTrue(GoldenMath.diff(gray, off, GoldenMath.mask(64, 64, listOf(intArrayOf(0, 0, 1, 1)))).geometryPasses)
    }

    // Regression: a region with no 8×8 window used to score 1.0, so a mask that missed its text passed the bar.
    @Test
    fun aRegionWithNoWindowFailsTheBar() {
        val white = IntArray(64 * 64) { -1 }
        val tiny = GoldenMath.mask(64, 64, listOf(intArrayOf(0, 0, 5, 5)))
        val ssim = GoldenMath.ssim(gray, white, 64, tiny)
        assertTrue(ssim.isNaN(), "ssim=$ssim")
        assertFalse(ssim >= 0.98)
    }
}
