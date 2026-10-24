package com.brewingbytes.gemmory.timelapse

import kotlin.math.roundToInt

/**
 * A founder clip's frame (#144, design system, Founder clip frame), in frame pixels. Sizes are given at 1080 wide and
 * scaled by the frame's width.
 *
 * Everything readable sits in the feeds' safe area ([SAFE_LEFT]-[SAFE_RIGHT], [SAFE_TOP]-[SAFE_BOTTOM] at 1080×1920:
 * the overlap of the TikTok, Reels and Shorts overlays). From the bottom up (C2): the store lockup (the app icon,
 * "Gemmory" over "Free on Google Play") ending on the safe area's bottom, the title [TITLE_GAP] above it, and the
 * canvas [BOARD_GAP] above the title, full width unless its top would pass [BOARD_TOP_MIN], then narrowed and
 * centered. A 4:5 frame puts the lockup [FOUR_FIVE_BOTTOM] above its bottom and lets the canvas reach [FOUR_FIVE_TOP_MIN].
 *
 * The end card (E2b) is one group, centered in the safe area (4:5: in the frame within [CARD_MARGIN]) and on the
 * frame's middle: the finished canvas [CARD_PICTURE] wide (narrower if the group wouldn't fit), the lockup, then the
 * promise line(s).
 *
 * The title's line count and the lockup text's width are measured by the caller (the fonts live there), at most
 * [titleWidth] and [LOCKUP_TEXT_MAX] wide.
 */
