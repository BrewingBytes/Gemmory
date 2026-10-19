package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The frost (design system, Hidden pictures; T36): 6 blocks across, rows by aspect, blurred, then felt-deep at 45%. */
class FrostTest {
    private val black = PaletteColor(1, 0xFF000000.toInt(), Lab(0.0, 0.0, 0.0))
    private val white = PaletteColor(2, 0xFFFFFFFF.toInt(), Lab(100.0, 0.0, 0.0))

    /** Left half black, right half white. */
    private fun halves(width: Int, height: Int) =
        Pattern(width, height, listOf(black, white), IntArray(width * height) { if (it % width < width / 2) 0 else 1 })

    private fun Int.red() = this shr 16 and 0xFF

    @Test
    fun sixBlocksAcrossAndRowsByAspect() {
        assertEquals(6 to 6, Frost.blocks(halves(56, 56)).let { it.columns to it.rows })
        assertEquals(6 to 8, Frost.blocks(halves(78, 104)).let { it.columns to it.rows })
        assertEquals(6 to 5, Frost.blocks(halves(104, 78)).let { it.columns to it.rows })
        val row = Frost.blocks(halves(56, 56)).argb.take(6).map { it.red() }
        assertEquals(listOf(0, 0, 0, 255, 255, 255), row, "each block is its cells' average")
    }

    @Test
    fun theBlurSoftensTheEdgeAndTheOverlayCoversIt() {
        val plain = Frost.image(halves(56, 56), 120, 120)
        val middle = plain.slice(60 * 120 until 61 * 120).map { it.red() }
        assertTrue(middle.first() < 40 && middle.last() > 215, "the sides keep their colors: $middle")
        assertTrue(middle.count { it in 40..215 } >= 10, "no hard edge in the middle: $middle")
        val felt = 0xFF3E4741.toInt()
        val covered = Frost.image(halves(56, 56), 120, 120, felt)
        // Black under 45% felt-deep is 45% of its red; white is 55% white plus that.
        assertEquals((0x3E * 0.45f).toInt().toFloat(), covered[60 * 120].red().toFloat(), 2f)
        assertEquals((255 * 0.55f + 0x3E * 0.45f).toInt().toFloat(), covered[60 * 120 + 119].red().toFloat(), 2f)
        assertTrue(covered.all { it ushr 24 == 0xFF }, "opaque")
    }
}
