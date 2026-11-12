package com.brewingbytes.gemmory.ui

import com.brewingbytes.gemmory.render.RenderAssets

/**
 * The shared renderers' fonts on Android (iOS Stage 1a, T62): the app's own `R.font` faces, the same ones the UI
 * uses. Interim, as [RenderAssets] is: Stage 1b reads them from the shared Compose resources instead.
 */
object AppRenderAssets : RenderAssets {
    override val numbers = AtkinsonMono
}
