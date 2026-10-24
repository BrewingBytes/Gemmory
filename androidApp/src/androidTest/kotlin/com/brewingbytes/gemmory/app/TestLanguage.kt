package com.brewingbytes.gemmory.app

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.copy.strings

/**
 * The app language a connected run captures in (T49, #136): `-e appLocale de` (any of the app's tags, `pt-BR`), set as
 * Gemmory's per-app language (Android 13+), the way Settings → Language does. Without it the run is English as before.
 */
object TestLanguage {
    /** The requested tag, or null for the phone's language. */
    val requested: String? get() = InstrumentationRegistry.getArguments().getString("appLocale")?.takeIf { it.isNotBlank() }

    /** File names gain the language: `store-de-1-gallery.png`; English runs keep their names. */
    val prefix: String get() = requested?.let { "$it-" }.orEmpty()

    /** Sets Gemmory's own language ([tag] null resets it to the phone's). */
    fun set(context: Context, tag: String?) {
        val locales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }

    /** A string as the app shows it now, for finding a node by its words in any language. */
    fun text(context: Context, @StringRes id: Int): String = context.applicationContext.strings.text(id)
}
