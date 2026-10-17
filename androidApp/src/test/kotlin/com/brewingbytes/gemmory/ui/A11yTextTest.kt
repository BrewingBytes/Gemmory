package com.brewingbytes.gemmory.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A11yTextTest {
    private val us = NumberFormat.getIntegerInstance(Locale.US)

    @Test
    fun canvasNodeReadsProgressAndTheSelectedColor() {
        assertEquals("Canvas. 4,812 of 8,112 placed. Color 7 selected, 312 left.", A11yText.canvas(4812, 8112, 7, 312, us))
        assertEquals("Canvas. 0 of 768 placed. Color 1 selected, 40 left.", A11yText.canvas(0, 768, 1, 40, us))
        assertEquals("Canvas. 768 of 768 placed.", A11yText.canvas(768, 768, 3, 0, us), "a finished picture has no selection")
    }

    @Test
    fun announcements() {
        assertEquals("Not color 7", A11yText.notColor(7))
        assertEquals("Color 7 done. Color 8 selected.", A11yText.colorDone(7, 8))
        assertEquals("Color 7 done.", A11yText.colorDone(7, null))
        assertEquals("1,311 left", A11yText.left(1311, us))
        assertEquals("A quarter done", A11yText.milestone(25))
        assertEquals("Halfway there", A11yText.milestone(50))
        assertEquals("Three quarters done", A11yText.milestone(75))
        assertEquals("Showing the finished picture", A11yText.peek(true))
        assertEquals("Back to canvas", A11yText.peek(false))
        assertEquals("1,214 placed. 98 left", A11yText.batch(1214, 98, us))
        assertEquals("Tool: Fill region", A11yText.tool("Fill region"))
        assertEquals("Tool: Brush 3×3, 1 fill", A11yText.tool("Brush 3×3", 1))
        assertEquals("Tool: Tap, 0 fills", A11yText.tool("Tap", 0))
        assertEquals("Fill the rest of color 7", A11yText.fillColor(7))
    }

    @Test
    fun troughs() {
        assertEquals("Color 7, 312 left", A11yText.trough(7, 312, us))
        assertEquals("Color 7, 1,312 left", A11yText.trough(7, 1312, us))
        assertEquals("Color 7, done", A11yText.trough(7, 0, us))
    }

    @Test
    fun tiles() {
        assertEquals("Sunflowers, 42% done", A11yText.tile("Sunflowers", 42, done = false, locked = false, damaged = false))
        assertEquals("Sunflowers, done", A11yText.tile("Sunflowers", 100, done = true, locked = false, damaged = false))
        assertEquals(
            "Sunflowers, locked, watch a video to unlock",
            A11yText.tile("Sunflowers", 0, done = false, locked = true, damaged = false),
        )
        assertEquals("My photo · 13 Oct, can't open", A11yText.tile("My photo · 13 Oct", 10, done = false, locked = false, damaged = true))
        // #43: a remote picture that just arrived.
        assertEquals("Fox in Snow, new, 0% done", A11yText.tile("Fox in Snow", 0, done = false, locked = false, damaged = false, new = true))
        assertEquals("Fox in Snow, new, locked, watch a video to unlock", A11yText.tile("Fox in Snow", 0, done = false, locked = true, damaged = false, new = true))
    }

    @Test
    fun galleryColumnsFollowTheWidthClasses() {
        assertEquals(2, galleryColumns(360f))
        assertEquals(2, galleryColumns(599.9f))
        assertEquals(3, galleryColumns(600f))
        assertEquals(3, galleryColumns(839f))
        assertEquals(4, galleryColumns(840f))
        assertEquals(4, galleryColumns(1280f))
    }

    @Test
    fun theAllowanceChipStaysBesideTheButtonUntilItWouldBeTooNarrow() {
        // 411dp phone at 100%: 379dp row, ~170dp button.
        assertTrue(noteFitsBeside(379f, 170f, 12f))
        // 360dp phone at 200%: 328dp row, ~250dp button.
        assertFalse(noteFitsBeside(328f, 250f, 12f))
        assertTrue(noteFitsBeside(172f + 160f, 160f, 12f), "exactly the minimum fits")
        assertFalse(noteFitsBeside(171f + 160f, 160f, 12f))
    }
}
