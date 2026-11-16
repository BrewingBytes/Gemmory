package com.brewingbytes.gemmory.share

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * Share progress (#36) for one screen, kept in its ViewModel so a rotation keeps the sheet. Android's
 * `ProgressSharing` is it (encode, then the share sheet, drawn by the shell's `ProgressShareHost`); iOS's comes with
 * its share sheet in Stage 2.
 */
interface ProgressShareSession {
    /** The canvas whose share sheet is up; null when nothing was shared from this screen. */
    val sharingRef: CanvasRef?

    /** Share progress tap: the time-lapse of [log] so far over [pattern]. */
    fun share(ref: CanvasRef, pattern: Pattern, log: IntArray, title: String)

    /** The screen moved on: its sheet belongs to nothing now (a running encode goes on). */
    fun clear()
}
