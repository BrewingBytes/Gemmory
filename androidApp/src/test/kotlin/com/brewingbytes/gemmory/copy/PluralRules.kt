package com.brewingbytes.gemmory.copy

/**
 * CLDR's cardinal plural rules for whole numbers in the app's languages (T49), so JVM tests pick the `<plurals>` item
 * Android would (no Robolectric here). `many` for French, Spanish, Italian and Portuguese is CLDR 42's millions rule.
 */
object PluralRules {
    /** The quantities each language's `<plurals>` must give (whole numbers only, so Polish's `other` is fractions). */
    val QUANTITIES: Map<String, Set<String>> = mapOf(
        "en" to setOf("one", "other"),
        "de" to setOf("one", "other"),
        "es" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "it" to setOf("one", "many", "other"),
        "ro" to setOf("one", "few", "other"),
        "pl" to setOf("one", "few", "many", "other"),
        "tr" to setOf("one", "other"),
        "hu" to setOf("one", "other"),
        "bg" to setOf("one", "other"),
        "ja" to setOf("other"),
    )

    /** The quantity [language] uses for [n]. */
    fun quantity(language: String, n: Int): String {
        val millions = n != 0 && n % 1_000_000 == 0
        return when (language) {
            "ja" -> "other"
            "fr" -> when { n == 0 || n == 1 -> "one"; millions -> "many"; else -> "other" }
            "pt" -> when { n == 0 || n == 1 -> "one"; millions -> "many"; else -> "other" }
            "es", "it" -> when { n == 1 -> "one"; millions -> "many"; else -> "other" }
            "ro" -> when { n == 1 -> "one"; n == 0 || n % 100 in 2..19 || n % 100 == 1 -> "few"; else -> "other" }
            "pl" -> when {
                n == 1 -> "one"
                n % 10 in 2..4 && n % 100 !in 12..14 -> "few"
                else -> "many"
            }
            else -> if (n == 1) "one" else "other"
        }
    }
}
