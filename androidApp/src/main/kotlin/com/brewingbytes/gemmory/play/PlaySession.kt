package com.brewingbytes.gemmory.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.GameEvent
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.game.Rejection
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

/**
 * One open canvas: game state, selection, zoom/pan and its save loop. Owned by the play ViewModel, so it survives
 * rotation and fold changes; process death is covered by the save and view files. Main thread only.
 */
class PlaySession internal constructor(
    val ref: CanvasRef,
    val title: String,
    val pattern: Pattern,
    private val state: CanvasState,
    view: ViewState?,
    private val saver: SaveLoop,
    private val clock: PlayClock,
    private val onStopped: (playMillis: Long) -> Unit,
    private val onClosed: (lastWrite: Job, playMillis: Long) -> Unit,
) {
    val viewport = CanvasViewport(pattern.width, pattern.height).apply { restore(view) }

    var selectedSlot by mutableIntStateOf(state.initialSlot(view?.slot))
        private set

    /** Bumps on every placement; the canvas, tray and progress read it to redraw. */
    var placedCount by mutableIntStateOf(state.placedCount)
        private set

    /** Bumps on every wrong-color tap; the selected trough nudges once per bump. */
    var wrongTaps by mutableIntStateOf(0)
        private set

    val cellCount: Int get() = state.cellCount
    val isFinished: Boolean get() = state.isFinished
    val diskFull: StateFlow<Boolean> get() = saver.diskFull

    fun isPlaced(cell: Int): Boolean = state.isPlaced(cell)

    fun remaining(slot: Int): Int = state.remaining(slot)

    @androidx.annotation.VisibleForTesting
    internal fun placedLogForTest(): IntArray = state.log()

    fun trayOrder(): IntArray = state.trayOrder()

    /** Finished colors can't be selected. */
    fun select(slot: Int) {
        if (slot in pattern.palette.indices && !state.isFinished(slot)) selectedSlot = slot
    }

    /** A tap at placement zoom. The result drives haptics and sound; a rejection changes nothing but [wrongTaps]. */
    fun place(cell: Int): PlaceResult {
        val result = state.place(cell, selectedSlot)
        when (result) {
            is PlaceResult.Rejected -> if (result.reason == Rejection.WRONG_COLOR) wrongTaps++
            is PlaceResult.Placed -> {
                saver.onPlacement(cell)
                placedCount = state.placedCount
                for (event in result.events) {
                    when (event) {
                        is GameEvent.ColorFinished -> event.next?.let { selectedSlot = it }
                        GameEvent.PictureFinished -> saver.writeNow()
                        is GameEvent.GemPlaced -> Unit
                    }
                }
            }
        }
        return result
    }

    /** Activity onStart: play time counts again. */
    fun onStart() = clock.resume()

    /** Activity onStop: progress, view and play time are written right away. */
    fun onStop(): Deferred<Unit> {
        onStopped(clock.pause())
        return saver.writeNow(viewState())
    }

    /** Leaving the canvas: last write of progress and view, then the play time; the save loop stops. */
    fun close() = onClosed(saver.close(viewState()), clock.pause())

    private fun viewState(): ViewState? = if (viewport.isLaidOut) viewport.toViewState(selectedSlot) else null
}
