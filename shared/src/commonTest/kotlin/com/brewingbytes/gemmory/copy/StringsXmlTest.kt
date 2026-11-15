package com.brewingbytes.gemmory.copy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The copy reader on every target (T63): iOS reads the app's words through it. The real files are in `StringsXmlFilesTest`. */
class StringsXmlTest {
    private val xml = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <!-- A comment with <markup> and %% in it. -->
            <string name="app_name">Gemmory</string>
            <string name="done">%1${'$'}d%% done</string>
            <string name="pair">%2${'$'}s &amp; %1${'$'}s</string>
            <string name="escapes">It\'s \"here\"\nnext é \@ \? \\ &lt;b&gt; &#233;&#x1F48E;</string>
            <string name="plain">%s has %d</string>
            <plurals name="fills">
                <item quantity="one">%1${'$'}d umplere</item>
                <item quantity="few">%1${'$'}d umpleri</item>
                <item quantity="other">%1${'$'}d de umpleri</item>
            </plurals>
        </resources>
    """.trimIndent()

    private val ro = StringsXml.parse("ro", xml)

    @Test
    fun aPercentSignIsWrittenOnce() {
        assertEquals("82% done", ro.text("done", 82))
    }

    @Test
    fun positionalArgumentsGoWhereTheyPoint() {
        assertEquals("b & a", ro.text("pair", "a", "b"))
        assertEquals("Ana has 3", ro.text("plain", "Ana", 3))
    }

    @Test
    fun escapesAndEntitiesReadAsAaptReadsThem() {
        assertEquals("It's \"here\"\nnext é @ ? \\ <b> é💎", ro.text("escapes"))
    }

    @Test
    fun pluralsFollowTheFilesLanguage() {
        assertEquals("1 umplere", ro.plural("fills", 1, 1))
        assertEquals("2 umpleri", ro.plural("fills", 2, 2))
        assertEquals("21 de umpleri", ro.plural("fills", 21, 21))
        // English rules on the same items would say "one"/"other" only.
        assertEquals("2 de umpleri", StringsXml("en", ro.strings, ro.plurals).plural("fills", 2, 2))
    }

    @Test
    fun aMissingNameFallsBackToTheBase() {
        val en = StringsXml.parse("en", "<resources><string name=\"only_en\">English</string><string name=\"app_name\">G</string></resources>")
        val mixed = ro.over(en)
        assertEquals("English", mixed.text("only_en"))
        assertEquals("Gemmory", mixed.text("app_name"))
        assertEquals("ro", mixed.tag)
    }

    @Test
    fun foldersAndTagsMapBothWays() {
        assertEquals("en", StringsXml.tagOf("values"))
        assertEquals("pt-BR", StringsXml.tagOf("values-pt-rBR"))
        assertEquals("values", StringsXml.folderOf("en"))
        assertEquals("values-pt-rBR", StringsXml.folderOf("pt-BR"))
        assertEquals("values-ja", StringsXml.folderOf("ja"))
    }

    @Test
    fun unmodelledMarkupFails() {
        assertFailsWith<IllegalArgumentException> { StringsXml.parse("en", "<resources><string name=\"b\"><b>x</b></string></resources>") }
        assertFailsWith<IllegalArgumentException> { StringsXml.parse("en", "<resources><string-array name=\"a\"></string-array></resources>") }
        assertFailsWith<IllegalArgumentException> { StringsXml.unescape("\" quoted\"") }
        assertFailsWith<IllegalArgumentException> { StringsXml.unescape("bad \\x") }
        assertFailsWith<IllegalArgumentException> { StringsXml.format("%1\$.2f", arrayOf(1.0)) }
        assertFailsWith<IllegalArgumentException> { StringsXml.format("%2\$s", arrayOf("one")) }
    }
}
