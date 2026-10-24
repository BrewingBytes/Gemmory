package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.PlacementTools
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MysteryTest {
    private val drop = LocalDate(2026, 11, 3)

    @Test
    fun aMysteryIsHiddenUntilItsFinished() {
        assertTrue(Mystery.hidden(mystery = true, finished = false))
        assertFalse(Mystery.hidden(mystery = true, finished = true))
        assertFalse(Mystery.hidden(mystery = false, finished = false), "R1: an ordinary picture is never hidden")
    }

    /** The finish is the reveal whether the last gem comes from a tap or a batch, at 50% and 99% it's still hidden. */
    @Test
    fun aBatchThatPlacesTheLastGemRevealsIt() {
        val palette = List(2) { PaletteColor(it + 1, 0xFF000000.toInt() or it * 90, Lab(it * 40.0, 0.0, 0.0)) }
        val pattern = Pattern(4, 2, palette, intArrayOf(0, 0, 0, 0, 1, 1, 1, 1))
        val state = CanvasState.empty(pattern)
        for (i in 0 until 4) state.place(i, 0)
        assertTrue(Mystery.hidden(true, state.isFinished), "halfway, nothing shows")
        val batch = PlacementTools.place(state, PlacementTools.fillColor(pattern, state::isPlaced, slot = 1, from = 4), selectedSlot = 1)
        assertTrue(batch.pictureFinished)
        assertFalse(Mystery.hidden(true, state.isFinished))
    }

    @Test
    fun itsNamedForItsDateThenPlainly() {
        assertEquals(MysteryName.TODAY, Mystery.name(drop, drop))
        assertEquals(MysteryName.LATER, Mystery.name(drop, LocalDate(2026, 11, 4)))
        assertEquals(MysteryName.LATER, Mystery.name(null, drop), "withdrawn: its date is gone with its entry")
    }
}
