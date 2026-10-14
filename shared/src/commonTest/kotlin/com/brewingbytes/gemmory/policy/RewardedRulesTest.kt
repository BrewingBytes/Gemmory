package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals

class RewardedRulesTest {
    private data class Row(val failure: LoadFailure, val online: Boolean, val sdkReady: Boolean, val grace: Boolean, val expected: Unavailable)

    /** CEO D7 + eng D15: grace only online, SDK up, and no-fill or timeout; offline never grants grace. */
    @Test
    fun onlineAndOfflineRows() {
        val rows = listOf(
            Row(LoadFailure.NO_FILL, online = true, sdkReady = true, grace = true, Unavailable.GRACE_OFFER),
            Row(LoadFailure.TIMEOUT, online = true, sdkReady = true, grace = true, Unavailable.GRACE_OFFER),
            Row(LoadFailure.NO_FILL, online = true, sdkReady = true, grace = false, Unavailable.NO_VIDEO),
            Row(LoadFailure.TIMEOUT, online = true, sdkReady = true, grace = false, Unavailable.NO_VIDEO),
            Row(LoadFailure.OTHER, online = true, sdkReady = true, grace = true, Unavailable.NO_VIDEO),
            Row(LoadFailure.NETWORK, online = true, sdkReady = true, grace = true, Unavailable.NO_VIDEO),
            Row(LoadFailure.NO_FILL, online = true, sdkReady = false, grace = true, Unavailable.NO_VIDEO),
            Row(LoadFailure.TIMEOUT, online = true, sdkReady = false, grace = true, Unavailable.NO_VIDEO),
            Row(LoadFailure.NO_FILL, online = false, sdkReady = true, grace = true, Unavailable.OFFLINE),
            Row(LoadFailure.TIMEOUT, online = false, sdkReady = true, grace = true, Unavailable.OFFLINE),
            Row(LoadFailure.NETWORK, online = false, sdkReady = false, grace = true, Unavailable.OFFLINE),
            Row(LoadFailure.OTHER, online = false, sdkReady = true, grace = false, Unavailable.OFFLINE),
        )
        for (r in rows) assertEquals(r.expected, RewardedRules.unavailable(r.failure, r.online, r.sdkReady, r.grace), r.toString())
    }

    @Test
    fun consentModeFromTcf() {
        assertEquals(ConsentModeV2.GRANTED, ConsentModeV2.fromTcf(gdprApplies = false, purposeConsents = null), "outside GDPR")
        assertEquals(ConsentModeV2.GRANTED, ConsentModeV2.fromTcf(true, "1111111111"), "all purposes")
        assertEquals(ConsentModeV2(false, false, false, false), ConsentModeV2.fromTcf(true, "0000000000"), "rejected all")
        assertEquals(ConsentModeV2(false, false, false, false), ConsentModeV2.fromTcf(null, null), "not decided yet")
        assertEquals(ConsentModeV2(true, true, false, false), ConsentModeV2.fromTcf(true, "1"), "storage only")
        assertEquals(ConsentModeV2(true, true, true, false), ConsentModeV2.fromTcf(true, "1000001"), "purposes 1 + 7")
        assertEquals(ConsentModeV2(false, false, false, true), ConsentModeV2.fromTcf(true, "0011"), "purposes 3 + 4")
    }
}
