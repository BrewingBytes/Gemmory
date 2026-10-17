package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The save loop on virtual time: the scheduler's clock and the loop's waits both run on the test scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveLoopTest {
    private class Write(val at: Long, val log: List<Int>)

    private class Harness(scope: TestScope, initialLog: IntArray = IntArray(0)) {
        val saves = mutableListOf<Write>()
        val views = mutableListOf<Pair<Long, ViewState>>()
        var nextSave: WriteResult = WriteResult.Written
        val loop = SaveLoop(
            scope = scope.backgroundScope,
            initialLog = initialLog,
            nowMillis = { scope.testScheduler.currentTime },
            writeSave = { log ->
                saves += Write(scope.testScheduler.currentTime, log.toList())
                nextSave
            },
            writeView = { view ->
                views += scope.testScheduler.currentTime to view
                WriteResult.Written
            },
        )
    }

    private val view = ViewState(zoom = 24f, panX = -120f, panY = 40f, slot = 3)

    @Test
    fun idleWriteOneSecondAfterTheLastPlacement() = runTest {
        val h = Harness(this)
        runCurrent()
        h.loop.onPlacement(7)
        advanceTimeBy(400)
        h.loop.onPlacement(2)
        advanceTimeBy(999)
        runCurrent()
        assertTrue(h.saves.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, h.saves.size)
        assertEquals(1_400L, h.saves[0].at)
        assertEquals(listOf(7, 2), h.saves[0].log)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, h.saves.size)
    }

    @Test
    fun continuousPlacementsWriteAtMostFiveSecondsApart() = runTest {
        val h = Harness(this)
        runCurrent()
        repeat(60) { i ->
            h.loop.onPlacement(i)
            advanceTimeBy(200)
            runCurrent()
        }
        assertEquals(listOf(5_000L, 10_000L), h.saves.map { it.at })
        // The write at 5000 covers placements made at 0..4800 (25 of them).
        assertEquals((0 until 25).toList(), h.saves[0].log)
    }

    @Test
    fun keepsTheLogFromTheRestoredSave() = runTest {
        val h = Harness(this, initialLog = intArrayOf(9, 4))
        runCurrent()
        h.loop.onPlacement(1)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(9, 4, 1), h.saves.single().log)
    }

    @Test
    fun onStopWritesProgressAndViewImmediately() = runTest {
        val h = Harness(this)
        runCurrent()
        h.loop.onPlacement(5)
        advanceTimeBy(300)
        val done = h.loop.writeNow(view)
        runCurrent()
        assertTrue(done.isCompleted)
        assertEquals(listOf(300L), h.saves.map { it.at })
        assertEquals(listOf(300L to view), h.views)
        // Nothing left for the debounce.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, h.saves.size)
    }

    @Test
    fun onStopWithNothingUnsavedWritesOnlyTheView() = runTest {
        val h = Harness(this)
        runCurrent()
        h.loop.writeNow(view)
        runCurrent()
        assertTrue(h.saves.isEmpty())
        assertEquals(1, h.views.size)
    }

    @Test
    fun pictureFinishedWritesImmediately() = runTest {
        val h = Harness(this)
        runCurrent()
        h.loop.onPlacement(0)
        h.loop.onPlacement(1)
        h.loop.writeNow()
        runCurrent()
        assertEquals(listOf(0L), h.saves.map { it.at })
        assertEquals(listOf(0, 1), h.saves[0].log)
        assertTrue(h.views.isEmpty())
    }

    @Test
    fun diskFullIsShownUntilTheNextSuccessfulWrite() = runTest {
        val h = Harness(this)
        runCurrent()
        assertFalse(h.loop.diskFull.value)
        h.nextSave = WriteResult.WriteFailed(diskFull = true)
        h.loop.onPlacement(0)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(h.loop.diskFull.value)
        // A failed write backs off 5 s before retrying the same progress.
        h.nextSave = WriteResult.Written
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, h.saves.size)
        assertTrue(h.loop.diskFull.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1_000L, 6_000L), h.saves.map { it.at })
        assertEquals(listOf(0), h.saves[1].log)
        assertFalse(h.loop.diskFull.value)
    }

    @Test
    fun otherWriteFailuresDontShowTheDiskFullLine() = runTest {
        val h = Harness(this)
        runCurrent()
        h.nextSave = WriteResult.WriteFailed(diskFull = false)
        h.loop.onPlacement(0)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, h.saves.size)
        assertFalse(h.loop.diskFull.value)
    }

    @Test
    fun onStopRetriesAFullDiskWithoutWaitingForTheBackoff() = runTest {
        val h = Harness(this)
        runCurrent()
        h.nextSave = WriteResult.WriteFailed(diskFull = true)
        h.loop.onPlacement(0)
        advanceTimeBy(1_001)
        runCurrent()
        h.nextSave = WriteResult.Written
        h.loop.writeNow(view)
        runCurrent()
        assertEquals(2, h.saves.size)
        assertFalse(h.loop.diskFull.value)
    }

    @Test
    fun closeFlushesThenStops() = runTest {
        val h = Harness(this)
        runCurrent()
        h.loop.onPlacement(3)
        val job = h.loop.close(view)
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(listOf(3), h.saves.single().log)
        assertEquals(1, h.views.size)
        h.loop.onPlacement(4)
        assertTrue(h.loop.writeNow().isCompleted)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, h.saves.size)
    }

    @Test
    fun aBatchIsSavedLikeOnePlacementInItsOrder() = runTest {
        val h = Harness(this, initialLog = intArrayOf(9))
        runCurrent()
        // A region fill of 200 cells (#20): one event, written 1 s later in its sweep order, after the earlier tap.
        val batch = IntArray(200) { 1000 - it }
        h.loop.onPlacement(4)
        advanceTimeBy(300)
        h.loop.onPlacements(batch)
        advanceTimeBy(999)
        runCurrent()
        assertTrue(h.saves.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1_300L), h.saves.map { it.at })
        assertEquals(listOf(9, 4) + batch.toList(), h.saves.single().log)
    }

    @Test
    fun batchesKeepTheFiveSecondCapAndAFinishWritesAtOnce() = runTest {
        val h = Harness(this)
        runCurrent()
        // A brush drag: a 9-cell stamp every 200ms keeps the debounce from firing; the 5 s cap still writes.
        repeat(30) { i ->
            h.loop.onPlacements(IntArray(9) { i * 9 + it })
            advanceTimeBy(200)
            runCurrent()
        }
        assertEquals(listOf(5_000L), h.saves.map { it.at })
        assertEquals((0 until 25 * 9).toList(), h.saves[0].log)
        // The batch that finishes the picture is followed by writeNow, as for a tap: written at once.
        h.loop.onPlacements(intArrayOf(500, 501))
        h.loop.writeNow()
        runCurrent()
        assertEquals(6_000L, h.saves.last().at)
        assertEquals(listOf(500, 501), h.saves.last().log.takeLast(2))
        assertEquals(30 * 9 + 2, h.saves.last().log.size)
    }
}
