package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LightsDownTrackerTest {
    private val starter = CanvasRef(CanvasKind.BUNDLED, "starter")
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")

    @Test
    fun aRotationDoesNotReplayButAnotherFinishDoes() {
        val tracker = LightsDownTracker()
        tracker.markPlayed(starter)
        assertTrue(tracker.hasPlayed(starter), "rotation mid-sequence: no replay")
        // Start over from the gallery menu, then finished again: MainActivity forgets it on finish.
        tracker.forget(starter)
        assertFalse(tracker.hasPlayed(starter))
    }

    @Test
    fun forgettingOneCanvasKeepsTheOthers() {
        val tracker = LightsDownTracker()
        tracker.markPlayed(starter)
        tracker.markPlayed(photo)
        tracker.forget(photo)
        assertTrue(tracker.hasPlayed(starter))
        assertFalse(tracker.hasPlayed(photo))
    }
}
