package com.brewingbytes.gemmory.copy

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.lowercaseStringWithLocale
import platform.Foundation.uppercaseStringWithLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

internal actual fun formatInteger(tag: String, value: Int): String {
    val numbers = NSNumberFormatter()
    numbers.numberStyle = NSNumberFormatterDecimalStyle
    numbers.maximumFractionDigits = 0u
    numbers.locale = NSLocale(localeIdentifier = tag)
    return numbers.stringFromNumber(NSNumber(int = value)) ?: value.toString()
}

internal actual fun weekdayName(tag: String, epochDay: Long): String {
    // An epoch day is a date, not an instant: read at noon UTC so no zone moves it to another day.
    val weekdays = NSDateFormatter()
    weekdays.locale = NSLocale(localeIdentifier = tag)
    weekdays.timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    weekdays.dateFormat = "EEEE"
    return weekdays.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochDay * 86_400.0 + 43_200.0))
}

@Suppress("CAST_NEVER_SUCCEEDS") // a Kotlin String is an NSString on Apple targets
internal actual fun lowercaseIn(tag: String, text: String): String =
    (text as NSString).lowercaseStringWithLocale(NSLocale(localeIdentifier = tag))

// One letter: its title case is its upper case in every language the app shows.
@Suppress("CAST_NEVER_SUCCEEDS") // a Kotlin String is an NSString on Apple targets
internal actual fun titlecaseIn(tag: String, text: String): String =
    (text as NSString).uppercaseStringWithLocale(NSLocale(localeIdentifier = tag))
