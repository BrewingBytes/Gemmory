package com.brewingbytes.gemmory.copy

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

internal actual fun formatInteger(tag: String, value: Int): String =
    NumberFormat.getIntegerInstance(Locale.forLanguageTag(tag)).format(value)

internal actual fun weekdayName(tag: String, epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag(tag))

internal actual fun lowercaseIn(tag: String, text: String): String = text.lowercase(Locale.forLanguageTag(tag))

internal actual fun titlecaseIn(tag: String, text: String): String = text.replaceFirstChar { it.titlecase(Locale.forLanguageTag(tag)) }

internal actual fun dayMonthIn(tag: String, epochMillis: Long): String =
    SimpleDateFormat("d MMM", Locale.forLanguageTag(tag)).format(Date(epochMillis))
