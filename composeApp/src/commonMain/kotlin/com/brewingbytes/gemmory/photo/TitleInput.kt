package com.brewingbytes.gemmory.photo

/** What a typed canvas title becomes (#12): shared, so a rename reads the same on Android and iOS (T64). */
object TitleInput {
    /** A rename that's blank keeps the old title; long ones are cut to the record's limit. */
    fun cleaned(input: String): String? =
        capped(input.trim().replace(Regex("\\s+"), " ")).takeIf { it.isNotEmpty() }

    /** At most [MAX_CHARS] UTF-16 units, never cutting a surrogate pair (an emoji) in half. */
    fun capped(input: String): String {
        val cut = input.take(MAX_CHARS)
        return if (cut.length < input.length && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    const val MAX_CHARS = 60
}
