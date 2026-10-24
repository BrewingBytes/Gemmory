package com.brewingbytes.gemmory.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * [text] (a resolved string resource) with each of its [figures] in [style], found in order: "3 of 7 finished" with
 * 3 and 7 in mono. A figure the words don't contain is left plain (T48: the words and their order come from resources).
 */
fun withFigures(text: String, figures: List<String>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    var from = 0
    for (figure in figures) {
        val at = text.indexOf(figure, from)
        if (at < 0) continue
        append(text.substring(from, at))
        withStyle(style) { append(figure) }
        from = at + figure.length
    }
    append(text.substring(from))
}
