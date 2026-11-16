package com.brewingbytes.gemmory.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak

/**
 * The faces for the language the app shows (T49, #136, design system Typography → Other scripts). Figtree and Bricolage
 * Grotesque draw every Latin-script language; Bulgarian gets Onest (body) and Geologica (titles), Japanese Zen Kaku
 * Gothic New for both, which has no 600, so its "600" is the 700 file. Numbers stay Atkinson Hyperlegible Mono
 * everywhere. Compose reads these through [GemType]; on Android the frame and the streak card draw with `typeface`.
 */
enum class AppFonts(val display: Face, val body: Face, val bodyStrong: Face) {
    LATIN(Face("bricolage_grotesque", 600), Face("figtree", 400), Face("figtree", 600)),
    CYRILLIC(Face("geologica", 600), Face("onest", 400), Face("onest", 600)),
    JAPANESE(
        Face("zen_kaku_gothic_new_bold", 600, variable = false),
        Face("zen_kaku_gothic_new_regular", 400, variable = false),
        Face("zen_kaku_gothic_new_bold", 600, variable = false),
    );

    /**
     * One font file at one weight; a variable file is set to [weight] on its `wght` axis, a static one is drawn as is.
     * [file] is its name in composeApp's `composeResources/font/` (T63).
     */
    data class Face(val file: String, val weight: Int, val variable: Boolean = true) {
        /** The `fontVariationSettings` a `Paint` takes for this face, or null for a static file. */
        val variation: String? get() = if (variable) "'wght' $weight" else null
    }

    companion object {
        /** Atkinson Hyperlegible Mono at the design's 700: numbers in every language (DC3). */
        val MONO = Face("atkinson_hyperlegible_mono", 700)

        /** The faces for a language code (`Locale.language`: "bg", "ja", "de", …). */
        fun forLanguage(language: String): AppFonts = when (language) {
            "bg" -> CYRILLIC
            "ja" -> JAPANESE
            else -> LATIN
        }
    }
}

/**
 * [face] as a Compose [Font] marked [weight], a variable file set to the face's weight on its `wght` axis. Synchronous,
 * outside any composition: the type scale and the numbers' face are made once per process ([GemType]). Android reads
 * the file from its assets, iOS from the app bundle's Compose resources.
 */
internal expect fun appFont(face: AppFonts.Face, weight: FontWeight): Font

/**
 * Japanese breaks lines at phrases (DR6): Android's `LineBreak.WordBreak.Phrase`. Compose has it on Android alone, so
 * iOS breaks paragraphs plainly until its screens ship (Stage 2), as the iOS ja-wrap golden check reports.
 */
internal expect val phraseLineBreak: LineBreak
