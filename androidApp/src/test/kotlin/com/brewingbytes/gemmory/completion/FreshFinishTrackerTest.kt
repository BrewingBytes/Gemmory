package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.nav.BackStack
import com.brewingbytes.gemmory.nav.Route
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreshFinishTrackerTest {
    private val starter = CanvasRef(CanvasKind.BUNDLED, "starter")
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")

    /** The back stack's saved form, as a rotation (or process death) restores it. */
    private fun restored(stack: BackStack): Route = BackStack(stack.routes.map(Route::encode).mapNotNull(Route::decode)).top

    @Test
    fun aRotationKeepsAJustFinishedCompletionFresh() {
        val stack = BackStack(listOf(Route.Gallery, Route.Canvas(starter)))
        val tracker = FreshFinishTracker()
        tracker.finished(starter)
        stack.finished(starter)
        val afterRotation = restored(stack) as Route.Completion
        assertEquals(Route.Completion(starter, fresh = false), afterRotation, "the stack alone restores a still")
        assertTrue(tracker.isFresh(afterRotation), "same process: still the lights-down completion")
        assertTrue(tracker.left(afterRotation), "Done after the rotation checks the interstitial")
    }

    @Test
    fun processDeathRestoresAStill() {
        val stack = BackStack(listOf(Route.Gallery, Route.Canvas(starter)))
        stack.finished(starter)
        val afterDeath = restored(stack) as Route.Completion
        val newProcess = FreshFinishTracker()
        assertFalse(newProcess.isFresh(afterDeath))
        assertFalse(newProcess.left(afterDeath), "no interstitial check for a still")
    }

    @Test
    fun leavingMakesItAStillAndOnlyTheLastFinishCounts() {
        val tracker = FreshFinishTracker()
        tracker.finished(starter)
        assertTrue(tracker.left(Route.Completion(starter, fresh = true)))
        assertFalse(tracker.left(Route.Completion(starter, fresh = false)), "reopened from the gallery later: a still")
        tracker.finished(starter)
        tracker.finished(photo)
        assertFalse(tracker.isFresh(Route.Completion(starter, fresh = false)))
        assertTrue(tracker.isFresh(Route.Completion(photo, fresh = false)))
    }
}
