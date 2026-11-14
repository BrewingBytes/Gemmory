package com.brewingbytes.gemmory.timelapse

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.File

/**
 * What the time-lapse frame reads from the app on Android, whichever renderer draws it (T62): the share assets, the
 * lockup's store weight and the cropped source photo. It outlives the old `FrameArt`, which is deleted with
 * `BuildConfig.SHARED_RENDERER` (R10-8).
 */
object FrameAssets {
    /** "Google Play" in the founder clip lockup's store line (Figtree 700; a static face draws as is). */
    const val LOCKUP_STORE_WEIGHT = 700
    const val ICON_ASSET = "share/app_icon.png"

    /** Google's official "Get it on Google Play" badge (play.google.com/intl/en_us/badges), the file unchanged. */
    const val BADGE_ASSET = "share/play_badge_en.png"

    /** The languages with Google's badge in their own words (T49), each file unchanged; named by language code. */
    val BADGE_LANGUAGES = setOf("en", "de", "es", "pt", "fr", "it", "ro", "pl", "tr", "hu", "bg", "ja")

    /** The badge for the app's [language] (`Locale.language`; Brazil's `pt_br` badge is `pt`), English otherwise. */
    fun badgeAsset(language: String): String = if (language in BADGE_LANGUAGES) "share/play_badge_$language.png" else BADGE_ASSET

    /** The cropped source at the canvas's size; null if it's missing or unreadable (the intro is then tints only). */
    fun decodeSource(file: File, width: Int, height: Int): Bitmap? {
        if (!file.isFile) return null
        val decoded = BitmapFactory.decodeFile(file.path) ?: return null
        if (decoded.width == width && decoded.height == height) return decoded
        return decoded.scale(width, height).also { if (it !== decoded) decoded.recycle() }
    }
}
