package com.brewingbytes.gemmory.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.LineBreak
import com.brewingbytes.gemmory.compose.res.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

/**
 * The file read from the app bundle, where Compose resources put `composeResources/font/` ([Res.getUri] is
 * synchronous). The fonts ship inside the app, so a missing one is a packaging bug and fails at once.
 */
@OptIn(ExperimentalResourceApi::class)
internal actual fun appFont(face: AppFonts.Face, weight: FontWeight): Font {
    val name = "${face.file}.ttf"
    val data = read(Res.getUri("font/$name")) ?: error("font $name isn't in the app bundle")
    return if (face.variable) {
        Font(identity = name, data = data, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(face.weight)))
    } else {
        Font(identity = name, data = data, weight = weight)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun read(uri: String): ByteArray? {
    val url = NSURL.URLWithString(uri) ?: return null
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return bytes
}

internal actual val phraseLineBreak: LineBreak = LineBreak.Paragraph
