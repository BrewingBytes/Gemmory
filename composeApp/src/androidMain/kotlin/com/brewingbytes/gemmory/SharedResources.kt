package com.brewingbytes.gemmory

import android.content.res.AssetManager

/**
 * composeApp's Compose resources as Android reads them (iOS Stage 1b, T63): the fonts, bundled pictures and share
 * images live once in `composeApp/src/commonMain/composeResources/`, and the Android build packages them as assets
 * under [ROOT]. The app reads them synchronously through the `AssetManager`, as it read its own assets and `R.font`
 * before; iOS reads the same files through `Res`.
 */
object SharedResources {
    /** Where the Compose resources plugin puts composeApp's resources in the APK (its `packageOfResClass`). */
    private const val ROOT = "composeResources/com.brewingbytes.gemmory.compose.res/"

    /**
     * The app's assets, set first thing in [GemmoryApp.onCreate]: the type scale and the numbers' face are made outside
     * any composition or `Context` ([com.brewingbytes.gemmory.ui.GemType]).
     */
    lateinit var assets: AssetManager

    /** A file under `composeResources/files/`, by its path there ("pictures/starter.gmrg"). */
    fun file(path: String): String = "${ROOT}files/$path"

    /** A font under `composeResources/font/`, by its file name without `.ttf` ("figtree"). */
    fun font(name: String): String = "${ROOT}font/$name.ttf"
}
