package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Refusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdPolicyTest {
    private val clock = FakeClock()
    private val store = CodecStore(AdPolicyCodec::encode) { (AdPolicyCodec.decode(it) as? Decoded.Ok)?.value }
    private val leave = InterstitialContext(AdMoment.LEAVE_FRESH_COMPLETION, Entitlements.FREE)

    private fun policy() = AdPolicy(clock.day, store)

    /** Second launch and two finished pictures: every rule but the one under test is satisfied. */
    private fun eligible(): AdPolicy {
        policy().onProcessStart()
        return policy().apply {
            onProcessStart()
            onPictureFinished()
            onPictureFinished()
        }
    }

    private class Row(val name: String, val expected: AdBlock?, val ctx: InterstitialContext? = null, val setup: AdPolicy.(FakeClock) -> Unit = {})

    /** Every Ad rules line (R3-9, CEO D6, eng E5/D6), from an otherwise eligible state. */
    @Test
    fun everyRule() {
        val rows = listOf(
            Row("eligible: leaving a fresh completion", null),
            Row("leaving a completion still", AdBlock.WRONG_MOMENT, leave.copy(moment = AdMoment.LEAVE_COMPLETION_STILL)),
            Row("on the play canvas", AdBlock.WRONG_MOMENT, leave.copy(moment = AdMoment.PLAY_CANVAS)),
            Row("before or during share", AdBlock.WRONG_MOMENT, leave.copy(moment = AdMoment.SHARE)),
            Row("leaving while the video is still being made", AdBlock.SHARING, leave.copy(sharing = true)),
            Row("VIP", AdBlock.ADS_REMOVED, leave.copy(entitlements = Entitlements(VipPlan.YEARLY))),
            Row("remove-ads", AdBlock.ADS_REMOVED, leave.copy(entitlements = Entitlements(removeAds = true))),
            Row("9:59 after the last one", AdBlock.TOO_SOON) { c -> onInterstitialShown(); c.now += 599_000 },
            Row("10:00 after the last one", null) { c -> onInterstitialShown(); c.advanceMinutes(10) },
            Row("1:59 after a rewarded ad", AdBlock.AFTER_REWARDED) { c -> onRewardedShown(); c.now += 119_000 },
            Row("2:00 after a rewarded ad", null) { c -> onRewardedShown(); c.advanceMinutes(2) },
            Row("4 today", AdBlock.DAILY_CAP) { c -> repeat(4) { onInterstitialShown(); c.advanceMinutes(10) } },
            Row("3 today", null) { c -> repeat(3) { onInterstitialShown(); c.advanceMinutes(10) } },
            Row("4 yesterday, after local midnight", null) { c ->
                c.set("2026-10-14T23:00")
                repeat(4) { onInterstitialShown(); c.advanceMinutes(10) }
                c.set("2026-10-15T00:10")
            },
            Row("4 on the DST fall-back day, 23:59 local", AdBlock.DAILY_CAP) { c ->
                c.set("2026-10-25T00:30")
                repeat(4) { onInterstitialShown(); c.advanceMinutes(10) }
                c.set("2026-10-25T23:59")
            },
        )
        for (row in rows) {
            store.bytes = null
            clock.set("2026-10-14T10:00")
            val p = eligible()
            row.setup(p, clock)
            assertEquals(row.expected, p.blockFor(row.ctx ?: leave), row.name)
        }
    }

    /** The first session (D6) and the 2nd-finish rule, from a fresh install. */
    @Test
    fun firstSessionAndSecondFinish() {
        val p = policy()
        p.onProcessStart()
        p.onPictureFinished()
        p.onPictureFinished()
        assertEquals(AdBlock.FIRST_SESSION, p.blockFor(leave), "first launch, two finishes")

        p.onBackground()
        clock.advanceMinutes(29)
        p.onForeground()
        assertEquals(AdBlock.FIRST_SESSION, p.blockFor(leave), "29 min in the background")

        p.onBackground()
        clock.advanceMinutes(30)
        p.onForeground()
        assertNull(p.blockFor(leave), "30 min in the background ends it")
    }

    @Test
    fun aLaterColdStartEndsTheFirstSession() {
        policy().onProcessStart()
        val p = policy()
        assertEquals(AdBlock.FIRST_SESSION, p.blockFor(leave), "same process, nothing new")
        p.onProcessStart()
        assertEquals(AdBlock.BEFORE_SECOND_FINISH, p.blockFor(leave), "cold start: only the finish rule left")
        p.onPictureFinished()
        assertEquals(AdBlock.BEFORE_SECOND_FINISH, p.blockFor(leave), "one finish")
        p.onPictureFinished()
        assertNull(p.blockFor(leave), "second finish")
        assertTrue(p.canShowInterstitial(leave))
    }

    @Test
    fun aForegroundWithoutABackgroundChangesNothing() {
        val p = policy()
        p.onProcessStart()
        clock.advanceMinutes(60)
        p.onForeground()
        assertEquals(AdBlock.FIRST_SESSION, p.blockFor(leave))
    }

    @Test
    fun aClockMovedBackBlocksOnlyForTheGapFromNow() {
        val p = eligible()
        p.onInterstitialShown()
        p.onRewardedShown()
        clock.now -= 3 * 24 * 3_600_000L
        assertEquals(AdBlock.TOO_SOON, p.blockFor(leave), "times in the future are pulled back to now")
        clock.advanceMinutes(10)
        assertNull(p.blockFor(leave), "not three days")
    }

    @Test
    fun stateSurvivesARestart() {
        val p = eligible()
        p.onInterstitialShown()
        clock.advanceMinutes(5)
        assertEquals(AdBlock.TOO_SOON, policy().blockFor(leave), "a new process reads the last show")
    }

    @Test
    fun codecRoundTripsAndRefusesDamage() {
        val state = AdPolicyState(
            launchedBefore = true,
            firstSessionOver = true,
            finishes = 7,
            lastInterstitialAt = 1_760_000_000_000,
            lastRewardedAt = null,
            interstitialDay = 20_740,
            interstitialsToday = 3,
        )
        val bytes = AdPolicyCodec.encode(state)
        assertEquals(state, assertIs<Decoded.Ok<AdPolicyState>>(AdPolicyCodec.decode(bytes)).value)
        assertEquals(AdPolicyState(), assertIs<Decoded.Ok<AdPolicyState>>(AdPolicyCodec.decode(AdPolicyCodec.encode(AdPolicyState()))).value)
        val flipped = bytes.copyOf().also { it[7] = (it[7].toInt() xor 4).toByte() }
        assertEquals(Decoded.Refused(Refusal.BadCrc), AdPolicyCodec.decode(flipped))
        assertEquals(Decoded.Refused(Refusal.BadMagic), AdPolicyCodec.decode(LedgerCodec.encode(LedgerState(0))))
    }

    @Test
    fun nothingIsWrittenWhenNothingChanges() {
        val p = eligible()
        val saves = store.saves
        repeat(3) { p.blockFor(leave) }
        assertEquals(saves, store.saves)
        assertFalse(p.canShowInterstitial(leave.copy(sharing = true)))
    }
}
