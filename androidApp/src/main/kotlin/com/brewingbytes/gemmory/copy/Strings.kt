package com.brewingbytes.gemmory.copy

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.brewingbytes.gemmory.R
import java.text.NumberFormat
import java.util.Locale

/**
 * The app's copy (T48, #135): every user-facing word lives in `res/values/strings.xml`, read through this. Numbers,
 * percents and dates are formatted in [locale], the language the app shows (`app_locale`, English for everyone until
 * T49), never the phone's raw locale (eng O-E4). JVM tests read the same XML through `XmlStrings`.
 */
interface Strings {
    val locale: Locale

    fun text(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String

    /** Whole numbers grouped for [locale]: "4,812". */
    fun number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)
}

/**
 * [Strings] over Android resources. Formats with `String.format` in the app language's locale (not `getString(id, …)`,
 * which uses the phone's), and picks plurals by that language's rules: English shown on a phone set to a language
 * the app doesn't have yet still says "21 fills", not the phone language's "one" form.
 */
class AndroidStrings(context: Context) : Strings {
    override val locale: Locale = Locale.forLanguageTag(context.resources.getString(R.string.app_locale))

    private val resources: Resources = context.resources.let { base ->
        val shown = locale
        if (base.configuration.locales[0].language == shown.language) {
            base
        } else {
            context.createConfigurationContext(Configuration(base.configuration).apply { setLocale(shown) }).resources
        }
    }

    override fun text(id: Int, vararg args: Any): String {
        val raw = resources.getString(id)
        return if (args.isEmpty()) raw else String.format(locale, raw, *args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        String.format(locale, resources.getQuantityText(id, count).toString(), *args)
}

/** The copy for this context's resources as they are now. */
val Context.strings: Strings get() = AndroidStrings(this)

/** For an object that outlives a language change (the canvas store): reads the context's current resources on each call. */
class LiveStrings(private val context: Context) : Strings {
    override val locale: Locale get() = context.strings.locale

    override fun text(id: Int, vararg args: Any): String = context.strings.text(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String = context.strings.plural(id, count, *args)
}

/** The copy for the composition's current resources (a language switch recreates the activity). */
@Composable
fun strings(): Strings {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AndroidStrings(context) }
}
