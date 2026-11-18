package com.brewingbytes.gemmory.share

import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * Start over, Play again, Delete and Delete all call it before touching a canvas's files (eng E4d, eng re-check
 * R-X1), so a running time-lapse encode is cancelled and that canvas's cached MP4s and pending marker are removed.
 * The app's implementation is [com.brewingbytes.gemmory.timelapse.EncodeManager].
 */
interface EncodeCanceller {
    suspend fun cancelAndClear(ref: CanvasRef)

    /** A gem was placed on [ref]: a progress share's encode of it is out of date (#36). */
    fun onPlaced(ref: CanvasRef) {}
}
