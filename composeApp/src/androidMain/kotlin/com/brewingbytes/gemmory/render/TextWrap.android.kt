package com.brewingbytes.gemmory.render

import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

/**
 * `StaticLayout.Builder`'s defaults (simple strategy, no strictness, no hyphens), with hyphens for German and
 * Hungarian and phrase breaks for Japanese (Android 13+), as androidApp's `wrapFor`. One difference: `wrapFor`'s
 * hyphens are `HYPHENATION_FREQUENCY_NORMAL`, and Compose 1.12.1's `Hyphens.Auto` is `FULL` (`FULL_FAST` on 13+),
 * which hyphenates more readily; Compose has no normal frequency (docs/todos.md).
 */
internal actual fun wrapStyle(language: String): TextStyle = when (language) {
    "de", "hu" -> TextStyle(lineBreak = PLAIN, hyphens = Hyphens.Auto)
    "ja" -> TextStyle(lineBreak = PLAIN.copy(wordBreak = LineBreak.WordBreak.Phrase), hyphens = Hyphens.None)
    else -> TextStyle(lineBreak = PLAIN, hyphens = Hyphens.None)
}

/**
 * Emoji as the system font draws them, as `StaticLayout` did: Compose's default swaps in EmojiCompat's font once it has
 * loaded, so a title's emoji (and its line breaks) could differ from the old frame, and from one encode to the next.
 */
internal actual fun drawnPlatformStyle(): PlatformTextStyle? = DRAWN_PLATFORM

private val DRAWN_PLATFORM = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)

private val PLAIN = LineBreak(LineBreak.Strategy.Simple, LineBreak.Strictness.Default, LineBreak.WordBreak.Default)
