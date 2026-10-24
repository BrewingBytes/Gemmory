package com.brewingbytes.gemmory.tools

import java.awt.Font
import java.io.File

/**
 * Build-time only (T49, #136): which characters a bundled font can't draw. The app's strings must draw in the faces we
 * picked for their language (a hard failure in `GlyphCheckTest`); a batch's translated title only warns, since the
 * phone's own font draws a missing character (eng O-E10, tension #1). Reads the TTFs in `androidApp/src/main/res/font`.
 */
class GlyphCheck(private val fontDir: File) {
    private val fonts = HashMap<String, Font>()

    /** The characters of [text] that one of [language]'s faces ([faces]) lacks, by font file; empty when all draw. */
    fun missing(text: String, language: String): Map<String, Set<String>> =
        faces(language).associateWith { file -> missingIn(text, file) }.filterValues { it.isNotEmpty() }

    /** [text]'s characters [file] can't draw, each as a string (a code point); line breaks and tabs aren't drawn. */
    fun missingIn(text: String, file: String): Set<String> {
        val font = fonts.getOrPut(file) { Font.createFont(Font.TRUETYPE_FONT, File(fontDir, file)) }
        return text.codePoints().toArray()
            .filter { it != '\n'.code && it != '\t'.code && !font.canDisplay(it) }
            .map { String(Character.toChars(it)) }
            .toSet()
    }

    /** One line per title a face can't fully draw, for the batch tool to print. */
    fun warnings(titles: Map<String, Map<String, String>>): List<String> = titles.flatMap { (id, byLanguage) ->
        byLanguage.flatMap { (tag, title) ->
            missing(title, tag.substringBefore('-')).map { (file, chars) ->
                "$id title_$tag: $file can't draw ${chars.joinToString(" ")}; the phone's own font draws them"
            }
        }
    }

    companion object {
        /**
         * The files that draw [language]'s words (design system Typography → Other scripts): Bricolage Grotesque and
         * Figtree for every Latin script, Geologica and Onest for Bulgarian, Zen Kaku Gothic New for Japanese.
         */
        fun faces(language: String): List<String> = when (language) {
            "bg" -> listOf("geologica.ttf", "onest.ttf")
            "ja" -> listOf("zen_kaku_gothic_new_bold.ttf", "zen_kaku_gothic_new_regular.ttf")
            else -> listOf("bricolage_grotesque.ttf", "figtree.ttf")
        }
    }
}
