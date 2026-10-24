package com.brewingbytes.gemmory.clips

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** A language a clip can be drawn in (#152): the app's eleven (T49) and English, by tag, named in itself. */
class ClipLanguage(val tag: String, val name: String)

object ClipLanguages {
    val ALL = listOf(
        ClipLanguage("en", "English"),
        ClipLanguage("de", "Deutsch"),
        ClipLanguage("es", "Español"),
        ClipLanguage("pt-BR", "Português"),
        ClipLanguage("fr", "Français"),
        ClipLanguage("it", "Italiano"),
        ClipLanguage("ro", "Română"),
        ClipLanguage("pl", "Polski"),
        ClipLanguage("tr", "Türkçe"),
        ClipLanguage("hu", "Magyar"),
        ClipLanguage("bg", "Български"),
        ClipLanguage("ja", "日本語"),
    )

    /** The tag of the language the app shows (`app_locale`), or English when it isn't one of ours. */
    fun appTag(appLocale: Locale): String =
        ALL.firstOrNull { it.tag == appLocale.toLanguageTag() }?.tag ?: ALL.firstOrNull { it.tag == appLocale.language }?.tag ?: "en"

    /** [base] with its resources in [tag]'s language: the frame, the title and the stills draw in it; the app's language doesn't change. */
    fun context(base: Context, tag: String): Context =
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
}
