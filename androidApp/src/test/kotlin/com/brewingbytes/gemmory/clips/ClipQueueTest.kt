package com.brewingbytes.gemmory.clips

import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.timelapse.ClipOrder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Clips screen's queue (#152): one entry at a time, in order, none left waiting, a failure doesn't stop it. */
class ClipQueueTest {
    private fun picture(id: String) = ClipPicture(CanvasRef(CanvasKind.BUNDLED, id), id, null) { null }

    private val formats = setOf(ClipFormat.NINE_SIXTEEN)

    @Test
    fun entriesRenderOneAtATimeInOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rendered = ArrayList<String>()
        var running = 0
        var most = 0
        val queue = ClipQueue(TestScope(dispatcher), dispatcher) { job, onProgress, _ ->
            running++
            most = maxOf(most, running)
            onProgress(50, job.languages.last())
            rendered += job.picture.ref.id
            running--
            ClipResult(job.languages.map { File("${job.picture.ref.id}-$it.mp4") }, null)
        }
        queue.add(picture("wave"), ClipOrder.BY_COLOR, formats, listOf("en", "de"))
        queue.add(picture("starter"), ClipOrder.RIPPLE, formats, listOf("ja"))
        advanceUntilIdle()
        assertEquals(listOf("wave", "starter"), rendered)
        assertEquals(1, most, "one render at a time")
        assertEquals(listOf(ClipStatus.Done(2, null), ClipStatus.Done(1, null)), queue.jobs.value.map { it.status })
        assertEquals("starter: saved 1 file", queue.announcement.value)
    }

    @Test
    fun anEntryAddedWhileOneRendersIsPickedUp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rendered = ArrayList<String>()
        lateinit var queue: ClipQueue
        queue = ClipQueue(TestScope(dispatcher), dispatcher) { job, _, _ ->
            if (job.key == 0) queue.add(picture("starter"), ClipOrder.BY_COLOR, formats, listOf("en"))
            rendered += job.picture.ref.id
            ClipResult(emptyList(), null)
        }
        queue.add(picture("wave"), ClipOrder.BY_COLOR, formats, listOf("en"))
        advanceUntilIdle()
        assertEquals(listOf("wave", "starter"), rendered)
    }

    @Test
    fun anEntryAddedAfterTheQueueWentIdleStillRenders() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val rendered = ArrayList<String>()
        val queue = ClipQueue(TestScope(dispatcher), dispatcher) { job, _, _ ->
            rendered += job.picture.ref.id
            ClipResult(emptyList(), null)
        }
        queue.add(picture("wave"), ClipOrder.BY_COLOR, formats, listOf("en"))
        advanceUntilIdle()
        queue.add(picture("starter"), ClipOrder.BY_COLOR, formats, listOf("en"))
        advanceUntilIdle()
        assertEquals(listOf("wave", "starter"), rendered)
        assertEquals(ClipStatus.Done(0, null), queue.jobs.value.last().status)
    }

    @Test
    fun aFailedEntryDoesntStopTheNext() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val queue = ClipQueue(TestScope(dispatcher), dispatcher) { job, _, _ ->
            when (job.key) {
                0 -> ClipResult(emptyList(), "Couldn't make wave-by-color-en.mp4")
                1 -> error("encoder gone")
                else -> ClipResult(listOf(File("x")), null)
            }
        }
        for (id in listOf("wave", "starter", "balloon")) queue.add(picture(id), ClipOrder.BY_COLOR, formats, listOf("en"))
        advanceUntilIdle()
        assertEquals(
            listOf(
                ClipStatus.Done(0, "Couldn't make wave-by-color-en.mp4"),
                ClipStatus.Done(0, "Couldn't make it: IllegalStateException"),
                ClipStatus.Done(1, null),
            ),
            queue.jobs.value.map { it.status },
        )
    }

    @Test
    fun cancellingStopsTheQueue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        lateinit var queue: ClipQueue
        queue = ClipQueue(TestScope(dispatcher), dispatcher) { _, _, checkCancelled ->
            queue.cancel()
            checkCancelled()
            ClipResult(emptyList(), null)
        }
        queue.add(picture("wave"), ClipOrder.BY_COLOR, formats, listOf("en"))
        queue.add(picture("starter"), ClipOrder.BY_COLOR, formats, listOf("en"))
        advanceUntilIdle()
        assertEquals(ClipStatus.Waiting, queue.jobs.value.last().status, "nothing after the cancel")
    }
}
