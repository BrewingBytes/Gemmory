package com.brewingbytes.gemmory.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import com.brewingbytes.gemmory.SharedResources

internal actual fun appFont(face: AppFonts.Face, weight: FontWeight): Font = if (face.variable) {
    Font(face.path, SharedResources.assets, weight, variationSettings = FontVariation.Settings(FontVariation.weight(face.weight)))
} else {
    Font(face.path, SharedResources.assets, weight)
}

/** Where the file is in the app's assets ([SharedResources]). */
val AppFonts.Face.path: String get() = SharedResources.font(file)

/** The face as an Android [Typeface] (the time-lapse frame, the streak card, the widget). */
fun AppFonts.Face.typeface(context: Context): Typeface =
    runCatching { Typeface.createFromAsset(context.assets, path) }.getOrDefault(Typeface.DEFAULT)

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

internal actual val phraseLineBreak: LineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
