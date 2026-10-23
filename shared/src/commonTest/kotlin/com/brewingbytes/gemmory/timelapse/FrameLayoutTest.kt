package com.brewingbytes.gemmory.timelapse

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameLayoutTest {
    /** Starter, Quick, Medium and Big, portrait, square and landscape (design doc, size table). */
    private val grids = listOf(24 to 32, 56 to 56, 78 to 104, 104 to 78, 106 to 142, 142 to 106, 60 to 80, 80 to 60, 30 to 40)

    /** The founder clips' 9:16 (T47) keeps the frame the player's share had before T53, until #144 gives it its own. */
    @Test
    fun clipNineBySixteenIsTheFullWidthBoardAsBefore() {
        for ((w, h) in listOf(1080 to 1920, 720 to 1280)) for ((gw, gh) in grids) {
            // The layout FrameArt had before T47.
            val top = (36 * (w / 1080f)).roundToInt()
            val expected = BoardRect(0, top, w, top + (w.toLong() * gh / gw).toInt())
            assertEquals(expected, FrameLayout.board(w, h, gw, gh), "${w}x$h, grid ${gw}x$gh")
        }
    }

    @Test
    fun fourByFiveFitsTheCanvasAboveTheTextAndCentersIt() {
        for ((w, h) in listOf(1080 to 1350, 864 to 1080)) for ((gw, gh) in grids) {
            val s = w / 1080f
            val board = FrameLayout.board(w, h, gw, gh)
            assertTrue(board.bottom <= h - (FrameLayout.TEXT_RESERVE * s).roundToInt(), "${w}x$h ${gw}x$gh leaves room for the text")
            assertTrue(board.left >= 0 && board.right <= w)
            assertTrue(kotlin.math.abs(board.left - (w - board.right)) <= 1, "centered")
            assertTrue(kotlin.math.abs(board.width.toFloat() / board.height - gw.toFloat() / gh) < 0.02f, "keeps the ratio")
        }
        // A landscape picture still runs the full width.
        assertEquals(1080, FrameLayout.board(1080, 1350, 104, 78).width)
    }
}
