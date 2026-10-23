package com.brewingbytes.gemmory.ads

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.photo.allowanceLine
import com.brewingbytes.gemmory.policy.Allowance
import org.junit.Test
import kotlin.test.assertEquals

/** The Preview allowance line and the gallery note, exactly as in the design system (Allowance line states, D5). */
class AllowanceCopyTest {
    @Test
    fun everyLineState() {
        assertEquals("Uses today's free photo canvas.", allowanceLine(XmlStrings.en, Allowance.Free))
        assertEquals("Today's free one is used. Watch a video for another.", allowanceLine(XmlStrings.en, Allowance.WatchVideo))
        assertEquals("Uses a bonus canvas from a video.", allowanceLine(XmlStrings.en, Allowance.Bonus))
        assertEquals("Uses a kept photo canvas (2 left).", allowanceLine(XmlStrings.en, Allowance.Bonus, kept = 2), "#38")
        assertEquals(null, allowanceLine(XmlStrings.en, Allowance.Unlimited), "VIP: no line")
    }
}
