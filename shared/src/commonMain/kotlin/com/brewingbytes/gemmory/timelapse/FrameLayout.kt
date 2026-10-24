package com.brewingbytes.gemmory.timelapse

import kotlin.math.roundToInt

/** Where the canvas sits in a time-lapse frame, in frame pixels. */
data class BoardRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * T47's founder clip frame: the 9:16 frame the player's share had before T53. The clips' videos use [ClipLayout] since
 * #144 and the player's share [ShareLayout]; the clip stills keep T47's look and size their text with [TEXT_RESERVE]. Sizes are given at
 * 1080 wide and scaled by the frame's width. The canvas runs the full width under the [BOARD_TOP] margin while [TEXT_RESERVE] still
 * fits under it for the title, app line and watermark row: every 9:16 frame, whatever the picture's ratio. A shorter
 * frame (the founder clip tool's 4:5, T47) fits the canvas's height into what's left and centers it.
 */
object FrameLayout {
    const val BOARD_TOP = 36

    /** Room under the canvas: the title's 40px gap and two lines, the app line and the watermark row. */
    const val TEXT_RESERVE = 340

    fun board(frameWidth: Int, frameHeight: Int, gridWidth: Int, gridHeight: Int): BoardRect {
        val s = frameWidth / 1080f
        val top = (BOARD_TOP * s).roundToInt()
        val fullHeight = (frameWidth.toLong() * gridHeight / gridWidth).toInt()
        val room = frameHeight - top - (TEXT_RESERVE * s).roundToInt()
        if (fullHeight <= room) return BoardRect(0, top, frameWidth, top + fullHeight)
        val width = (room.toLong() * gridWidth / gridHeight).toInt()
        val height = (width.toLong() * gridHeight / gridWidth).toInt()
        val left = (frameWidth - width) / 2
        return BoardRect(left, top, left + width, top + height)
    }
}
