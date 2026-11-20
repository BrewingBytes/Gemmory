package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.storage.MemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** First-time hints (design D12), shared since iOS Stage 1c (T64): their order and "once ever" over a [MemoryStore]. */
class CoachHintsTest {
    private val prefs = MemoryStore(CoachHints.FILE)
    private val hints = CoachHints(prefs)

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
