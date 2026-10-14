package com.brewingbytes.gemmory.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasGestures
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.GameEvent
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.game.Rejection
import com.brewingbytes.gemmory.game.nextRemainingCell
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.A11yText
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

    /** The latest TalkBack announcement (design D19), spoken by the canvas's one polite live region. */
    var announcement by mutableStateOf<Announcement?>(null)
        private set

    val cellCount: Int get() = state.cellCount
    val isFinished: Boolean get() = state.isFinished
    val diskFull: StateFlow<Boolean> get() = saver.diskFull

    fun isPlaced(cell: Int): Boolean = state.isPlaced(cell)

    fun remaining(slot: Int): Int = state.remaining(slot)

    /** The [index]th placement, oldest first; the renderer catches up on new gems with it. */
    fun placedAt(index: Int): Int = state.placedAt(index)

    /** Find (design D24) shows while the selected color has 1 to 20 cells left. */
    val findVisible: Boolean
        get() {
            placedCount // a snapshot read, so callers recompose after every placement
            return remaining(selectedSlot) in 1..FIND_MAX_LEFT
        }

    /** Where Find and "Place next gem" last were; a new color starts again at its first cell in reading order. */
    private var findCursor = -1

    /** The next remaining cell of the selected color after the last one found, wrapping; null when none is left. */
    fun findNext(): Int? = nextRemainingCell(pattern, state::isPlaced, selectedSlot, findCursor)?.also { findCursor = it }

    /**
     * TalkBack "Place next gem" (design D19): the cell Find last showed while it's still empty, else the next remaining
     * cell of the selected color in reading order. It goes through [place] like a tap, so the save log and the
     * time-lapse can't tell the difference. Null when the selected color has nothing left.
     */
    fun placeNext(): PlaceResult? {
        val cell = nextRemainingCell(pattern, state::isPlaced, selectedSlot, findCursor - 1) ?: return null
        findCursor = cell
        val result = place(cell)
        // A finished color already announced "Color 7 done. Color 8 selected."
        if (result is PlaceResult.Placed && result.events.none { it is GameEvent.ColorFinished }) {
            announce(A11yText.left(remaining(selectedSlot)))
        }
        return result
    }

    /** The live region has spoken [shown]; clearing it lets the same words be announced again. */
    fun announced(shown: Announcement) {
        if (announcement == shown) announcement = null
    }

    private var announcements = 0

    private fun announce(text: String) {
        announcement = Announcement(text, ++announcements)
    }

    /** Design D23: a one-finger drag from [cell] paints, at the current zoom and selection, instead of panning. */
    fun startsPaint(cell: Int?): Boolean = CanvasGestures.startsPaint(viewport.zoomDp, cell, state, selectedSlot)

    @androidx.annotation.VisibleForTesting
    internal fun placedLogForTest(): IntArray = state.log()

    fun trayOrder(): IntArray = state.trayOrder()

    /** Finished colors can't be selected. */
    fun select(slot: Int) {
        if (slot in pattern.palette.indices && !state.isFinished(slot) && slot != selectedSlot) {
            selectedSlot = slot
            findCursor = -1
        }
    }

    /** A tap at placement zoom. The result drives haptics and sound; a rejection changes nothing but [wrongTaps]. */
    fun place(cell: Int): PlaceResult {
        val result = state.place(cell, selectedSlot)
        when (result) {
            is PlaceResult.Rejected -> if (result.reason == Rejection.WRONG_COLOR) {
                wrongTaps++
                // An announcement only: nothing else changes (eng D4).
                announce(A11yText.notColor(selectedSlot + 1))
            }
            is PlaceResult.Placed -> {
                saver.onPlacement(cell)
                placedCount = state.placedCount
                for (event in result.events) {
                    when (event) {
                        is GameEvent.ColorFinished -> {
                            event.next?.let {
                                selectedSlot = it
                                findCursor = -1
                            }
                            announce(A11yText.colorDone(event.slot + 1, event.next?.plus(1)))
                        }
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

    private companion object {
        const val FIND_MAX_LEFT = 20
    }
}

/** One announcement; [id] tells two identical texts apart, so "Not color 7" twice is spoken twice. */
data class Announcement(val text: String, val id: Int)
