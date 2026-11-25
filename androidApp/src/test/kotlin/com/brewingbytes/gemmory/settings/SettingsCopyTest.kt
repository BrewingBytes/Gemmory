package com.brewingbytes.gemmory.settings

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.play.HighlightStyle
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The shared Settings screen (T64) names its copy as strings: the Selected color names and lines read as they did
 * through `R.string`, and the Music volume value is the same percent.
 */
class SettingsCopyTest {
    private val words = XmlStrings.en.words

    @Test
    fun theSelectedColorStylesReadAsBefore() {
        val shown = HighlightStyle.entries.associateWith { words.text(HighlightCopy.name(it)) to words.text(HighlightCopy.description(it)) }
        assertEquals(
            mapOf(
                HighlightStyle.TINT to ("Stronger tint" to "Its cells turn a deeper shade"),
                HighlightStyle.OUTLINE to ("Outline" to "A ring around its cells when zoomed in"),
                HighlightStyle.DIM to ("Dim the rest" to "Every other color steps back"),
            ),
            shown,
        )
    }

    @Test
    fun theMusicVolumeIsAPercent() {
        assertEquals("30%", words.text("percent", 30))
    }
}
