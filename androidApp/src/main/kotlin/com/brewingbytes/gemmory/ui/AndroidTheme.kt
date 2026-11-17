package com.brewingbytes.gemmory.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.brewingbytes.gemmory.app.LocalShellUi
import com.brewingbytes.gemmory.app.ShellUi
import com.brewingbytes.gemmory.copy.strings
import kotlin.random.Random

/**
 * The shared theme ([GemmoryTheme] in composeApp, T64) in the language Android shows: the same `Words` the app's
 * `Strings` read, so a shared screen and an Android one say the same thing. It also provides Android's [ShellUi]
 * slots, so every shared screen under it (and every connected test that draws one) has them.
 */
@Composable
fun GemmoryTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShellUi provides AndroidShellUi) { GemmoryTheme(strings().words, content) }
}

/** The felt's ~9% grain as an Android [Bitmap], for the streak share card (#104): the shared felt's seeded noise. */
fun grainBitmap(): Bitmap {
    val size = 96
    val random = Random(0x6E6D)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (23 shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
