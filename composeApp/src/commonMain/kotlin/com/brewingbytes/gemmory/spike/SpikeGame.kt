package com.brewingbytes.gemmory.spike

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.play.CanvasViewport
import com.brewingbytes.gemmory.render.CanvasGame
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.save.SaveCodec

/** Where the spike keeps its one save file: app-private storage on each platform. */
internal interface SpikeFiles {
    fun read(name: String): ByteArray?

    fun write(name: String, bytes: ByteArray)
}

/**
 * The spike's one open canvas: the shared [CanvasState] rules (a wrong-color tap is rejected with no state change),
 * the selected color, and the save log, written whole after each placement and read back on open (save and resume).
 * The app's `SaveLoop` cadence and fsync come in Stage 1c; this is only enough to prove the codecs on iOS.
 */
internal class SpikeGame private constructor(
    val grid: GridFile,
    private val state: CanvasState,
    private val files: SpikeFiles?,
) : CanvasGame {
    val pattern: Pattern get() = grid.pattern
    val viewport = CanvasViewport(pattern.width, pattern.height)

    override var selectedSlot by mutableIntStateOf(state.initialSlot(null))
        private set

    override var placedCount by mutableIntStateOf(state.placedCount)
        private set

    /** Bumps on every rejected tap, so a test (and the screen) can see a rejection happened. */
    var rejections by mutableIntStateOf(0)
        private set

    override fun placedAt(index: Int): Int = state.placedAt(index)

    override fun isPlaced(cell: Int): Boolean = state.isPlaced(cell)

    fun select(slot: Int) {
        if (slot in pattern.palette.indices) selectedSlot = slot
    }

    fun place(cell: Int): PlaceResult {
        val result = state.place(cell, selectedSlot)
        when (result) {
            is PlaceResult.Placed -> {
                placedCount = state.placedCount
                files?.write(SAVE, SaveCodec().encode(grid.binding, state.log()))
            }
            is PlaceResult.Rejected -> rejections++
        }
        return result
    }

    companion object {
        const val SAVE = "spike.gmrs"

        /** Opens [gridBytes] and resumes its save from [files], if one is there and valid; otherwise starts empty. */
        fun open(gridBytes: ByteArray, files: SpikeFiles?): SpikeGame {
            val grid = (GridCodec.decode(gridBytes) as Decoded.Ok).value
            val saved = files?.read(SAVE)?.let { (SaveCodec().decode(it, grid.binding) as? Decoded.Ok)?.value }
            val state = saved?.let { CanvasState.restore(grid.pattern, it) } ?: CanvasState.empty(grid.pattern)
            return SpikeGame(grid, state, files)
        }
    }
}
