package com.brewingbytes.gemmory.ads

import com.brewingbytes.gemmory.policy.ReleaseAdIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T34: debug builds (and so connected tests) only ever carry Google's test units; a release check would reject them. */
class AdUnitsTest {
    @Test
    fun debugBuildsUseGoogleTestUnits() {
        assertEquals("ca-app-pub-3940256099942544/5224354917", AdUnits.REWARDED)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdUnits.INTERSTITIAL)
        assertTrue(ReleaseAdIds.problems("x", AdUnits.REWARDED, AdUnits.INTERSTITIAL).drop(1).all { "test publisher" in it })
    }
}
