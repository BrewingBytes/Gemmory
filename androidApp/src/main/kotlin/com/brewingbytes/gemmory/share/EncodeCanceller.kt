package com.brewingbytes.gemmory.share

import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * Seam for #4's EncodeManager (eng E4d, eng re-check R-X1). Start over, Play again, Delete and Delete all call it
 * before touching a canvas's files, so a running time-lapse encode is cancelled and that canvas's cached MP4s and
 * pending marker are removed. Until #4 lands there is no encoder, so it does nothing.
 */
fun interface EncodeCanceller {
    fun cancelAndClear(ref: CanvasRef)

    companion object {
        /** Replaced by EncodeManager.cancel in #4. */
        val NONE_UNTIL_TIMELAPSE = EncodeCanceller { }
    }
}
