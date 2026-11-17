package com.brewingbytes.gemmory.copy

import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared words (T64): a language over English, its own plural rules, and numbers and weekdays in that language. */
class WordsTest {
    private val files = mapOf(
        "strings/values/strings.xml" to """
            <resources>
                <string name="size_quick">Quick</string>
                <string name="only_english">Only English</string>
                <plurals name="fills"><item quantity="one">%1${'$'}d fill</item><item quantity="other">%1${'$'}d fills</item></plurals>
            </resources>
        """.trimIndent(),
        "strings/values-pl/strings.xml" to """
            <resources>
                <string name="size_quick">Szybki</string>
                <plurals name="fills">
                    <item quantity="one">%1${'$'}d wypełnienie</item>
                    <item quantity="few">%1${'$'}d wypełnienia</item>
                    <item quantity="many">%1${'$'}d wypełnień</item>
                    <item quantity="other">%1${'$'}d wypełnienia</item>
                </plurals>
            </resources>
        """.trimIndent(),
    )

    private fun words(tag: String) = Words.load(tag) { files.getValue(it) }

    @Test
    fun aLanguageFallsBackToEnglishForWhatItLacks() {
        val pl = words("pl")
        assertEquals("pl", pl.tag)
        assertEquals("Szybki", pl.text("size_quick"))
        assertEquals("Only English", pl.text("only_english"))
    }

    @Test
    fun pluralsFollowTheShownLanguage() {
        assertEquals("21 fills", words("en").plural("fills", 21, 21))
        assertEquals("21 wypełnień", words("pl").plural("fills", 21, 21))
        assertEquals("22 wypełnienia", words("pl").plural("fills", 22, 22))
    }

    @Test
    fun numbersAndWeekdaysAreInTheShownLanguage() {
        assertEquals("4,812", LocaleFormats("en").integer(4812))
        assertEquals("4.812", LocaleFormats("de").integer(4812))
        // 1970-01-01 was a Thursday.
        assertEquals("Thursday", LocaleFormats("en").weekday(0))
        assertEquals("Donnerstag", LocaleFormats("de").weekday(0))
        assertEquals("木曜日", LocaleFormats("ja").weekday(0))
        assertEquals("Wednesday", LocaleFormats("en").weekday(-1))
    }

    @Test
    fun aFirstLetterIsLoweredByTheLanguagesOwnRules() {
        assertEquals("ıstanbul yolda", LocaleFormats("tr").lowercaseFirst("Istanbul yolda"), "Turkish dotless I")
        assertEquals("istanbul yolda", LocaleFormats("en").lowercaseFirst("Istanbul yolda"))
        assertEquals("jeszcze 4 w drodze", words("pl").lowercaseFirst("Jeszcze 4 w drodze"))
        assertEquals("", LocaleFormats("en").lowercaseFirst(""))
    }

    /** The streak's joined "kept it going" line and the wheel's "added" line start with a raised phrase (T64). */
    @Test
    fun aFirstLetterIsRaisedByTheLanguagesOwnRules() {
        assertEquals("İzin günün", LocaleFormats("tr").titlecaseFirst("izin günün"), "Turkish dotted I")
        assertEquals("Izin", LocaleFormats("en").titlecaseFirst("izin"))
        assertEquals("5 fills", words("en").titlecaseFirst("5 fills"))
        assertEquals("", LocaleFormats("en").titlecaseFirst(""))
    }
}