data class ClipLayout(
    val board: BoardRect,
    /** The title's box: its first line's top to its last line's bottom, [TITLE_LINE] a line. */
    val title: BoardRect,
    val lockup: Lockup,
    val card: Card,
    /** Where every word goes: the feeds' safe area in 9:16, the frame within [FOUR_FIVE_BOTTOM] in 4:5. */
    val safeArea: BoardRect,
) {
    /** The app icon at [icon] with [iconRadius] corners; "Gemmory" over the store line, [textLeft] to [right]. */
    data class Lockup(val icon: BoardRect, val iconRadius: Int, val textLeft: Int, val right: Int) {
        val top: Int get() = icon.top
        val bottom: Int get() = icon.bottom
        val left: Int get() = icon.left
    }

    data class Card(
        val picture: BoardRect,
        val lockup: Lockup,
        /** The promise's box, its lines centered on the frame's middle. */
        val promise: BoardRect,
        /** What the card's layer covers: the picture's shadow, the lockup and the promise. */
        val area: BoardRect,
    )

    companion object {
        const val SAFE_LEFT = 60
        const val SAFE_RIGHT = 940
        const val SAFE_TOP = 250
        const val SAFE_BOTTOM = 1440

        const val MARGIN = 64
        const val TITLE_RIGHT = 920
        const val TITLE_LINE = 70
        const val TITLE_GAP = 28
        const val BOARD_GAP = 48
        const val BOARD_TOP_MIN = 130

        const val FOUR_FIVE_BOTTOM = 40
        const val FOUR_FIVE_TOP_MIN = 60

        const val ICON = 132
        const val ICON_RADIUS = 30
        const val ICON_GAP = 28

        /** The lockup's text column at its widest: a longer translation is drawn smaller. */
        const val LOCKUP_TEXT_MAX = 640

        const val CARD_PICTURE = 760
        const val CARD_LOCKUP_GAP = 72
        const val CARD_PROMISE_GAP = 44
        const val PROMISE_LINE = 56

        /** The promise's box: centered on the frame, it ends on the safe area's right edge. */
        const val PROMISE_WIDTH = 800
        const val CARD_MARGIN = 60

        /** Room around the card's picture for its shadow (0 24px, 60px blur). */
        const val CARD_SHADOW_ROOM = 100

        /** The title's box width in a frame [frameWidth] wide. */
        fun titleWidth(frameWidth: Int): Int = px(frameWidth, TITLE_RIGHT - MARGIN)

        /** A 9:16 frame (1080×1920, 720×1280); anything shorter is laid out as 4:5. */
        fun isNineSixteen(frameWidth: Int, frameHeight: Int): Boolean = frameHeight * 9L >= frameWidth * 16L - 9

        fun of(
            frameWidth: Int,
            frameHeight: Int,
            gridWidth: Int,
            gridHeight: Int,
            titleLines: Int,
            lockupTextWidth: Int,
            promiseLines: Int = 1,
        ): ClipLayout {
            require(titleLines in 1..2) { "titleLines must be 1 or 2" }
            require(promiseLines in 1..2) { "promiseLines must be 1 or 2" }
            fun px(at1080: Int): Int = px(frameWidth, at1080)
            val tall = isNineSixteen(frameWidth, frameHeight)
            val safe = if (tall) {
                BoardRect(px(SAFE_LEFT), px(SAFE_TOP), px(SAFE_RIGHT), px(SAFE_BOTTOM))
            } else {
                // A 4:5 post's caption sits under it, not over it: the frame within the lockup's bottom margin.
                BoardRect(px(FOUR_FIVE_BOTTOM), px(FOUR_FIVE_BOTTOM), frameWidth - px(FOUR_FIVE_BOTTOM), frameHeight - px(FOUR_FIVE_BOTTOM))
            }
            val cardBounds = if (tall) safe else BoardRect(px(CARD_MARGIN), px(CARD_MARGIN), frameWidth - px(CARD_MARGIN), frameHeight - px(CARD_MARGIN))
            val lockupWidth = lockupTextWidth.coerceIn(0, px(LOCKUP_TEXT_MAX))

            val lockupBottom = if (tall) px(SAFE_BOTTOM) else frameHeight - px(FOUR_FIVE_BOTTOM)
            val lockup = lockup(px(MARGIN), lockupBottom - px(ICON), lockupWidth, ::px)
            val titleBottom = lockup.top - px(TITLE_GAP)
            val titleTop = titleBottom - titleLines * px(TITLE_LINE)
            val title = BoardRect(px(MARGIN), titleTop, px(TITLE_RIGHT), titleBottom)
            val board = board(frameWidth, gridWidth, gridHeight, titleTop - px(BOARD_GAP), px(if (tall) BOARD_TOP_MIN else FOUR_FIVE_TOP_MIN))

            return ClipLayout(board, title, lockup, card(frameWidth, cardBounds, gridWidth, gridHeight, lockupWidth, promiseLines, ::px), safe)
        }

        private fun px(frameWidth: Int, at1080: Int): Int = (at1080 * (frameWidth / 1080f)).roundToInt()

        private fun lockup(left: Int, top: Int, textWidth: Int, px: (Int) -> Int): Lockup {
            val icon = BoardRect(left, top, left + px(ICON), top + px(ICON))
            val textLeft = icon.right + px(ICON_GAP)
            return Lockup(icon, px(ICON_RADIUS), textLeft, textLeft + textWidth)
        }

        /** Bottom-anchored at [bottom]: full width while its top stays at [minTop] or lower, else narrowed and centered. */
        private fun board(frameWidth: Int, gridWidth: Int, gridHeight: Int, bottom: Int, minTop: Int): BoardRect {
            val fullHeight = (frameWidth.toLong() * gridHeight / gridWidth).toInt()
            if (bottom - fullHeight >= minTop) return BoardRect(0, bottom - fullHeight, frameWidth, bottom)
            val width = ((bottom - minTop).toLong() * gridWidth / gridHeight).toInt()
            val height = (width.toLong() * gridHeight / gridWidth).toInt()
            val left = (frameWidth - width) / 2
            return BoardRect(left, bottom - height, left + width, bottom)
        }

        private fun card(
            frameWidth: Int,
            bounds: BoardRect,
            gridWidth: Int,
            gridHeight: Int,
            lockupTextWidth: Int,
            promiseLines: Int,
            px: (Int) -> Int,
        ): Card {
            val rest = px(CARD_LOCKUP_GAP) + px(ICON) + px(CARD_PROMISE_GAP) + promiseLines * px(PROMISE_LINE)
            // Centered on the frame's middle, the picture can't pass the safe area's nearer side.
            val maxWidth = minOf(px(CARD_PICTURE), 2 * minOf(frameWidth / 2 - bounds.left, bounds.right - frameWidth / 2))
            var w = maxWidth
            var h = (w.toLong() * gridHeight / gridWidth).toInt()
            if (h > bounds.height - rest) {
                w = ((bounds.height - rest).toLong() * gridWidth / gridHeight).toInt()
                h = (w.toLong() * gridHeight / gridWidth).toInt()
            }
            val top = (bounds.top + bounds.bottom - (h + rest)) / 2
            val pictureLeft = (frameWidth - w) / 2
            val picture = BoardRect(pictureLeft, top, pictureLeft + w, top + h)
            val lockupWidth = px(ICON) + px(ICON_GAP) + lockupTextWidth
            val lockup = lockup((frameWidth - lockupWidth) / 2, picture.bottom + px(CARD_LOCKUP_GAP), lockupTextWidth, px)
            val promiseTop = lockup.bottom + px(CARD_PROMISE_GAP)
            val promiseHalf = minOf(px(PROMISE_WIDTH) / 2, frameWidth / 2 - bounds.left, bounds.right - frameWidth / 2)
            val promise = BoardRect(frameWidth / 2 - promiseHalf, promiseTop, frameWidth / 2 + promiseHalf, promiseTop + promiseLines * px(PROMISE_LINE))
            val area = BoardRect(0, picture.top - px(CARD_SHADOW_ROOM), frameWidth, maxOf(promise.bottom, picture.bottom + px(CARD_SHADOW_ROOM)))
            return Card(picture, lockup, promise, area)
        }
    }
}
