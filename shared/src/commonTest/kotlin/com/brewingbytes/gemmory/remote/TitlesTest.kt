package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemoteFixtures.KEY
import com.brewingbytes.gemmory.remote.RemoteFixtures.catalogJson
import com.brewingbytes.gemmory.remote.RemoteFixtures.grid
import com.brewingbytes.gemmory.remote.RemoteFixtures.picture
import com.brewingbytes.gemmory.remote.RemoteFixtures.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T49 X4 (#136): translated titles in the catalog, and the title shown for the app's language. */
class TitlesTest {
    private val fox = picture("fox-in-snow", grid(CanvasSize.BIG, AspectRatio.PORTRAIT, colors = 30), CanvasSize.BIG, AspectRatio.PORTRAIT)
    private val titled = fox.copy(titles = mapOf("de" to "Fuchs im Schnee", "pt-BR" to "Raposa na neve", "ja" to "雪の中のキツネ"))

    private fun parse(bytes: ByteArray) = assertIs<CatalogCheck.Ok>(RemoteCatalogs.check(bytes, sign(bytes), listOf(KEY), 99, null, emptySet())).catalog

    @Test
    fun theChainTriesTheRegionThenTheLanguageThenEnglish() {
        assertEquals(listOf("pt-BR", "pt"), Titles.chain("pt-BR"))
        assertEquals(listOf("de"), Titles.chain("de"))
        val titles = mapOf("pt" to "Raposa", "de" to "Fuchs")
        assertEquals("Raposa", Titles.pick(titles, "Fox", "pt-BR"))
        assertEquals("Fuchs", Titles.pick(titles, "Fox", "de-AT"))
        assertEquals("Fox", Titles.pick(titles, "Fox", "ja"), "a missing language shows English")
        assertEquals("Fox", Titles.pick(emptyMap(), "Fox", "en"))
    }

    @Test
    fun titlesRoundTripForPicturesAndCollections() {
        val seaside = RemoteCollection("seaside", "Seaside", listOf("fox-in-snow"), titles = mapOf("fr" to "Bord de mer"))
        val catalog = parse(catalogJson(listOf(titled), collections = listOf(seaside)))
        assertEquals(listOf(titled), catalog.pictures)
        assertEquals(listOf(seaside), catalog.collections)
        assertEquals("雪の中のキツネ", catalog.pictures.single().titleIn("ja"))
        assertEquals("Raposa na neve", catalog.pictures.single().titleIn("pt-BR"))
        assertEquals("Bord de mer", catalog.collections.single().titleIn("fr"))
        assertEquals("Seaside", catalog.collections.single().titleIn("it"))
    }

    @Test
    fun noTitlesWritesTheEntryAsBefore() {
        // An entry without translations is byte for byte what the tool wrote before T49 (R1).
        assertFalse("titles" in catalogJson(listOf(fox)).decodeToString())
        val written = catalogJson(listOf(titled)).decodeToString()
        assertTrue("\"titles\"" in written && "Fuchs im Schnee" in written && "雪の中のキツネ" in written, written)
    }

    /**
     * R1: an app before T49 (0.8.3) reads a catalog with `titles` like one without (an unknown key is skipped) and so
     * never needs a higher `minAppVersion`; this app reads the same entry with its titles.
     */
    @Test
    fun titlesNeverRaiseTheMinimumApp() {
        assertEquals(RemoteCatalogs.lowestApp(fox), RemoteCatalogs.lowestApp(titled))
        val parsed = parse(catalogJson(listOf(titled))).pictures.single()
        assertEquals(fox, parsed.copy(titles = emptyMap()))
    }

    @Test
    fun aBadTitlesMapIsIgnoredNeverRefused() {
        fun withTitles(json: String): RemotePicture {
            val text = catalogJson(listOf(fox)).decodeToString().replaceFirst("\"title\":", "\"titles\": $json, \"title\":")
            return parse(text.encodeToByteArray()).pictures.single()
        }
        val tooLong = "x".repeat(RemoteCatalogs.MAX_TITLE + 1)
        for (bad in listOf("\"Fuchs\"", "[]", "{\"de\": 3}", "{\"de\": \"\"}", "{\"de\": \"$tooLong\"}", "{\"de\": \"a\\u0007b\"}", "{\"German\": \"Fuchs\"}")) {
            assertEquals(emptyMap(), withTitles(bad).titles, bad)
            assertEquals("Fox-in-snow", withTitles(bad).titleIn("de"))
        }
        assertEquals(mapOf("de" to "Fuchs"), withTitles("{\"de\": \"Fuchs\"}").titles)
    }

    @Test
    fun aMysteryKeepsItsTitlesHiddenLikeItsEnglishOne() {
        // The app hides an unfinished mystery by name (Mystery.hidden), above whichever title the language picks.
        assertTrue(Mystery.hidden(mystery = true, finished = false))
        assertFalse(Mystery.hidden(mystery = true, finished = true))
    }

    @Test
    fun romanianIsNormalizedToTheCommaBelowLetters() {
        assertEquals("Ștrand și țărm", Romanian.normalize("Ştrand şi ţărm"))
        assertTrue(Romanian.hasCedilla("ţ"))
        assertFalse(Romanian.hasCedilla("ț"))
    }
}
