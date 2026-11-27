package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotRulesTest {
    @Test
    fun theBonusWaitsForTheSecondFreshFinish() {
        assertFalse(SpotRules.bonusAllowed(fresh = true, finishes = 1, claimed = false, vip = false, adsOn = true), "first finish stays clean (D8)")
        assertTrue(SpotRules.bonusAllowed(fresh = true, finishes = 2, claimed = false, vip = false, adsOn = true))
        assertFalse(SpotRules.bonusAllowed(fresh = false, finishes = 5, claimed = false, vip = false, adsOn = true), "a reopened still")
        assertFalse(SpotRules.bonusAllowed(fresh = true, finishes = 5, claimed = true, vip = false, adsOn = true), "once per canvas")
        assertFalse(SpotRules.bonusAllowed(fresh = true, finishes = 5, claimed = false, vip = true, adsOn = true), "VIP sees none")
        assertFalse(SpotRules.bonusAllowed(fresh = true, finishes = 5, claimed = false, vip = false, adsOn = false), "ads off")
    }

    @Test
    fun todaysGiftAfterTheFirstFinishOnceADay() {
        assertFalse(SpotRules.giftAllowed(everFinished = false, openedToday = false, vip = false, adsOn = true), "DR-D6")
        assertTrue(SpotRules.giftAllowed(everFinished = true, openedToday = false, vip = false, adsOn = true))
        assertFalse(SpotRules.giftAllowed(everFinished = true, openedToday = true, vip = false, adsOn = true))
        assertFalse(SpotRules.giftAllowed(everFinished = true, openedToday = false, vip = true, adsOn = true))
        assertTrue(SpotRules.giftPopupDue(giftAllowed = true, popupShownToday = false))
        assertFalse(SpotRules.giftPopupDue(giftAllowed = true, popupShownToday = true), "dismissed stays dismissed today")
        assertFalse(SpotRules.giftPopupDue(giftAllowed = false, popupShownToday = false))
    }

    @Test
    fun idsAreDistinctPerThing() {
        assertEquals(setOf("bonus-a", "gift-3", "gift-popup-3", "again-7"), setOf(SpotRules.bonusId("a"), SpotRules.giftId(3), SpotRules.giftPopupId(3), SpotRules.againId(7)))
    }

    @Test
    fun theNewSpotsHaveNoGraceAndTheirPlacements() {
        assertEquals(listOf(true, true, true, true, true, false, false, false), RewardType.entries.map { it.hasGrace })
        assertEquals(
            listOf("photo_canvas", "locked_picture", "inventory_fills", "colors", "streak_repair", "completion_bonus", "daily_chest", "double_wheel"),
            RewardType.entries.map { it.placement },
        )
    }
}
