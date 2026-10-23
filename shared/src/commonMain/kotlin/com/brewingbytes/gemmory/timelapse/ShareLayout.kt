package com.brewingbytes.gemmory.timelapse

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The player's share frame (T53, design system, 9:16 time-lapse frame), in frame pixels. Sizes are given at 1080 wide
 * and scaled by the frame's width.
 *
 * The whole picture runs the full width, centered in the area above the Gemmory row and lifted 140px, with the title
 * and app line over a dark fade at the bottom of that area (no zoom, no pan: D2/D3). The non-VIP row is a 150px band
 * at the bottom: icon, "Gemmory", "Your photos in gems" and Google's Play badge (D1). The end card (non-VIP) is the
 * picture, the icon, the name, the tagline and the badge, centered as one block. VIP has neither ([branded] false):
 * the picture area is then the whole frame.
 *
 * The founder clips (T47) keep [FrameLayout] until #144 gives them their own frame.
 */
data class ShareLayout(
    val board: BoardRect,
    /** The bottom of the picture area: the row's top, or the frame's bottom for VIP. */
    val areaBottom: Int,
    val fadeTop: Int,
    val textLeft: Int,
    /** The app line's baseline; the progress line and the title stack upward from it. */
    val appBaseline: Int,
    val row: Row?,
    val card: Card?,
) {
    data class Row(
        val top: Int,
        val icon: BoardRect,
        val iconRadius: Int,
        val textLeft: Int,
        val nameBaseline: Int,
        val taglineBaseline: Int,
        val badge: BoardRect,
    )

    data class Card(
        val picture: BoardRect,
        val icon: BoardRect,
        val iconRadius: Int,
        val nameBaseline: Int,
        val taglineBaseline: Int,
        val badge: BoardRect,
        /** The block starts this far down and slides up to its place as it fades in. */
        val slide: Int,
    ) {
        /** Everything the card draws besides the picture's own pixels (its shadow is drawn around it). */
        val top: Int get() = picture.top
        val bottom: Int get() = badge.bottom
    }

    companion object {
        const val ROW = 150
        const val LIFT = 140
        const val FADE = 420
        const val MARGIN = 48

        /** The app line's baseline above the area's bottom; the title's (one line) is [TITLE_GAP] above it. */
        const val APP_BASELINE = 44
        const val TITLE_GAP = 52

        const val ROW_ICON = 84
        const val ROW_ICON_TOP = 33
        const val ROW_ICON_RADIUS = 18
        const val ROW_TEXT_LEFT = 154
        const val ROW_NAME_BASELINE = 70
        const val ROW_TAGLINE_BASELINE = 110
        const val ROW_BADGE_WIDTH = 336
        const val ROW_BADGE_TOP = 10

        const val CARD_BOX = 760
        const val CARD_ICON_GAP = 90
        const val CARD_ICON = 180
        const val CARD_ICON_RADIUS = 40
        const val CARD_NAME_BASELINE = 34 + 84
        const val CARD_TAGLINE_BASELINE = 70
        const val CARD_BADGE_TOP = 110
        const val CARD_BADGE_WIDTH = 520
        const val CARD_SLIDE = 30

        /** Google's badge file is 646×250, its clear space included; it's drawn at that ratio. */
        const val BADGE_FILE_WIDTH = 646
        const val BADGE_FILE_HEIGHT = 250

        fun of(frameWidth: Int, frameHeight: Int, gridWidth: Int, gridHeight: Int, branded: Boolean): ShareLayout {
            val s = frameWidth / 1080f
            fun px(at1080: Int): Int = (at1080 * s).roundToInt()
            val areaBottom = if (branded) frameHeight - px(ROW) else frameHeight
            return ShareLayout(
                board = board(frameWidth, areaBottom, gridWidth, gridHeight, px(LIFT)),
                areaBottom = areaBottom,
                fadeTop = areaBottom - px(FADE),
                textLeft = px(MARGIN),
                appBaseline = areaBottom - px(APP_BASELINE),
                row = if (branded) row(frameWidth, frameHeight, ::px) else null,
                card = if (branded) card(frameWidth, frameHeight, gridWidth, gridHeight, ::px) else null,
            )
        }

        private fun badgeHeight(width: Int): Int = (width.toFloat() * BADGE_FILE_HEIGHT / BADGE_FILE_WIDTH).roundToInt()

        private fun board(frameWidth: Int, areaBottom: Int, gridWidth: Int, gridHeight: Int, lift: Int): BoardRect {
            val fullHeight = (frameWidth.toLong() * gridHeight / gridWidth).toInt()
            if (fullHeight <= areaBottom) {
                val top = max(0, (areaBottom - fullHeight) / 2 - lift)
                return BoardRect(0, top, frameWidth, top + fullHeight)
            }
            // Taller than the area (no grid ratio does this in 9:16): fit the height and center it.
            val width = (areaBottom.toLong() * gridWidth / gridHeight).toInt()
            val left = (frameWidth - width) / 2
            return BoardRect(left, 0, left + width, (width.toLong() * gridHeight / gridWidth).toInt())
        }

        private fun row(frameWidth: Int, frameHeight: Int, px: (Int) -> Int): Row {
            val top = frameHeight - px(ROW)
            val iconLeft = px(MARGIN)
            val iconTop = top + px(ROW_ICON_TOP)
            val badgeRight = frameWidth - px(MARGIN)
            val badgeTop = top + px(ROW_BADGE_TOP)
            return Row(
                top = top,
                icon = BoardRect(iconLeft, iconTop, iconLeft + px(ROW_ICON), iconTop + px(ROW_ICON)),
                iconRadius = px(ROW_ICON_RADIUS),
                textLeft = px(ROW_TEXT_LEFT),
                nameBaseline = top + px(ROW_NAME_BASELINE),
                taglineBaseline = top + px(ROW_TAGLINE_BASELINE),
                badge = BoardRect(badgeRight - px(ROW_BADGE_WIDTH), badgeTop, badgeRight, badgeTop + badgeHeight(px(ROW_BADGE_WIDTH))),
            )
        }

        private fun card(frameWidth: Int, frameHeight: Int, gridWidth: Int, gridHeight: Int, px: (Int) -> Int): Card {
            // The picture fits a 760px square: 760 wide for a landscape or square picture, 760 tall for a tall one.
            val box = px(CARD_BOX)
            val (w, h) = if (gridWidth >= gridHeight) {
                box to (box.toLong() * gridHeight / gridWidth).toInt()
            } else {
                (box.toLong() * gridWidth / gridHeight).toInt() to box
            }
            val badgeWidth = px(CARD_BADGE_WIDTH)
            val badgeH = badgeHeight(badgeWidth)
            val block = h + px(CARD_ICON_GAP) + px(CARD_ICON) + px(CARD_NAME_BASELINE) + px(CARD_BADGE_TOP) + badgeH
            val top = (frameHeight - block) / 2
            val pictureLeft = (frameWidth - w) / 2
            val iconTop = top + h + px(CARD_ICON_GAP)
            val iconLeft = (frameWidth - px(CARD_ICON)) / 2
            val nameBaseline = iconTop + px(CARD_ICON) + px(CARD_NAME_BASELINE)
            val badgeTop = nameBaseline + px(CARD_BADGE_TOP)
            val badgeLeft = (frameWidth - badgeWidth) / 2
            return Card(
                picture = BoardRect(pictureLeft, top, pictureLeft + w, top + h),
                icon = BoardRect(iconLeft, iconTop, iconLeft + px(CARD_ICON), iconTop + px(CARD_ICON)),
                iconRadius = px(CARD_ICON_RADIUS),
                nameBaseline = nameBaseline,
                taglineBaseline = nameBaseline + px(CARD_TAGLINE_BASELINE),
                badge = BoardRect(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeH),
                slide = px(CARD_SLIDE),
            )
        }
    }
}
