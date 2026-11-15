package com.brewingbytes.gemmory.copy

import com.brewingbytes.gemmory.tools.AndroidStringsFile
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app's real strings files through [StringsXml], the reader iOS uses (T63, eng D23 / OV-6 option B): every word of
 * every language reads as Android's resources give it, and formats as Android's `String.format` does.
 */
class StringsXmlFilesTest {
    private val files = AndroidStringsFile.all()

    private fun read(file: AndroidStringsFile): StringsXml = StringsXml.parse(file.tag, File(file.folder, "strings.xml").readText())

    @Test
    fun everyLanguageIsThere() {
        assertEquals(
            listOf("en", "bg", "de", "es", "fr", "hu", "it", "ja", "pl", "pt-BR", "ro", "tr"),
            files.map { it.tag },
        )
        files.forEach { assertEquals(it.folder.name, StringsXml.folderOf(it.tag)) }
    }

    /** The same text as a real XML parser plus aapt's unescape (`AndroidStringsFile`), for every string and plural item. */
    @Test
    fun readsEveryWordAsTheXmlParserDoes() {
        for (file in files) {
            val copy = read(file)
            val ours = LinkedHashMap<String, String>()
            copy.strings.forEach { (name, text) -> ours[name] = text }
            copy.plurals.forEach { (name, items) -> items.forEach { (q, text) -> ours["$name/$q"] = text } }
            assertEquals(file.texts, ours, file.tag)
        }
    }

    /** aapt collapses runs of whitespace; our files have none inside a string, so the reader needn't model it. */
    @Test
    fun noStringReliesOnWhitespaceCollapsing() {
        for (file in files) {
            val raw = File(file.folder, "strings.xml").readText()
            val bodies = Regex("""<(string|item)\b[^>]*>([^<]*)</\1>""").findAll(raw).map { it.groupValues[2] }
            bodies.forEach { assertTrue("  " !in it && '\n' !in it && '\t' !in it, "${file.tag}: <$it>") }
        }
    }

    /** `String.format` in the language's locale and [StringsXml.format] agree on every string, with every argument. */
    @Test
    fun formatsEveryStringAsStringFormatDoes() {
        for (file in files) {
            val locale = Locale.forLanguageTag(file.tag)
            val copy = read(file)
            val all = copy.strings.entries.map { it.key to it.value } +
                copy.plurals.flatMap { (name, items) -> items.map { (q, t) -> "$name/$q" to t } }
            for ((name, raw) in all) {
                val args = sampleArgs(raw) ?: continue
                assertEquals(String.format(locale, raw, *args), StringsXml.format(raw, args), "${file.tag} $name")
            }
        }
    }

    /** "21 fills": the plural item comes from the file's own language, and every language has the item its rules pick. */
    @Test
    fun twentyOneFillsInEveryLanguage() {
        val expected = mapOf(
            "en" to "21 fills",
            "ro" to "21 de umpleri",
            "pl" to "21 wypełnień",
        )
        for (file in files) {
            val copy = read(file)
            val quantity = PluralRules.quantity(file.language, 21)
            val item = copy.plurals.getValue("prize_fills")[quantity]
            assertTrue(item != null, "${file.tag} has no $quantity item for prize_fills")
            val text = copy.plural("prize_fills", 21, 21)
            assertEquals(String.format(Locale.forLanguageTag(file.tag), item, 21), text, file.tag)
            assertTrue("21" in text, "${file.tag}: $text")
            expected[file.tag]?.let { assertEquals(it, text, file.tag) }
        }
    }

    /** `%%` shows one percent sign in every language (Compose resources showed `%%`, the Stage 0 finding). */
    @Test
    fun aPercentSignShowsOnceInEveryLanguage() {
        for (file in files) {
            val text = read(file).text("progress_done", 82)
            assertTrue("82" in text && "%" in text && "%%" !in text, "${file.tag}: $text")
        }
        assertEquals("82% done", read(files.first()).text("progress_done", 82))
    }

    /** One argument per specifier: `%n$d` gets 21, `%n$s` gets a word; null when the string takes none. */
    private fun sampleArgs(raw: String): Array<Any>? {
        val numbered = Regex("""%(\d+)\$([sd])""").findAll(raw).map { it.groupValues[1].toInt() to it.groupValues[2] }.toList()
        val plain = Regex("""(?<!%)%([sd])""").findAll(raw.replace("%%", "")).map { it.groupValues[1] }.toList()
        if (numbered.isEmpty() && plain.isEmpty()) return null
        if (numbered.isNotEmpty()) {
            val count = numbered.maxOf { it.first }
            return Array(count) { i -> if (numbered.firstOrNull { it.first == i + 1 }?.second == "d") 21 else "Ana${i + 1}" }
        }
        return plain.mapIndexed { i, kind -> if (kind == "d") 21 else "Ana${i + 1}" }.toTypedArray<Any>()
    }
}
