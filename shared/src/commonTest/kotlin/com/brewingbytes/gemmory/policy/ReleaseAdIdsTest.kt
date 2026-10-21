package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The release-build ID gate (T34): real IDs pass; missing, blank, test-publisher and malformed IDs fail. */
class ReleaseAdIdsTest {
    private val app = "ca-app-pub-1234567890123456~1234567890"
    private val rewarded = "ca-app-pub-1234567890123456/1111111111"
    private val interstitial = "ca-app-pub-1234567890123456/2222222222"

    @Test
    fun realIdsPass() {
        assertEquals(emptyList(), ReleaseAdIds.problems(app, rewarded, interstitial))
    }

    @Test
    fun missingAndBlankIdsFail() {
        assertEquals(
            listOf("gemmory.admob.appId is missing", "gemmory.admob.rewardedUnit is missing", "gemmory.admob.interstitialUnit is missing"),
            ReleaseAdIds.problems(null, "", "  "),
        )
    }

    /** R1: the IDs v0.7.0 shipped with (Google's samples) can't reach a release again. */
    @Test
    fun googleTestIdsFail() {
        val problems = ReleaseAdIds.problems(
            "ca-app-pub-3940256099942544~3347511713",
            "ca-app-pub-3940256099942544/5224354917",
            "ca-app-pub-3940256099942544/1033173712",
        )
        assertEquals(3, problems.size)
        assertTrue(problems.all { "test publisher" in it })
    }

    @Test
    fun oneTestUnitAmongRealIdsFails() {
        assertEquals(1, ReleaseAdIds.problems(app, rewarded, "ca-app-pub-3940256099942544/1033173712").size)
    }

    @Test
    fun malformedIdsFail() {
        // A unit ID where the app ID goes and the other way round, a placeholder, a stray space.
        val problems = ReleaseAdIds.problems(rewarded, app, "$interstitial ")
        assertEquals(3, problems.size)
        assertTrue(problems.all { "not an AdMob ID" in it })
        assertEquals(1, ReleaseAdIds.problems(app, "ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN", interstitial).size)
    }
}
