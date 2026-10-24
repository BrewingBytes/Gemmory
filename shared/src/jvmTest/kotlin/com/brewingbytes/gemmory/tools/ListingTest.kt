package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.remote.Romanian
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T49 X2 (#136): the Play listings in `tools/store/listing/<locale>/`, which the founder pastes into the Play Console.
 * Play's limits (title 30, short 80, full 4,000 characters), the store listing check in `docs/play-compliance.md`
 * (no "free", "best" or "#1" in the title), five captions for the five screenshots, drawn in our faces by
 * [StoreFrames], and Romanian with ș / ț.
 */
class ListingTest {
    private val root = File("../tools/store/listing")
    private val glyphs = GlyphCheck(File(AndroidStringsFile.RES, "font"))

    @Test
    fun everyLocaleFitsPlaysLimitsAndOurRules() {
        assertEquals(StoreFrames.LOCALES.keys, root.list()!!.toSet())
        val problems = ArrayList<String>()
        for ((locale, language) in StoreFrames.LOCALES) {
            fun read(name: String) = File(root, "$locale/$name.txt").readText().trimEnd('\n')
            val title = read("title")
            val short = read("short")
            val full = read("full")
            val captions = StoreFrames.captions(root, locale)
            if (title.length > 30) problems += "$locale title ${title.length} > 30"
            if (short.length > 80) problems += "$locale short ${short.length} > 80"
            if (full.length > 4000) problems += "$locale full ${full.length} > 4000"
            if (captions.size != StoreFrames.SHOTS.size) problems += "$locale has ${captions.size} captions, not ${StoreFrames.SHOTS.size}"
            BANNED.filter { title.lowercase().contains(it) }.forEach { problems += "$locale title says \"$it\"" }
            if (language == "ro" && listOf(title, short, full, captions.joinToString()).any(Romanian::hasCedilla)) problems += "ro: ş/ţ"
            captions.forEach { c -> glyphs.missing(c, language).takeIf { it.isNotEmpty() }?.let { problems += "$locale caption \"$c\": $it" } }
        }
        assertEquals(emptyList(), problems)
        // Every caption fits its frame (two lines; Japanese one), drawn as the frame tool draws it.
        val shot = java.awt.image.BufferedImage(1080, 1920, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for ((locale, language) in StoreFrames.LOCALES) {
            for (caption in StoreFrames.captions(root, locale)) {
                StoreFrames.frame(shot, caption, language, File(AndroidStringsFile.RES, "font"), File("../tools/store/fonts"))
            }
        }
        assertTrue(File(root, "es-419/full.txt").readText() == File(root, "es-ES/full.txt").readText(), "one Spanish (C3)")
    }

    private companion object {
        /** Ranking, price and promise words Play doesn't allow in a title, in the listing languages. */
        val BANNED = listOf(
            "free", "best", "#1", "top ", "kostenlos", "gratis", "gratuit", "gratuito", "darmow", "ücretsiz", "ingyen",
            "безплат", "無料", "beste", "mejor", "melhor", "meilleur", "migliore", "najlepsz", "en iyi", "legjobb", "най-добр", "最高",
        )
    }
}
