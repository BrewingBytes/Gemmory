package com.brewingbytes.gemmory.remote

/**
 * Translated picture, collection and event titles (T49 X4, #136). The catalog may carry a `titles` map beside the
 * English `title` (language tag → title); a shown title is picked by the language the app shows, through its chain
 * (`pt-BR` → `pt`), then English. An app before T49 never reads the map, so it never raises `minAppVersion`.
 */
object Titles {
    /** The app's languages besides English, as the catalog and the batch CSV write them (`title_pt-BR`). */
    val LANGUAGES = listOf("de", "es", "pt-BR", "fr", "it", "ro", "pl", "tr", "hu", "bg", "ja")

    /** A language tag a `titles` key may be: a language, optionally with a region. */
    val TAG = Regex("[a-z]{2,3}(-[A-Z]{2})?")

    /** The English title's rule, for every language too (eng O-E10): not blank, at most 60 chars, no control characters. */
    fun valid(title: String): Boolean = title.isNotBlank() && title.length <= RemoteCatalogs.MAX_TITLE && title.none { it < ' ' }

    /** The tags tried for [languageTag], most specific first: `pt-BR` → [`pt-BR`, `pt`]; `de` → [`de`]. */
    fun chain(languageTag: String): List<String> {
        val language = languageTag.substringBefore('-')
        return if (language == languageTag) listOf(languageTag) else listOf(languageTag, language)
    }

    /** The title shown in [languageTag]: the first of its [chain] in [titles], else [english]. */
    fun pick(titles: Map<String, String>, english: String, languageTag: String): String =
        chain(languageTag).firstNotNullOfOrNull { titles[it] } ?: english
}

/**
 * Romanian is written with ș and ț, the comma-below letters (T49, design system Typography → Other scripts); the
 * cedilla forms ş and ţ are an old encoding's stand-ins and our fonts don't draw ţ. One normalizer for the app's
 * strings check and the batch tool's titles.
 */
object Romanian {
    fun normalize(text: String): String =
        text.replace('ş', 'ș').replace('ţ', 'ț').replace('Ş', 'Ș').replace('Ţ', 'Ț')

    /** Whether [text] still holds a cedilla form. */
    fun hasCedilla(text: String): Boolean = text.any { it == 'ş' || it == 'ţ' || it == 'Ş' || it == 'Ţ' }
}

/** This picture's title in [languageTag] (T49 X4), English when the catalog has none for it. */
fun RemotePicture.titleIn(languageTag: String): String = Titles.pick(titles, title, languageTag)

/** This collection's or event's title in [languageTag] (T49 X4), English when the catalog has none for it. */
fun RemoteCollection.titleIn(languageTag: String): String = Titles.pick(titles, title, languageTag)
