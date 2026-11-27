package com.brewingbytes.gemmory.policy

/**
 * Who sees the 1.0.7 opt-in ad spots, and the ids that make each reward once (ad-spots.md: CEO D2-D3, eng D1, design
 * DR-D3..DR-D8). Pure rules; the screens and the rewarded flow ask them. Every spot also needs its own video already
 * loaded (CEO D2), which the caller checks. VIP sees none (no inventory); Remove-ads players see all three.
 */
object SpotRules {
    /** The completion bonus's and the rewarded interstitial's reward, as a [Spin] for the shared result line. */
    val BONUS_FILLS: Spin get() = Spin(BonusFill, Item.FILLS, Inventory.FILLS_PER_BONUS)

    private object BonusFill : WheelSlice {
        override val item = Item.FILLS
        override val amount = Inventory.FILLS_PER_BONUS
        override val percent = 100
    }

    /** The completion bonus starts at this many finished pictures (DR-D3: the first finish stays clean, D8). */
    const val BONUS_FROM_FINISH = 2

    /** The bonus pays once per canvas, ever (office hours: Play again / Start over can't farm it). */
    fun bonusId(canvasId: String): String = "bonus-$canvasId"

    /** Today's gift pays once a local day. */
    fun giftId(day: Long): String = "gift-$day"

    /** Today's gift popup opened (or was dismissed) on [day]: once a day (DR-D5). */
    fun giftPopupId(day: Long): String = "gift-popup-$day"

    /** "Same again" pays once per 5-day spin, [spinIndex] being the spins used once it landed. */
    fun againId(spinIndex: Int): String = "again-$spinIndex"

    /**
     * The completion bonus offer may be laid out: a fresh completion (not a reopened still), from the 2nd finish,
     * this canvas never paid, not VIP, ads on.
     */
    fun bonusAllowed(fresh: Boolean, finishes: Int, claimed: Boolean, vip: Boolean, adsOn: Boolean): Boolean =
        fresh && finishes >= BONUS_FROM_FINISH && !claimed && !vip && adsOn

    /** Today's gift may be offered: a picture finished once (DR-D6), today's not opened, not VIP, ads on. */
    fun giftAllowed(everFinished: Boolean, openedToday: Boolean, vip: Boolean, adsOn: Boolean): Boolean =
        everFinished && !openedToday && !vip && adsOn

    /** Today's popup may open: the gift is allowed and the popup hasn't shown today (DR-D5, DR-D8 checks the screen). */
    fun giftPopupDue(giftAllowed: Boolean, popupShownToday: Boolean): Boolean = giftAllowed && !popupShownToday
}
