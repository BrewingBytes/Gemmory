package com.brewingbytes.gemmory.gallery

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.WheelPrize
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #102, E7: the wheel's freeze slice reads "5 fills" only at 2 held, and never changes during a spin. */
class FreezeLabelTest {
    /** R1: a freeze won at 1 held keeps "Streak freeze" on the slice until the wheel lands. */
    @Test
    fun aFreezeWonMidSpinKeepsItsLabelUntilTheWheelLands() {
        val label = FreezeLabel(full = false)
        label.spinStarted(nowFull = false)
        // The prize is added before the wheel turns: the inventory now holds 2.
        label.held(nowFull = true)
        assertFalse(label.full, "mid-spin the slice still reads Streak freeze")
        assertEquals("Streak freeze 10%", "${WheelPrize.STREAK_FREEZE.label(XmlStrings.en, label.full)} 10%")
        label.spinEnded(nowFull = true)
        assertTrue(label.full, "landed: the next spin's slice reads 5 fills")
        assertEquals("5 fills", WheelPrize.STREAK_FREEZE.label(XmlStrings.en, label.full))
    }

    @Test
    fun betweenSpinsTheLabelFollowsTheFreezesHeld() {
        val label = FreezeLabel(full = true)
        label.held(nowFull = false)
        assertFalse(label.full)
        label.held(nowFull = true)
        assertTrue(label.full)
    }

    @Test
    fun theResultLineNamesWhatWasPaid() {
        assertEquals("A streak freeze added to your inventory.", Spin(WheelPrize.STREAK_FREEZE, Item.STREAK_FREEZE, 1).result(XmlStrings.en))
        assertEquals("5 fills added to your inventory.", Spin(WheelPrize.STREAK_FREEZE, Item.FILLS, 5).result(XmlStrings.en))
        assertEquals("1 photo canvas added to your inventory.", Spin(WheelPrize.PHOTO_CANVAS, Item.PHOTO_CANVAS, 1).result(XmlStrings.en))
    }
}
