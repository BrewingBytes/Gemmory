package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.remote.Romanian
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T49 X2 (#136): the Play listings in `tools/store/listing/<locale>/`, which the founder pastes into the Play Console.
 * Play's limits (title 30, short 80, full 4,000 characters), the store listing check in `docs/play-compliance.md`
 * (no "free", "best" or "#1" in the title), a title and a subtitle for each of the five screenshots and the feature
 * graphic's two lines and the tablet set's privacy line, drawn in our faces by [StoreFrames] and fitting their room, and Romanian with ș / ț.
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
            val subtitles = StoreFrames.subtitles(root, locale)
            val feature = StoreFrames.feature(root, locale)
            val privacy = StoreFrames.privacy(root, locale)
            if (title.length > 30) problems += "$locale title ${title.length} > 30"
            if (short.length > 80) problems += "$locale short ${short.length} > 80"
            if (full.length > 4000) problems += "$locale full ${full.length} > 4000"
            if (captions.size != StoreFrames.SHOTS.size) problems += "$locale has ${captions.size} captions, not ${StoreFrames.SHOTS.size}"
            if (subtitles.size != StoreFrames.SHOTS.size) problems += "$locale has ${subtitles.size} subtitles, not ${StoreFrames.SHOTS.size}"
            if (feature.size != 2) problems += "$locale feature.txt has ${feature.size} lines, not 2"
            BANNED.filter { title.lowercase().contains(it) }.forEach { problems += "$locale title says \"$it\"" }
            val drawn = captions + subtitles + feature + privacy
            if (language == "ro" && (listOf(title, short, full) + drawn).any(Romanian::hasCedilla)) problems += "ro: ş/ţ"
            drawn.forEach { c -> glyphs.missing(c, language).takeIf { it.isNotEmpty() }?.let { problems += "$locale caption \"$c\": $it" } }
        }
        assertEquals(emptyList(), problems)
        // Every title (two lines; Japanese one), subtitle and feature line (one) fits, drawn as the frame tool draws it.
        val shot = java.awt.image.BufferedImage(1080, 1920, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val fonts = File(AndroidStringsFile.RES, "font")
        val static = File("../tools/store/fonts")
        val misfits = ArrayList<String>()
        fun draw(locale: String, block: () -> Unit) = try { block() } catch (e: IllegalArgumentException) { misfits += "$locale: ${e.message}" }
        for ((locale, language) in StoreFrames.LOCALES) {
            StoreFrames.captions(root, locale).zip(StoreFrames.subtitles(root, locale)).forEach { (t, s) ->
                draw(locale) { StoreFrames.frame(shot, t, s, language, fonts, static) }
            }
            val (tagline, line) = StoreFrames.feature(root, locale)
            draw(locale) { StoreFrames.featureGraphic(shot, tagline, line, language, fonts, static) }
            // The tablet set: the first frame on the art with the privacy line, the rest on shots.
            val privacy = StoreFrames.privacy(root, locale)
            StoreFrames.captions(root, locale).zip(StoreFrames.subtitles(root, locale)).forEachIndexed { i, (t, s) ->
                draw("$locale tablet") {
                    StoreFrames.tabletFrame(shot.takeIf { i > 0 }, shot, t, s, privacy.takeIf { i == 0 }, language, fonts, static)
                }
            }
        }
        assertEquals(emptyList(), misfits)
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
