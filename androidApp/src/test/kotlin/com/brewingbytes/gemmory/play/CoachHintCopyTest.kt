package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.copy.XmlStrings
import org.junit.Test
import kotlin.test.assertEquals

/** The hints' exact copy (design D12), read by name from the English strings file; their behavior is tested in composeApp. */
class CoachHintCopyTest {
    @Test
    fun theExactCopy() {
        val words = XmlStrings.en.words
        assertEquals("Pinch or tap to zoom in until numbers appear", words.text(CoachHint.ZOOM_IN.text))
        assertEquals("Pick a color below, then tap or drag its numbers", words.text(CoachHint.PICK_COLOR.text))
        assertEquals("Zoomed in so you can see the numbers", words.text(CoachHint.TAP_ZOOMED.text))
    }
}
