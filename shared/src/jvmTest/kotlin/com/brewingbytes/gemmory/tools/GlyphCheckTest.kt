package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.remote.Romanian
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T49 (#136, eng O-E6): every word the app shows, in every language, draws in the faces we bundled for that language
 * (design system Typography → Other scripts). That covers the time-lapse frame and the streak card, which draw the
 * same strings into pictures, and the bundled picture titles (`title_<id>`). A character a face lacks fails the build.
 */
class GlyphCheckTest {
    private val glyphs = GlyphCheck(File(AndroidStringsFile.RES, "font"))

    @Test
    fun everyStringDrawsInItsLanguagesFaces() {
        val files = AndroidStringsFile.all()
        assertTrue(files.size >= 12, "English and the eleven: ${files.map { it.tag }}")
        val problems = files.flatMap { file ->
            file.texts.mapNotNull { (name, text) ->
                glyphs.missing(text, file.language).takeIf { it.isNotEmpty() }?.let { "${file.folder.name}/$name: $it" }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun romanianUsesTheCommaBelowLetters() {
        val ro = AndroidStringsFile.all().single { it.tag == "ro" }
        assertEquals(emptyList(), ro.texts.filterValues(Romanian::hasCedilla).keys.toList(), "ş/ţ: write ș/ț (Romanian.normalize)")
    }

    @Test
    fun aMissingCharacterIsNamed() {
        // Figtree has no kana; Zen Kaku Gothic New has them. A batch title that only a phone font draws is a warning.
        assertEquals(setOf("か"), glyphs.missingIn("かA", "figtree.ttf"))
        assertEquals(emptyMap(), glyphs.missing("神奈川沖浪裏", "ja"))
        val warnings = glyphs.warnings(mapOf("wave" to mapOf("de" to "Welle かな")))
        assertTrue(warnings.isNotEmpty() && warnings.all { "title_de" in it && "か" in it }, "$warnings")
    }
}
