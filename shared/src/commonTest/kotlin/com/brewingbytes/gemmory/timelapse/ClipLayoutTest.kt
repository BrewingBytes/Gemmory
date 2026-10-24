package com.brewingbytes.gemmory.timelapse

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A founder clip's frame (#144, C2 and E2b): every word inside the feeds' safe area. */
class ClipLayoutTest {
    /** Starter, Quick, Medium and Big, portrait, square and landscape (design doc, size table). */
    private val grids = listOf(24 to 32, 56 to 56, 78 to 104, 104 to 78, 106 to 142, 142 to 106, 60 to 80, 80 to 60, 30 to 40)
    private val tall = listOf(1080 to 1920, 720 to 1280)
    private val fourByFive = listOf(1080 to 1350, 864 to 1080)

    private fun inside(outer: BoardRect, inner: BoardRect) =
        inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom

    private fun layouts() = sequence {
        for ((w, h) in tall + fourByFive) for ((gw, gh) in grids) for (lines in 1..2) for (promise in 1..2) {
            // The lockup's text: English's width (~380 at 1080), and a translation at the widest it's drawn.
            for (text in listOf(380, ClipLayout.LOCKUP_TEXT_MAX)) {
                val s = w / 1080f
                val name = "${w}x$h ${gw}x$gh title=$lines promise=$promise text=$text"
                yield(name to ClipLayout.of(w, h, gw, gh, lines, (text * s).roundToInt(), promise))
            }
        }
    }

    @Test
    fun everyWordIsInsideTheSafeArea() {
        for ((name, l) in layouts()) {
            assertTrue(inside(l.safeArea, l.title), "$name title ${l.title} in ${l.safeArea}")
            assertTrue(inside(l.safeArea, l.lockup.icon), "$name lockup icon")
            assertTrue(l.lockup.right <= l.safeArea.right, "$name lockup text")
            val c = l.card
            for ((what, r) in listOf("picture" to c.picture, "icon" to c.lockup.icon, "promise" to c.promise)) {
                assertTrue(inside(l.safeArea, r), "$name card $what $r in ${l.safeArea}")
            }
            assertTrue(c.lockup.right <= l.safeArea.right && c.lockup.left >= l.safeArea.left, "$name card lockup text")
        }
    }

    @Test
    fun theSafeAreaIsTheFeedsOverlap() {
        assertEquals(BoardRect(60, 250, 940, 1440), ClipLayout.of(1080, 1920, 104, 78, 1, 380).safeArea)
        assertEquals(BoardRect(40, 167, 627, 960), ClipLayout.of(720, 1280, 104, 78, 1, 253).safeArea)
        assertEquals(BoardRect(40, 40, 1040, 1310), ClipLayout.of(1080, 1350, 104, 78, 1, 380).safeArea)
    }

    @Test
    fun theLockupEndsOnTheSafeAreasBottomThenTitleThenCanvas() {
        for ((w, h) in tall + fourByFive) for ((gw, gh) in grids) for (lines in 1..2) {
            val s = w / 1080f
            fun px(v: Int) = (v * s).roundToInt()
            val l = ClipLayout.of(w, h, gw, gh, lines, px(380))
            val name = "${w}x$h ${gw}x$gh title=$lines"
            val nineSixteen = h * 9 >= w * 16 - 9
            assertEquals(if (nineSixteen) px(1440) else h - px(40), l.lockup.bottom, name)
            assertEquals(px(132), l.lockup.icon.width, name)
            assertEquals(px(64), l.lockup.left, name)
            assertEquals(l.lockup.top - px(28), l.title.bottom, name)
            assertEquals(lines * px(70), l.title.height, name)
            assertEquals(l.title.top - px(48), l.board.bottom, name)
            assertTrue(l.board.top >= px(if (nineSixteen) 130 else 60), "$name board top ${l.board.top}")
            assertTrue(abs(l.board.left - (w - l.board.right)) <= 1, "$name centered")
            assertTrue(abs(l.board.width.toFloat() / l.board.height - gw.toFloat() / gh) < 0.02f, "$name keeps the ratio")
        }
    }

    @Test
    fun aWidePictureRunsTheFullWidthAndATallOneNarrows() {
        val wave = ClipLayout.of(1080, 1920, 104, 78, 1, 380)
        assertEquals(BoardRect(0, 1162 - 810, 1080, 1162), wave.board)
        val starry = ClipLayout.of(1080, 1920, 78, 104, 1, 380)
        assertEquals(130, starry.board.top)
        assertTrue(starry.board.width < 1080)
        // 4:5 keeps a landscape picture full width too.
        assertEquals(1080, ClipLayout.of(1080, 1350, 104, 78, 1, 380).board.width)
    }

    @Test
    fun aTwoLineTitlePushesTheCanvasUpByALine() {
        val one = ClipLayout.of(1080, 1920, 104, 78, 1, 380)
        val two = ClipLayout.of(1080, 1920, 104, 78, 2, 380)
        assertEquals(one.board.bottom - 70, two.board.bottom)
        assertEquals(one.board.height, two.board.height)
        assertEquals(one.lockup, two.lockup)
    }

    @Test
    fun theEndCardIsOneCenteredGroup() {
        for ((w, h) in tall + fourByFive) for ((gw, gh) in grids) for (promise in 1..2) {
            val s = w / 1080f
            fun px(v: Int) = (v * s).roundToInt()
            val c = ClipLayout.of(w, h, gw, gh, 1, px(380), promise)
            val card = c.card
            val name = "${w}x$h ${gw}x$gh promise=$promise"
            assertTrue(card.picture.width <= px(760), "$name picture ${card.picture.width} wide")
            assertTrue(abs(card.picture.width.toFloat() / card.picture.height - gw.toFloat() / gh) < 0.02f, "$name keeps the ratio")
            assertEquals(card.picture.bottom + px(72), card.lockup.top, name)
            assertEquals(card.lockup.bottom + px(44), card.promise.top, name)
            assertEquals(promise * px(56), card.promise.height, name)
            for (r in listOf(card.picture, card.promise)) assertTrue(abs(r.left - (w - r.right)) <= 1, "$name $r centered")
            assertTrue(abs(card.lockup.left - (w - card.lockup.right)) <= 1, "$name lockup centered")
            // Centered between the safe area's top and bottom (4:5: the frame within 60 px).
            val bounds = if (h * 9 >= w * 16 - 9) c.safeArea else BoardRect(px(60), px(60), w - px(60), h - px(60))
            assertTrue(inside(bounds, card.picture) && inside(bounds, card.promise), "$name card in $bounds")
            val above = card.picture.top - bounds.top
            val below = bounds.bottom - card.promise.bottom
            assertTrue(abs(above - below) <= 1, "$name group centered: $above above, $below below")
            assertTrue(card.area.top <= card.picture.top - px(100) + 1 && card.area.bottom >= card.promise.bottom, "$name layer covers the card")
        }
        // A wide picture is 760 wide in 9:16.
        assertEquals(760, ClipLayout.of(1080, 1920, 104, 78, 1, 380).card.picture.width)
    }
}
