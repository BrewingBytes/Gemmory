package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.save.SaveScheduler
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * The one writer of a canvas (eng D8): runs [SaveScheduler] in a single coroutine on [scope] and calls the
 * blocking [writeSave] / [writeView] from there, so writes never overlap and never run on the main thread.
 *
 * The placement log is append-only, so the loop keeps its own copy from [onPlacement] events instead of reading
 * the game state across threads. [diskFull] is true while the last save write failed with a full disk (design D9).
 */
class SaveLoop(
    scope: CoroutineScope,
    initialLog: IntArray,
    private val nowMillis: () -> Long,
    private val writeSave: (IntArray) -> WriteResult,
    private val writeView: (ViewState) -> WriteResult,
) {
    private sealed interface Event {
        class Placed(val cell: Int) : Event

        /** A tool's batch (#20): its cells in log order, one placement as far as the cadence goes. */
        class PlacedAll(val cells: IntArray) : Event

        class Flush(val view: ViewState?, val done: CompletableDeferred<Unit>) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val scheduler = SaveScheduler(nowMillis)
    private var log = initialLog.copyOf(maxOf(initialLog.size, 64))
    private var size = initialLog.size
    private val _diskFull = MutableStateFlow(false)
    val diskFull: StateFlow<Boolean> = _diskFull.asStateFlow()

    private val job: Job = scope.launch { run() }

    fun onPlacement(cell: Int) {
        events.trySend(Event.Placed(cell))
    }

    /** A tool's batch (#20), in log order: saved on the same 1 s / 5 s cadence as a single placement. */
    fun onPlacements(cells: IntArray) {
        events.trySend(Event.PlacedAll(cells.copyOf()))
    }

    /**
     * Writes unsaved progress right away, then [view] if given: on Activity onStop and on picture finished.
     * The result completes once both are written (or failed).
     */
    fun writeNow(view: ViewState? = null): Deferred<Unit> {
        val done = CompletableDeferred<Unit>()
        if (events.trySend(Event.Flush(view, done)).isFailure) done.complete(Unit)
        return done
    }

    /** Flushes like [writeNow] and stops. The returned job ends after the last write. */
    fun close(view: ViewState?): Job {
        writeNow(view)
        events.close()
        return job
    }

    // select, not withTimeout around receive: a receive cancelled by a timeout can drop the element it just took,
    // and a dropped placement would be missing from every later save.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run() {
        while (true) {
            val due = scheduler.dueAt()
            val received: ChannelResult<Event>? = if (due == null) {
                events.receiveCatching()
            } else {
                select {
                    events.onReceiveCatching { it }
                    onTimeout(maxOf(0L, due - nowMillis())) { null }
                }
            }
            if (received == null) {
                scheduler.takeDueWrite()?.let(::save)
                continue
            }
            when (val event = received.getOrNull() ?: return) {
                is Event.Placed -> {
                    if (size == log.size) log = log.copyOf(log.size * 2)
                    log[size++] = event.cell
                    scheduler.onPlacement()
                }
                is Event.PlacedAll -> {
                    if (size + event.cells.size > log.size) log = log.copyOf(maxOf(log.size * 2, size + event.cells.size))
                    event.cells.copyInto(log, size)
                    size += event.cells.size
                    scheduler.onPlacement()
                }
                is Event.Flush -> {
                    scheduler.takeImmediateWrite()?.let(::save)
                    event.view?.let { writeView(it) }
                    event.done.complete(Unit)
                }
            }
        }
    }

    private fun save(ticket: SaveScheduler.Ticket) {
        val result = writeSave(log.copyOf(size))
        scheduler.onWriteFinished(ticket, success = result == WriteResult.Written)
        when (result) {
            WriteResult.Written -> _diskFull.value = false
            is WriteResult.WriteFailed -> if (result.diskFull) _diskFull.value = true
        }
    }
}
