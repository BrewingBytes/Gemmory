package com.brewingbytes.gemmory.ui

import com.brewingbytes.gemmory.render.RenderAssets

/**
 * The play canvas's fonts (iOS Stage 1a, T62): the app's own Atkinson Hyperlegible Mono, the same face the UI uses,
 * from composeApp's resources. Common since the Play screen moved (T64), so both platforms draw the same numbers.
 */
object AppRenderAssets : RenderAssets {
    override val numbers = AtkinsonMono
}
