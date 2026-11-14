package com.brewingbytes.gemmory.render

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

/**
 * Simple breaks, with hyphens for German and Hungarian. Compose on iOS has no phrase breaks for Japanese, so a
 * Japanese title breaks between characters here: `GoldenIosTest` reports how far that is from Android's phrase breaks
 * (docs/todos.md, "Japanese phrase breaks on iOS").
 */
internal actual fun wrapStyle(language: String): TextStyle = when (language) {
    "de", "hu" -> TextStyle(lineBreak = LineBreak.Simple, hyphens = Hyphens.Auto)
    else -> TextStyle(lineBreak = LineBreak.Simple, hyphens = Hyphens.None)
}

/** iOS draws emoji with the system font already. */
internal actual fun drawnPlatformStyle(): PlatformTextStyle? = null
