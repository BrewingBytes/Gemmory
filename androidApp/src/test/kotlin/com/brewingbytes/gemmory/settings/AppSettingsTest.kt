package com.brewingbytes.gemmory.settings

import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.play.HighlightStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsTest {
    @Test
    fun defaultsAreSoundHapticsAndStrongerTint() {
        val settings = AppSettings(FakePrefs())
        assertTrue(settings.sound.value)
        assertTrue(settings.haptics.value)
        assertEquals(HighlightStyle.TINT, settings.highlight.value)
    }

    @Test
    fun theHighlightStyleRoundTripsThroughThePrefs() {
        val prefs = FakePrefs()
        for (style in HighlightStyle.entries) {
            val settings = AppSettings(prefs)
            settings.setHighlight(style)
            assertEquals(style, settings.highlight.value, "applied at once")
            assertEquals(style, AppSettings(prefs).highlight.value, "read back on the next start")
        }
        assertEquals(mapOf("highlight" to "dim"), prefs.values.filterKeys { it == "highlight" })
    }

    @Test
    fun anUnknownStoredStyleFallsBackToTheDefault() {
        val prefs = FakePrefs()
        prefs.values["highlight"] = "sparkle"
        assertEquals(HighlightStyle.TINT, AppSettings(prefs).highlight.value)
    }
}
