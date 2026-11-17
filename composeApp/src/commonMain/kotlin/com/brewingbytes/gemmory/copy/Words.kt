package com.brewingbytes.gemmory.copy

import androidx.compose.runtime.staticCompositionLocalOf
import com.brewingbytes.gemmory.compose.res.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The app's copy in one language for shared code (iOS Stage 1c, T64; eng D23 / OV-6 option B): every word by its name
 * in `composeResources/files/strings/values[-xx]/strings.xml`, read by [StringsXml] on both platforms, so a shared
 * screen says the same thing on Android and iOS. Synchronous: it is loaded once per language ([load], [preload]) and
 * then only looked up. Numbers and weekdays come from [LocaleFormats] in the same language, never the phone's.
 *
 * Android code outside shared UI keeps reading `R.string` through `Strings`, from the same files.
 */
class Words(private val xml: StringsXml, private val formats: LocaleFormats) {
    /** The language tag shown ("en", "pt-BR"). */
    val tag: String get() = xml.tag

    /** The string [name] with [args] put in. */
    fun text(name: String, vararg args: Any): String = xml.text(name, *args)

    /** The plural [name] for [count] by this language's rules, with [args] put in. */
    fun plural(name: String, count: Int, vararg args: Any): String = xml.plural(name, count, *args)

    /** Whole numbers grouped for this language: "4,812". */
    fun number(value: Int): String = formats.integer(value)

    /** An epoch day's weekday in full: "Tuesday". */
    fun weekday(epochDay: Long): String = formats.weekday(epochDay)

    /** [text] with its first letter lowered by this language's rules, for words that start a sentence on screen. */
    fun lowercaseFirst(text: String): String = formats.lowercaseFirst(text)

    /** [text] with its first letter raised by this language's rules, for a phrase that starts a sentence. */
    fun titlecaseFirst(text: String): String = formats.titlecaseFirst(text)

    companion object {
        /** Where the language folders live under `composeResources/files/`. */
        const val FOLDER = "strings"

        /** The file of [tag] under `composeResources/files/`. */
        fun path(tag: String): String = "$FOLDER/${StringsXml.folderOf(tag)}/strings.xml"

        /**
         * [tag]'s words over English, as Android falls back to `values/`; [read] returns a file under
         * `composeResources/files/` by its [path]. Android reads them synchronously from its assets.
         */
        fun load(tag: String, read: (path: String) -> String): Words {
            val base = StringsXml.parse("en", read(path("en")))
            val shown = if (tag == "en") base else StringsXml.parse(tag, read(path(tag))).over(base)
            return Words(shown, LocaleFormats(tag))
        }

        /**
         * [load] through Compose resources, for a shell that can't read them synchronously (iOS): at startup and on a
         * language change, before the first frame that shows words (eng D23).
         */
        @OptIn(ExperimentalResourceApi::class)
        suspend fun preload(tag: String): Words {
            val files = mutableMapOf<String, String>()
            for (t in setOf("en", tag)) files[path(t)] = Res.readBytes("files/${path(t)}").decodeToString()
            return load(tag) { files.getValue(it) }
        }
    }
}

/**
 * The [Words] of the language the app shows, provided by `GemmoryTheme`: shared screens read their copy here. A
 * composable outside the theme fails at once instead of showing another language.
 */
val LocalWords = staticCompositionLocalOf<Words> { error("No Words: GemmoryTheme provides LocalWords") }
