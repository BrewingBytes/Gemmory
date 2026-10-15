package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.i64
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntitlementsTest {
    private val clock = FakeClock()
    private val store = CodecStore(EntitlementCodec::encode) { (EntitlementCodec.decode(it) as? Decoded.Ok)?.value }

    private fun cache() = EntitlementCache({ clock.now }, store)

    private fun bought(vararg products: String, state: PurchaseState = PurchaseState.PURCHASED) = Purchase(products.toList(), state)

    private val weekly = bought(Products.VIP_WEEKLY)
    private val yearly = bought(Products.VIP_YEARLY)
    private val removeAds = bought(Products.REMOVE_ADS)

    private fun advanceDays(days: Double) {
        clock.now += (days * 24 * 60 * 60_000).roundToLong()
    }

    @Test
    fun derivationTable() {
        data class Row(val name: String, val purchases: List<Purchase>, val expected: Entitlements)
        val rows = listOf(
            Row("nothing", emptyList(), Entitlements.FREE),
            Row("weekly", listOf(weekly), Entitlements(VipPlan.WEEKLY)),
            Row("yearly", listOf(yearly), Entitlements(VipPlan.YEARLY)),
            Row("remove_ads", listOf(removeAds), Entitlements(removeAds = true)),
            Row("VIP + remove_ads", listOf(weekly, removeAds), Entitlements(VipPlan.WEEKLY, removeAds = true)),
            Row("both plans (switching) read as yearly", listOf(weekly, yearly), Entitlements(VipPlan.YEARLY)),
            Row("pending VIP grants nothing", listOf(bought(Products.VIP_YEARLY, state = PurchaseState.PENDING)), Entitlements.FREE),
            Row("pending remove_ads grants nothing", listOf(bought(Products.REMOVE_ADS, state = PurchaseState.PENDING)), Entitlements.FREE),
            Row("unspecified grants nothing", listOf(bought(Products.VIP_WEEKLY, state = PurchaseState.UNSPECIFIED)), Entitlements.FREE),
            Row("one purchase, several products", listOf(bought(Products.VIP_WEEKLY, Products.REMOVE_ADS)), Entitlements(VipPlan.WEEKLY, removeAds = true)),
            Row("unknown products are ignored", listOf(bought("coins_100")), Entitlements.FREE),
        )
        for (r in rows) assertEquals(r.expected, Entitlements.from(r.purchases), r.name)
    }

    @Test
    fun vipAndRemoveAdsBothRemoveAds() {
        assertTrue(Entitlements(VipPlan.WEEKLY).adsRemoved)
        assertTrue(Entitlements(removeAds = true).adsRemoved)
        assertFalse(Entitlements(removeAds = true).vip)
        assertFalse(Entitlements.FREE.adsRemoved)
    }

    @Test
    fun vipLastsSixDaysOfflineAndFallsBackOnDaySeven() {
        cache().onVerified(listOf(yearly))
        advanceDays(6.0)
        assertEquals(Entitlements(VipPlan.YEARLY), cache().entitlements(), "day 6 keeps VIP")
        assertEquals(Membership.VIP_YEARLY, cache().membership())
        advanceDays(0.99)
        assertTrue(cache().entitlements().vip, "just before 7 days")
        advanceDays(0.01)
        assertEquals(Entitlements.FREE, cache().entitlements(), "day 7 falls back to Free")
        assertEquals(Membership.VIP_UNCONFIRMED, cache().membership())
    }

    @Test
    fun aSuccessfulQueryRestoresVipAndAFailedOneChangesNothing() {
        val c = cache()
        c.onVerified(listOf(weekly))
        advanceDays(8.0)
        assertFalse(c.entitlements().vip)
        // A failed query never calls onVerified: still lapsed.
        assertFalse(cache().entitlements().vip)
        c.onVerified(listOf(weekly))
        assertEquals(Entitlements(VipPlan.WEEKLY), c.entitlements())
        assertEquals(Membership.VIP_WEEKLY, c.membership())
    }

    @Test
    fun aSuccessfulQueryAlwaysOverwritesDowngradesIncluded() {
        val c = cache()
        c.onVerified(listOf(yearly, removeAds))
        c.onVerified(listOf(removeAds))
        assertEquals(Entitlements(removeAds = true), c.entitlements(), "VIP cancelled")
        assertEquals(Membership.ADS_REMOVED, c.membership())
        c.onVerified(emptyList())
        assertEquals(Entitlements.FREE, c.entitlements(), "remove_ads refunded")
        assertEquals(Membership.FREE, c.membership())
    }

    @Test
    fun aClockMovedBackwardDoesNotExtendTheWindow() {
        cache().onVerified(listOf(yearly))
        advanceDays(5.0)
        assertTrue(cache().entitlements().vip)
        advanceDays(-30.0)
        assertTrue(cache().entitlements().vip, "moving back adds no time")
        advanceDays(1.99)
        assertTrue(cache().entitlements().vip, "6.99 days of real time")
        advanceDays(0.01)
        assertFalse(cache().entitlements().vip, "7 days of real time, whatever the clock says")
    }

    @Test
    fun aClockMovedForwardShortensTheWindow() {
        cache().onVerified(listOf(yearly))
        advanceDays(10.0)
        assertFalse(cache().entitlements().vip)
        advanceDays(-10.0)
        assertFalse(cache().entitlements().vip, "moving back again doesn't give the days back")
    }

    @Test
    fun removeAdsNeverExpiresOffline() {
        cache().onVerified(listOf(removeAds, weekly))
        advanceDays(400.0)
        assertEquals(Entitlements(removeAds = true), cache().entitlements())
        assertTrue(cache().entitlements().adsRemoved)
        assertEquals(Membership.VIP_UNCONFIRMED, cache().membership(), "the lapsed VIP is what Membership names")
    }

    @Test
    fun aPendingPurchaseIsNotCachedAsOwned() {
        cache().onVerified(listOf(bought(Products.VIP_YEARLY, state = PurchaseState.PENDING)))
        assertEquals(Entitlements.FREE, cache().entitlements())
        assertEquals(Membership.FREE, cache().membership())
    }

    @Test
    fun aFinishedPurchaseCountsAtOnceAndKeepsWhatWasKnown() {
        val c = cache()
        c.onVerified(listOf(removeAds))
        assertEquals(Entitlements(VipPlan.WEEKLY, removeAds = true), c.onPurchased(listOf(weekly)), "remove_ads is kept")
        advanceDays(6.0)
        c.onPurchased(listOf(bought(Products.VIP_YEARLY, state = PurchaseState.PENDING)))
        assertEquals(Entitlements(VipPlan.WEEKLY, removeAds = true), c.entitlements(), "a pending purchase changes nothing")
        advanceDays(1.0)
        assertFalse(c.entitlements().vip)
        assertTrue(c.entitlements().removeAds)
    }

    @Test
    fun buyingRemoveAdsDoesNotReviveALapsedVip() {
        val c = cache()
        c.onVerified(listOf(weekly))
        advanceDays(8.0)
        assertEquals(Entitlements(removeAds = true), c.onPurchased(listOf(removeAds)))
        assertEquals(Membership.VIP_UNCONFIRMED, c.membership())
        assertEquals(Entitlements(VipPlan.YEARLY, removeAds = true), c.onPurchased(listOf(yearly)), "a new plan starts a new window")
        assertEquals(Membership.VIP_YEARLY, c.membership())
    }

    @Test
    fun aFreshInstallIsFree() {
        assertEquals(Entitlements.FREE, cache().entitlements())
        assertEquals(Membership.FREE, cache().membership())
        assertFalse(cache().lapseNoticeDue())
    }

    @Test
    fun theLapseLineShowsOnceAndAgainAfterTheNextLapse() {
        cache().onVerified(listOf(weekly))
        advanceDays(6.0)
        assertFalse(cache().lapseNoticeDue(), "not lapsed yet")
        advanceDays(1.0)
        assertTrue(cache().lapseNoticeDue())
        assertTrue(cache().lapseNoticeDue(), "until the gallery shows it")
        cache().onLapseNoticeShown()
        assertFalse(cache().lapseNoticeDue())
        advanceDays(3.0)
        assertFalse(cache().lapseNoticeDue(), "once per lapse")
        cache().onVerified(listOf(weekly))
        advanceDays(7.0)
        assertTrue(cache().lapseNoticeDue(), "a later lapse shows it again")
    }

    @Test
    fun aRemoveAdsOnlyPlayerNeverSeesTheLapseLine() {
        cache().onVerified(listOf(removeAds))
        advanceDays(30.0)
        assertFalse(cache().lapseNoticeDue())
        cache().onLapseNoticeShown()
        assertEquals(EntitlementRecord(Entitlements(removeAds = true), 30L * 24 * 60 * 60_000, clock.now), store.load())
    }

    @Test
    fun nothingIsWrittenWhenNothingChanges() {
        val c = cache()
        c.onVerified(listOf(yearly))
        val saves = store.saves
        repeat(3) {
            c.entitlements()
            c.membership()
        }
        assertEquals(saves, store.saves)
    }

    @Test
    fun codecRoundTrips() {
        val records = listOf(
            EntitlementRecord(),
            EntitlementRecord(Entitlements(VipPlan.WEEKLY), unverifiedMs = 5, lastSeenAt = -7),
            EntitlementRecord(Entitlements(VipPlan.YEARLY, removeAds = true), 123_456_789, clock.now, lapseNoticeShown = true),
            EntitlementRecord(Entitlements(removeAds = true), Long.MAX_VALUE, Long.MIN_VALUE),
        )
        for (r in records) assertEquals(r, assertIs<Decoded.Ok<EntitlementRecord>>(EntitlementCodec.decode(EntitlementCodec.encode(r))).value)
        assertEquals("GMRE", EntitlementCodec.encode(EntitlementRecord()).copyOf(4).decodeToString())
    }

    @Test
    fun codecRefusesDamagedForeignAndNewerFiles() {
        val bytes = EntitlementCodec.encode(EntitlementRecord(Entitlements(VipPlan.YEARLY), 1, 2))
        val flipped = bytes.copyOf().also { it[7] = (it[7].toInt() xor 1).toByte() }
        assertEquals(Decoded.Refused(Refusal.BadCrc), EntitlementCodec.decode(flipped))
        assertEquals(Decoded.Refused(Refusal.TooShort), EntitlementCodec.decode(bytes.copyOf(5)))
        assertEquals(Decoded.Refused(Refusal.BadMagic), EntitlementCodec.decode(AdPolicyCodec.encode(AdPolicyState())))
        val body = bytes.copyOfRange(Envelope.HEADER_SIZE, bytes.size - Envelope.TRAILER_SIZE)
        assertEquals(Decoded.Refused(Refusal.NewerVersion(2)), EntitlementCodec.decode(Envelope.wrap(EntitlementCodec.MAGIC, 2, body)))
        assertEquals(Decoded.Refused(Refusal.NoMigration(0)), EntitlementCodec.decode(Envelope.wrap(EntitlementCodec.MAGIC, 0, body)))
    }

    @Test
    fun codecRefusesImpossibleValues() {
        fun body(flags: Int, plan: Int, unverified: Long, extra: Int? = null): ByteArray {
            val w = ByteWriter()
            w.u8(flags)
            w.u8(plan)
            w.i64(unverified)
            w.i64(0)
            extra?.let(w::u8)
            return Envelope.wrap(EntitlementCodec.MAGIC, EntitlementCodec.VERSION, w.toByteArray())
        }
        assertIs<Decoded.Ok<EntitlementRecord>>(EntitlementCodec.decode(body(3, 2, 0)))
        for ((name, bytes) in listOf(
            "unknown flag" to body(4, 0, 0),
            "unknown plan" to body(0, 3, 0),
            "negative time" to body(0, 1, -1),
            "trailing byte" to body(0, 1, 0, extra = 0),
        )) {
            assertEquals(Decoded.Refused(Refusal.Malformed), EntitlementCodec.decode(bytes), name)
        }
    }

    @Test
    fun anUnreadableCacheStartsFree() {
        store.bytes = byteArrayOf(1, 2, 3)
        assertEquals(Entitlements.FREE, cache().entitlements())
    }
}
