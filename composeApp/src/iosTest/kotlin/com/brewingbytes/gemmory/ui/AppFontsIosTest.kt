package com.brewingbytes.gemmory.ui

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every face of every script, and the numbers' face, loads from the bundle's Compose resources (T64): the type scale is
 * made outside any composition, so a font Compose resources put somewhere else would fail at the first screen.
 */
class AppFontsIosTest {
    @Test
    fun everyFaceLoadsFromTheBundle() {
        val faces = AppFonts.entries.flatMap { listOf(it.display, it.body, it.bodyStrong) } + AppFonts.MONO
        for (face in faces.distinct()) assertEquals(FontWeight.Bold, appFont(face, FontWeight.Bold).weight, face.file)
        for (language in listOf("en", "de", "hu", "ja", "bg")) GemTypeSet.of(language)
    }
}
