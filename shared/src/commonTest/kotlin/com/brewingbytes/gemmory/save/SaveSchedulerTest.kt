package com.brewingbytes.gemmory.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveSchedulerTest {
    private var now = 0L
    private val scheduler = SaveScheduler(nowMillis = { now })

    private fun at(t: Long) {
        now = t
    }

    @Test
    fun nothingIsDueWhenClean() {
        assertNull(scheduler.dueAt())
        assertNull(scheduler.takeDueWrite())
        assertNull(scheduler.takeImmediateWrite())
    }

    @Test
    fun writesOneSecondAfterTheLastPlacement() {
        at(100)
        scheduler.onPlacement()
        at(600)
        scheduler.onPlacement()
        assertEquals(1_600, scheduler.dueAt())
        at(1_599)
        assertNull(scheduler.takeDueWrite())
        at(1_600)
        val ticket = assertNotNull(scheduler.takeDueWrite())
        scheduler.onWriteFinished(ticket, success = true)
        assertNull(scheduler.dueAt())
        assertFalse(scheduler.hasUnsavedPlacements)
    }

    @Test
    fun continuousPlacementsWriteAtFiveSeconds() {
        val writes = mutableListOf<Long>()
        var t = 0L
        while (t <= 12_000) {
            at(t)
            scheduler.onPlacement()
            scheduler.takeDueWrite()?.let {
                writes += t
                scheduler.onWriteFinished(it, success = true)
            }
            t += 200
        }
        // The placement at 5000 is in the first write, so the next unsaved run starts at 5200.
        assertEquals(listOf(5_000L, 10_200L), writes)
    }

    @Test
    fun onStopAndCompletionWriteImmediately() {
        at(0)
        scheduler.onPlacement()
        at(10)
        assertNull(scheduler.takeDueWrite())
        val ticket = assertNotNull(scheduler.takeImmediateWrite())
        scheduler.onWriteFinished(ticket, success = true)
        assertNull(scheduler.takeImmediateWrite())
    }

    @Test
    fun placementsDuringAWriteStayUnsaved() {
        at(0)
        scheduler.onPlacement()
        at(1_000)
        val ticket = assertNotNull(scheduler.takeDueWrite())
        at(1_050)
        scheduler.onPlacement()
        scheduler.onWriteFinished(ticket, success = true)
        assertTrue(scheduler.hasUnsavedPlacements)
        assertEquals(2_050, scheduler.dueAt())
    }

    @Test
    fun aFailedWriteRetriesAfterTheMaxWaitButOnStopStillWrites() {
        at(0)
        scheduler.onPlacement()
        at(1_000)
        scheduler.onWriteFinished(assertNotNull(scheduler.takeDueWrite()), success = false)
        assertTrue(scheduler.hasUnsavedPlacements)
        assertEquals(6_000, scheduler.dueAt())
        at(1_500)
        scheduler.onPlacement()
        assertEquals(6_000, scheduler.dueAt())
        at(5_999)
        assertNull(scheduler.takeDueWrite())
        val immediate = assertNotNull(scheduler.takeImmediateWrite())
        scheduler.onWriteFinished(immediate, success = true)
        assertNull(scheduler.dueAt())
    }

    @Test
    fun theBackoffClearsAfterASuccessfulWrite() {
        at(0)
        scheduler.onPlacement()
        at(1_000)
        scheduler.onWriteFinished(assertNotNull(scheduler.takeDueWrite()), success = false)
        at(6_000)
        scheduler.onWriteFinished(assertNotNull(scheduler.takeDueWrite()), success = true)
        at(7_000)
        scheduler.onPlacement()
        assertEquals(8_000, scheduler.dueAt())
    }
}
