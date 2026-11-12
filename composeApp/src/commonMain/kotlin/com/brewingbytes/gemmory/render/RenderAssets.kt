package com.brewingbytes.gemmory.render

import androidx.compose.ui.text.font.FontFamily

/**
 * What the shared renderers draw text with (iOS Stage 1a, T62, eng R10-3). It's interim: each platform shell fills it
 * (Android from today's `R.font`), and Stage 1b deletes it when the renderers read the shared Compose resources.
 * Later Stage 1a PRs add the time-lapse and streak card faces and their drawn strings here.
 */
interface RenderAssets {
    /** Atkinson Hyperlegible Mono, with a Bold face at 700 on its `wght` axis: the canvas's cell numbers. */
    val numbers: FontFamily
}
