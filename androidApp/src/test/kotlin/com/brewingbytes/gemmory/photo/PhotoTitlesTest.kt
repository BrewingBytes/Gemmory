package com.brewingbytes.gemmory.photo

import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoTitlesTest {
    @Test
    fun defaultTitleIsMyPhotoWithTheDay() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            // 2026-10-13T12:00:00Z
            assertEquals("My photo · 13 Oct", PhotoTitles.default(1_791_892_800_000, Locale.UK))
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun renameInputIsTrimmedCollapsedAndCapped() {
        assertEquals("Beach day", PhotoTitles.cleaned("  Beach \n day "))
        assertNull(PhotoTitles.cleaned("   "))
        assertEquals(PhotoTitles.MAX_CHARS, PhotoTitles.cleaned("x".repeat(200))!!.length)
    }

    @Test
    fun capNeverSplitsAnEmoji() {
        val title = "x".repeat(59) + "😀"
        assertEquals("x".repeat(59), PhotoTitles.cleaned(title))
        assertEquals("x".repeat(59), PhotoTitles.capped(title))
        assertEquals("x".repeat(58) + "😀", PhotoTitles.cleaned("x".repeat(58) + "😀"))
        val cleaned = PhotoTitles.cleaned(title)!!
        assertEquals(cleaned, cleaned.encodeToByteArray().decodeToString(), "round-trips through the record's UTF-8")
    }
}
