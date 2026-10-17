package com.brewingbytes.gemmory.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.Batch
import com.brewingbytes.gemmory.game.CanvasGestures
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.GameEvent
import com.brewingbytes.gemmory.game.Milestones
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.game.PlacementTool
import com.brewingbytes.gemmory.game.PlacementTools
import com.brewingbytes.gemmory.game.Rejection
import com.brewingbytes.gemmory.game.Sweep
import com.brewingbytes.gemmory.game.ToolCells
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
    /** The last gem was just placed (not a reopen of a finished canvas). */
    private val onPictureFinished: () -> Unit = {},
    /** Any gem was placed: a progress share's encode is out of date (#36). */
    private val onPlaced: () -> Unit = {},
    /** A placement tool (#20) placed its first gems on this canvas since it opened. */
    private val onToolUsed: (tool: PlacementTool, unlimited: Boolean) -> Unit = { _, _ -> },
    /** The monotonic clock the batch cues wait on (#20). */
    private val uptimeMillis: () -> Long = { System.nanoTime() / 1_000_000 },
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

    /**
     * The latest progress milestone (#35) crossed by a placement in this session; its banner, chime and haptic play
     * once per [Milestone.id]. Null on open, so a canvas reopened past a milestone never replays it.
     */
    var milestone by mutableStateOf<Milestone?>(null)
        private set

    /** The Milestones setting (#35), kept current by the play screen; off, a crossing plays and says nothing. */
    var milestonesOn = true

    /** The milestone a cue last started for; a rotation mid-banner doesn't start it again. */
    var cuedMilestone = 0
        private set

    /** Marks [shown] as cued; false when it already was. */
    fun cue(shown: Milestone): Boolean {
        if (shown.id <= cuedMilestone) return false
        cuedMilestone = shown.id
        return true
    }

    /** VIP (#20): the placement tools without limit. Kept current by the play screen through [offerTools]. */
    var unlimitedTools by mutableStateOf(false)
        private set

    /**
     * Fills a player without VIP holds (#38), less those this session spent that the inventory hasn't counted yet;
     * each Fill region or Fill color use, or Brush stroke, takes one.
     */
    var fills by mutableIntStateOf(0)
        private set

    private var fillsFromInventory = 0
    private var fillsPending = 0

    /** A fill was spent on this canvas: at 0 the tool well stays, dimmed, rather than vanishing under the finger. */
    private var spentHere by mutableStateOf(false)

    /** Whether the tool well shows: VIP, fills held, or fills run out on this canvas. */
    val toolsOn: Boolean get() = unlimitedTools || fills > 0 || spentHere

    /** Whether a tool can place now; at 0 fills the well is dimmed and plain taps are back. */
    val toolsUsable: Boolean get() = unlimitedTools || fills > 0

    /** The tool well's mode (#20); Fill color is a button, never a mode. */
    var tool by mutableStateOf(PlacementTool.TAP)
        private set

    /**
     * Spends one fill for [useId] (#38) and calls `done` once the inventory has it; set by the play screen. Main thread.
     */
    var spendFill: (useId: String, done: () -> Unit) -> Unit = { _, done -> done() }

    /** VIP and the fills held, from the entitlements and the inventory (kept current by the play screen). */
    fun offerTools(unlimited: Boolean, fillsHeld: Int = 0) {
        unlimitedTools = unlimited
        fillsFromInventory = fillsHeld
        fills = maxOf(0, fillsHeld - fillsPending)
        if (!toolsUsable) tool = PlacementTool.TAP
    }

    fun chooseTool(chosen: PlacementTool) {
        if (toolsUsable && chosen in PlacementTool.MODES) tool = chosen
    }

    /** Whether the current Brush stroke (finger down to up, #38) has paid its fill. */
    private var strokePaid = false

    /** A Brush stroke starts (a tap, or a drag that paints): its first placement pays one fill. */
    fun startStroke() {
        strokePaid = false
    }

    private var uses = 0

    /** Pays one fill for a use that placed something (#38); VIP pays nothing. */
    private fun payFill() {
        if (unlimitedTools) return
        fillsPending++
        fills = maxOf(0, fills - 1)
        spentHere = true
        spendFill("fill-${System.nanoTime()}-${uses++}") {
            fillsPending--
            fills = maxOf(0, fillsFromInventory - fillsPending)
            if (!toolsUsable) tool = PlacementTool.TAP
        }
        if (!toolsUsable) tool = PlacementTool.TAP
    }

    /** Reduced motion, kept current by the play screen: a batch then shows at once and its cues don't wait. */
    var reducedMotion = false

    /** When the running batch's sweep ends (#20): its color-done, milestone and finish cues wait until then. */
    private var cuesAt = 0L

    /** How long the cues of the last batch still wait for its sweep; 0 when no sweep is running. */
    fun cueDelayMs(): Long = maxOf(0L, cuesAt - uptimeMillis())

    private val toolsUsed = HashSet<PlacementTool>()

    /** The peek (#35) was turned on or off from its TalkBack canvas action. */
    fun announcePeek(shown: Boolean) = announce(A11yText.peek(shown))

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
        val before = placedCount
        val result = place(cell)
        // A finished color already announced "Color 7 done. Color 8 selected." (and any milestone after it).
        if (result is PlaceResult.Placed && result.events.none { it is GameEvent.ColorFinished }) {
            val crossed = crossedMilestone(before)
            announce(listOfNotNull(A11yText.left(remaining(selectedSlot)), crossed?.let(A11yText::milestone)).joinToString(". "))
        }
        return result
    }

    /** Fill color (#20) shows beside Find for VIP while the selected color has 1 to 20 cells left. */
    val fillColorVisible: Boolean
        get() {
            placedCount // a snapshot read, as in findVisible
            return toolsUsable && remaining(selectedSlot) in 1..PlacementTools.FILL_COLOR_MAX_LEFT
        }

    /**
     * A brush stamp (#20) centred on [cell]: the selected color's unplaced cells in the 3×3 around it. The stroke's
     * first placement pays its fill (#38); the rest of the stroke is free, even once the fills run out.
     */
    fun brush(cell: Int): Batch? {
        val paid = strokePaid
        val batch = placeBatch(PlacementTool.BRUSH, PlacementTools.brush(pattern, cell), announceCount = false, paid = paid)
        if (batch != null) strokePaid = true
        return batch
    }

    /** Fill region (#20) from [cell], an unplaced cell of the selected color (the caller checks, as for a tap). */
    fun fillRegion(cell: Int): Batch? =
        placeBatch(PlacementTool.REGION, PlacementTools.region(pattern, cell, selectedSlot), announceCount = false)

    /** Fill color (#20): every remaining cell of the selected color, nearest to [from] (the view's middle) first. */
    fun fillColor(from: Int, announceCount: Boolean = false): Batch? {
        if (!fillColorVisible) return null
        return placeBatch(PlacementTool.FILL_COLOR, PlacementTools.fillColor(pattern, state::isPlaced, selectedSlot, from), announceCount)
    }

    /**
     * TalkBack "Fill region at next gem" (#20): the region holding the cell "Place next gem" would place, announced
     * with its count ("214 placed. 98 left.") unless it finishes the color.
     */
    fun fillRegionNext(): Batch? {
        if (!toolsUsable) return null
        val cell = nextRemainingCell(pattern, state::isPlaced, selectedSlot, findCursor - 1) ?: return null
        findCursor = cell
        return placeBatch(PlacementTool.REGION, PlacementTools.region(pattern, cell, selectedSlot), announceCount = true)
    }

    /**
     * A tool's batch (#20): every cell through [CanvasState.place] in order, saved like one placement, the selection
     * moving on if it finished the color. The milestone, announcement and finish cues are set now and play when the
     * sweep ends ([cueDelayMs]). Null when nothing could be placed (then nothing changed, and no fill is spent).
     * Without VIP a use needs a fill, unless [paid] already (a Brush stroke's later stamps).
     */
    private fun placeBatch(tool: PlacementTool, cells: ToolCells, announceCount: Boolean, paid: Boolean = false): Batch? {
        if (!paid && !toolsUsable) return null
        val before = placedCount
        val batch = PlacementTools.place(state, cells, selectedSlot)
        if (batch.placed.isEmpty()) return null
        if (!paid) payFill()
        saver.onPlacements(batch.placed)
        onPlaced()
        if (toolsUsed.add(tool)) onToolUsed(tool, unlimitedTools)
        placedCount = state.placedCount
        cuesAt = uptimeMillis() + if (reducedMotion) 0 else Sweep.durationMs(batch.lastRing)
        val crossed = crossedMilestone(before)
        if (crossed != null) milestone = Milestone(crossed, (milestone?.id ?: 0) + 1)
        val done = batch.colorFinished
        if (done != null) {
            done.next?.let {
                selectedSlot = it
                findCursor = -1
            }
            val line = A11yText.colorDone(done.slot + 1, done.next?.plus(1))
            announce(if (crossed == null) line else "$line ${A11yText.milestone(crossed)}.")
        } else {
            val count = if (announceCount) A11yText.batch(batch.placed.size, remaining(selectedSlot)) else null
            val line = listOfNotNull(count, crossed?.let(A11yText::milestone)).joinToString(". ")
            if (line.isNotEmpty()) announce(line)
        }
        if (batch.pictureFinished) {
            saver.writeNow()
            onPictureFinished()
        }
        return batch
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

    /** The save log so far, in order: what Share progress replays (#36). */
    fun log(): IntArray = state.log()

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
                onPlaced()
                val before = placedCount
                placedCount = state.placedCount
                val crossed = crossedMilestone(before)
                if (crossed != null) milestone = Milestone(crossed, (milestone?.id ?: 0) + 1)
                for (event in result.events) {
                    when (event) {
                        is GameEvent.ColorFinished -> {
                            event.next?.let {
                                selectedSlot = it
                                findCursor = -1
                            }
                            val done = A11yText.colorDone(event.slot + 1, event.next?.plus(1))
                            // One line when the same gem finishes a color and crosses a milestone.
                            announce(if (crossed == null) done else "$done ${A11yText.milestone(crossed)}.")
                        }
                        GameEvent.PictureFinished -> {
                            saver.writeNow()
                            onPictureFinished()
                        }
                        is GameEvent.GemPlaced -> Unit
                    }
                }
                if (crossed != null && result.events.none { it is GameEvent.ColorFinished }) announce(A11yText.milestone(crossed))
            }
        }
        return result
    }

    private fun crossedMilestone(before: Int): Int? = if (milestonesOn) Milestones.crossed(before, placedCount, cellCount) else null

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

/** A progress milestone (#35): [percent] is 25, 50 or 75; [id] counts up, so each crossing is its own cue. */
data class Milestone(val percent: Int, val id: Int)

/** One announcement; [id] tells two identical texts apart, so "Not color 7" twice is spoken twice. */
data class Announcement(val text: String, val id: Int)
