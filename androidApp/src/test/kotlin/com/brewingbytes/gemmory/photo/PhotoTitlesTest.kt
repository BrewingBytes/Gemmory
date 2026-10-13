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
}
