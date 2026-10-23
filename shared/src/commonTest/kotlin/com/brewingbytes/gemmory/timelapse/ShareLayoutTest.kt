package com.brewingbytes.gemmory.timelapse

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The player's share frame (T53). This is the pinned check of the 9:16 share that replaced T47's "9:16 share
 * unchanged" (R1 changed on purpose); the founder clips keep [FrameLayout] (see [FrameLayoutTest]).
 */
class ShareLayoutTest {
    /** Starter, Quick, Medium and Big, portrait, square and landscape (design doc, size table). */
    private val grids = listOf(24 to 32, 56 to 56, 78 to 104, 104 to 78, 106 to 142, 142 to 106, 60 to 80, 80 to 60, 30 to 40)
    private val frames = listOf(1080 to 1920, 720 to 1280)

    @Test
    fun everyRatioFitsTheWholePictureAtFullWidthCenteredAboveTheRow() {
        for ((w, h) in frames) for ((gw, gh) in grids) for (branded in listOf(true, false)) {
            val s = w / 1080f
            val l = ShareLayout.of(w, h, gw, gh, branded)
            val name = "${w}x$h ${gw}x$gh branded=$branded"
            assertEquals(0, l.board.left, name)
            assertEquals(w, l.board.right, name)
            assertTrue(abs(l.board.width.toFloat() / l.board.height - gw.toFloat() / gh) < 0.01f, "$name keeps the ratio")
            assertTrue(l.board.top >= 0 && l.board.bottom <= l.areaBottom, "$name inside the picture area")
            val centered = (l.areaBottom - l.board.height) / 2 - (ShareLayout.LIFT * s).roundToInt()
            assertEquals(maxOf(0, centered), l.board.top, "$name centered, lifted 140")
            assertEquals(l.areaBottom - (ShareLayout.FADE * s).roundToInt(), l.fadeTop, name)
            assertTrue(l.appBaseline < l.areaBottom && l.appBaseline > l.fadeTop, "$name text over the fade, above the row")
        }
    }

    @Test
    fun theRowIsTheBottom150AndVipHasNoRowNorCard() {
        for ((w, h) in frames) for ((gw, gh) in grids) {
            val s = w / 1080f
            val branded = ShareLayout.of(w, h, gw, gh, branded = true)
            val row = assertNotNull(branded.row)
            assertEquals(h - (150 * s).roundToInt(), row.top)
            assertEquals(row.top, branded.areaBottom)
            assertTrue(row.badge.bottom <= h && row.badge.right <= w && row.icon.top > row.top)
            assertTrue(row.icon.right < row.textLeft && row.badge.left > row.textLeft)
            val vip = ShareLayout.of(w, h, gw, gh, branded = false)
            assertNull(vip.row)
            assertNull(vip.card)
            assertEquals(h, vip.areaBottom, "VIP's picture area is the whole frame")
        }
    }

    @Test
    fun theEndCardBlockFitsTheFrameForEveryRatio() {
        for ((w, h) in frames) for ((gw, gh) in grids) {
            val card = assertNotNull(ShareLayout.of(w, h, gw, gh, branded = true).card)
            val box = (760 * (w / 1080f)).roundToInt()
            assertEquals(box, maxOf(card.picture.width, card.picture.height), "${gw}x$gh fits the 760 box")
            assertTrue(abs(card.picture.width.toFloat() / card.picture.height - gw.toFloat() / gh) < 0.01f)
            assertTrue(card.top >= 0 && card.bottom + card.slide <= h, "${w}x$h ${gw}x$gh inside the frame while it slides")
            assertTrue(abs(card.top - (h - card.bottom)) <= 1, "centered as one block")
            assertTrue(card.picture.bottom < card.icon.top && card.icon.bottom < card.nameBaseline)
            assertTrue(card.nameBaseline < card.taglineBaseline && card.taglineBaseline < card.badge.top)
        }
    }

    @Test
    fun pinnedStarterAt1080() {
        val l = ShareLayout.of(1080, 1920, 24, 32, branded = true)
        assertEquals(BoardRect(0, 25, 1080, 1465), l.board)
        assertEquals(1770, l.areaBottom)
        assertEquals(1350, l.fadeTop)
        assertEquals(1726, l.appBaseline)
        assertEquals(
            ShareLayout.Row(1770, BoardRect(48, 1803, 132, 1887), 18, 154, 1840, 1880, BoardRect(696, 1780, 1032, 1910)),
            l.row,
        )
        assertEquals(
            ShareLayout.Card(
                picture = BoardRect(255, 230, 825, 990),
                icon = BoardRect(450, 1080, 630, 1260),
                iconRadius = 40,
                nameBaseline = 1378,
                taglineBaseline = 1448,
                badge = BoardRect(280, 1488, 800, 1689),
                slide = 30,
            ),
            l.card,
        )
        assertEquals(BoardRect(0, 100, 1080, 1540), ShareLayout.of(1080, 1920, 24, 32, branded = false).board)
    }

    @Test
    fun pinnedLandscapeAt1080() {
        val l = ShareLayout.of(1080, 1920, 104, 78, branded = true)
        assertEquals(BoardRect(0, 340, 1080, 1150), l.board)
        assertEquals(BoardRect(160, 325, 920, 895), l.card?.picture)
    }
}
