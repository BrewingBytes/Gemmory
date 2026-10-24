package com.brewingbytes.gemmory.clips

import com.brewingbytes.gemmory.timelapse.ClipOrder
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Clips screen's languages and file names (#152). */
class ClipFilesTest {
    @Test
    fun everyFileCarriesItsLanguageButTheFinishedStill() {
        assertEquals("wave-by-color-de.mp4", ClipRenderer.fileName("wave", ClipOrder.BY_COLOR, ClipFormat.NINE_SIXTEEN, "de"))
        assertEquals("wave-ripple-4x5-pt-BR.mp4", ClipRenderer.fileName("wave", ClipOrder.RIPPLE, ClipFormat.FOUR_FIVE, "pt-BR"))
        assertEquals("wave-random-feed-ro.mp4", ClipRenderer.fileName("wave", ClipOrder.RANDOM, ClipFormat.FEED, "ro"))
        assertEquals("wave-before-after-ja.png", ClipRenderer.fileName("wave", ClipOrder.RANDOM, ClipFormat.STILLS, "ja"))
        // No two languages or formats share a name.
        val names = ClipLanguages.ALL.flatMap { l -> ClipFormat.entries.map { ClipRenderer.fileName("wave", ClipOrder.BY_COLOR, it, l.tag) } }
        assertEquals(names.size, names.toSet().size)
    }

    /** The chips are the app's languages: every `values*` folder's `app_locale`, and nothing else. */
    @Test
    fun theLanguagesAreTheAppsTwelve() {
        val res = listOf(File("src/main/res"), File("androidApp/src/main/res")).first { it.isDirectory }
        val tags = res.listFiles().orEmpty()
            .mapNotNull { File(it, "strings.xml").takeIf(File::isFile) }
            .mapNotNull { Regex("""<string name="app_locale">([^<]+)</string>""").find(it.readText())?.groupValues?.get(1) }
            .toSet()
        assertEquals(tags, ClipLanguages.ALL.map { it.tag }.toSet())
    }

    @Test
    fun theAppsLanguageIsPickedFirst() {
        assertEquals("de", ClipLanguages.appTag(Locale.GERMAN))
        assertEquals("pt-BR", ClipLanguages.appTag(Locale.forLanguageTag("pt-BR")))
        assertEquals("en", ClipLanguages.appTag(Locale.forLanguageTag("nl")))
    }
}
