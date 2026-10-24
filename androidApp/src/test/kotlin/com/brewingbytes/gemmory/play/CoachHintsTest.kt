package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.ads.FakePrefs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachHintsTest {
    private val prefs = FakePrefs()
    private val hints = CoachHints(prefs)

    @Test
    fun theExactCopy() {
        assertEquals("Pinch or tap to zoom in until numbers appear", XmlStrings.en.text(CoachHint.ZOOM_IN.text))
        assertEquals("Pick a color below, then tap or drag its numbers", XmlStrings.en.text(CoachHint.PICK_COLOR.text))
        assertEquals("Zoomed in so you can see the numbers", XmlStrings.en.text(CoachHint.TAP_ZOOMED.text))
    }

    @Test
    fun zoomInShowsOnAFarOutOpenAndFadesWhenNumbersAppear() {
        hints.onOpened(4f)
        assertEquals(CoachHint.ZOOM_IN, hints.current)
        assertTrue(hints.wasShown(CoachHint.ZOOM_IN))
        hints.onZoom(12f)
        assertEquals(CoachHint.ZOOM_IN, hints.current)
        hints.onZoom(16f)
        assertEquals(CoachHint.PICK_COLOR, hints.current, "reaching 16dp the first time brings the next hint")
        hints.onPlaced()
        assertNull(hints.current)
    }

    @Test
    fun tapToZoomExplainsItselfBeforePickAColor() {
        hints.onOpened(4f)
        hints.onTapZoom()
        assertEquals(CoachHint.TAP_ZOOMED, hints.current)
        hints.onZoom(24f)
        assertEquals(CoachHint.TAP_ZOOMED, hints.current)
        hints.expire(CoachHint.TAP_ZOOMED)
        assertEquals(CoachHint.PICK_COLOR, hints.current)
        hints.expire(CoachHint.PICK_COLOR)
        assertNull(hints.current)
    }

    @Test
    fun eachShowsOnceEver() {
        hints.onOpened(4f)
        hints.expire(CoachHint.ZOOM_IN)
        hints.reset()
        val again = CoachHints(prefs)
        again.onOpened(4f)
        assertNull(again.current)
        again.onZoom(20f)
        assertEquals(CoachHint.PICK_COLOR, again.current)
        again.onPlaced()
        again.onTapZoom()
        assertEquals(CoachHint.TAP_ZOOMED, again.current)
        again.expire(CoachHint.TAP_ZOOMED)
        val third = CoachHints(prefs)
        third.onOpened(4f)
        third.onTapZoom()
        third.onZoom(30f)
        assertNull(third.current)
    }

    @Test
    fun aCanvasThatOpensWithNumbersSkipsZoomInWithoutUsingItUp() {
        hints.onOpened(17f)
        assertEquals(CoachHint.PICK_COLOR, hints.current)
        assertFalse(hints.wasShown(CoachHint.ZOOM_IN), "it can still show on a canvas that opens far out")
    }

    @Test
    fun anActionDoneWhileWaitingDropsTheHintUnshown() {
        hints.onOpened(4f)
        hints.onTapZoom()
        hints.onZoom(24f)
        hints.onPlaced() // places while "Zoomed in…" shows: "Pick a color" is no longer needed now
        assertNull(hints.current)
        assertFalse(hints.wasShown(CoachHint.PICK_COLOR))
    }

    @Test
    fun anExpiryForAnotherHintChangesNothing() {
        hints.onOpened(4f)
        hints.expire(CoachHint.PICK_COLOR)
        assertEquals(CoachHint.ZOOM_IN, hints.current)
    }
}
