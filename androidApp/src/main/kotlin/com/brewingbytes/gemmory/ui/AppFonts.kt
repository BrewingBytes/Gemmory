package com.brewingbytes.gemmory.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.brewingbytes.gemmory.R

/**
 * The faces for the language the app shows (T49, #136, design system Typography → Other scripts). Figtree and Bricolage
 * Grotesque draw every Latin-script language; Bulgarian gets Onest (body) and Geologica (titles), Japanese Zen Kaku
 * Gothic New for both, which has no 600, so its "600" is the 700 file. Numbers stay Atkinson Hyperlegible Mono
 * everywhere. Compose reads these through [GemType]; the frame and the streak card draw with [typeface].
 */
enum class AppFonts(val display: Face, val body: Face, val bodyStrong: Face) {
    LATIN(Face(R.font.bricolage_grotesque, 600), Face(R.font.figtree, 400), Face(R.font.figtree, 600)),
    CYRILLIC(Face(R.font.geologica, 600), Face(R.font.onest, 400), Face(R.font.onest, 600)),
    JAPANESE(
        Face(R.font.zen_kaku_gothic_new_bold, 600, variable = false),
        Face(R.font.zen_kaku_gothic_new_regular, 400, variable = false),
        Face(R.font.zen_kaku_gothic_new_bold, 600, variable = false),
    );

    /** One font file at one weight; a variable file is set to [weight] on its `wght` axis, a static one is drawn as is. */
    data class Face(@FontRes val res: Int, val weight: Int, val variable: Boolean = true) {
        /** The face as an Android [Typeface] (the time-lapse frame, the streak card). */
        fun typeface(context: Context): Typeface = ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT

        /** The `fontVariationSettings` a `Paint` takes for this face, or null for a static file. */
        val variation: String? get() = if (variable) "'wght' $weight" else null
    }

    companion object {
        /** The faces for a language code (`Locale.language`: "bg", "ja", "de", …). */
        fun forLanguage(language: String): AppFonts = when (language) {
            "bg" -> CYRILLIC
            "ja" -> JAPANESE
            else -> LATIN
        }
    }
}

/**
 * A drawn title wraps like the app's text in [language] (DR6): German and Hungarian hyphenate, Japanese breaks at
 * phrases (Android 13+). Every other language is laid out as before T49.
 */
fun StaticLayout.Builder.wrapFor(language: String): StaticLayout.Builder = apply {
    when (language) {
        "de", "hu" -> setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        "ja" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setLineBreakConfig(LineBreakConfig.Builder().setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build())
        }
    }
}
