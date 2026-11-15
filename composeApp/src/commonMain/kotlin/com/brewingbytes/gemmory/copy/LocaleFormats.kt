package com.brewingbytes.gemmory.copy

/**
 * Numbers and dates in the language the app shows ([tag]), not the phone's (eng O-E4, D23). Android: `java.text` and
 * `java.time`, as `Strings.number` and `Messages.weekday` did; iOS: `NSNumberFormatter` and `NSDateFormatter`.
 */
class LocaleFormats(val tag: String) {
    /** Whole numbers grouped: "4,812", "4.812". */
    fun integer(value: Int): String = formatInteger(tag, value)

    /** An epoch day's weekday in full: "Tuesday", "火曜日". */
    fun weekday(epochDay: Long): String = weekdayName(tag, epochDay)
}

/** [value] grouped as language [tag] writes whole numbers. */
internal expect fun formatInteger(tag: String, value: Int): String

/** The weekday of [epochDay] (days since 1970-01-01) in full, in language [tag]. */
internal expect fun weekdayName(tag: String, epochDay: Long): String
