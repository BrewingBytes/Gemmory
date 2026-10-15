package com.brewingbytes.gemmory.billing

import android.app.Activity
import com.brewingbytes.gemmory.policy.EntitlementCache
import com.brewingbytes.gemmory.policy.EntitlementCodec
import com.brewingbytes.gemmory.policy.EntitlementRecord
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.PurchaseState
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.VipPlan
import com.brewingbytes.gemmory.save.Decoded
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepositoryTest {
    private var now = 1_760_000_000_000L
    private val day = 24 * 60 * 60_000L

    /** A Play client the test scripts call by call, recording what the repository asked. */
    private class FakeClient : BillingBackend {
        val calls = mutableListOf<String>()
        var connectCode = BillingCode.OK

        /** Codes the next queries answer with before the purchases (DISCONNECTED once = the service dropped). */
        val queryCodes = ArrayDeque<BillingCode>()
        var owned = listOf<StorePurchase>()
        var prices: Outcome<Prices> = Outcome.Ok(PRICES)
        var launchCode = BillingCode.OK
        val acknowledged = mutableListOf<String>()
        var ackCode = BillingCode.OK
        override val updates = MutableSharedFlow<PurchaseUpdate>()

        override suspend fun connect(): BillingCode {
            calls += "connect"
            return connectCode
        }

        override suspend fun queryPurchases(): Outcome<List<StorePurchase>> {
            calls += "query"
            val code = queryCodes.removeFirstOrNull() ?: BillingCode.OK
            return if (code == BillingCode.OK) Outcome.Ok(owned) else Outcome.Failed(code)
        }

        override suspend fun queryPrices(): Outcome<Prices> {
            calls += "prices"
            return prices
        }

        override suspend fun acknowledge(token: String): BillingCode {
            calls += "ack $token"
            if (ackCode == BillingCode.OK) {
                acknowledged += token
                owned = owned.map { if (it.token == token) it.copy(acknowledged = true) else it }
            }
            return ackCode
        }

        override fun launchPurchase(activity: Activity, product: String): BillingCode {
            calls += "launch $product"
            return launchCode
        }
    }

    private class MemoryStore : StateStore<EntitlementRecord> {
        var bytes: ByteArray? = null

        override fun load() = bytes?.let { (EntitlementCodec.decode(it) as? Decoded.Ok)?.value }

        override fun save(value: EntitlementRecord) {
            bytes = EntitlementCodec.encode(value)
        }
    }

    private val client = FakeClient()
    private val store = MemoryStore()
    private val reports = mutableListOf<Throwable>()

    private fun TestScope.repository() = BillingRepository(
        backend = client,
        cache = EntitlementCache({ now }, store),
        scope = backgroundScope,
        worker = UnconfinedTestDispatcher(testScheduler),
        reporter = { reports += it },
    )

    private fun purchase(product: String, state: PurchaseState = PurchaseState.PURCHASED, acknowledged: Boolean = true, token: String = "t-$product") =
        StorePurchase(listOf(product), state, token, acknowledged)

    @Test
    fun everyForegroundQueriesPlayAndTheFirstIsTheColdStart() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        assertTrue(client.calls.isEmpty(), "nothing before the first foreground")
        client.owned = listOf(purchase(Products.VIP_YEARLY))
        r.onForeground()
        assertEquals(listOf("connect", "query"), client.calls)
        assertEquals(Entitlements(VipPlan.YEARLY), r.entitlements.value)
        assertEquals(Membership.VIP_YEARLY, r.membership.value)
        client.owned = emptyList()
        r.onForeground()
        assertEquals(Entitlements.FREE, r.entitlements.value, "resume: a cancelled VIP is gone")
    }

    @Test
    fun aDroppedServiceReconnectsAndRetriesOnce() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        client.owned = listOf(purchase(Products.REMOVE_ADS))
        client.queryCodes += BillingCode.DISCONNECTED
        assertIs<Outcome.Ok<Entitlements>>(r.refresh())
        assertEquals(listOf("connect", "query", "connect", "query"), client.calls)
        assertTrue(r.entitlements.value.adsRemoved)

        client.calls.clear()
        client.queryCodes += listOf(BillingCode.DISCONNECTED, BillingCode.DISCONNECTED)
        assertEquals(Outcome.Failed(BillingCode.DISCONNECTED), r.refresh(), "only once")
        assertEquals(listOf("connect", "query", "connect", "query"), client.calls)
    }

    @Test
    fun noConnectionMeansNoQueryAndTheCacheStays() = runTest(UnconfinedTestDispatcher()) {
        client.owned = listOf(purchase(Products.VIP_WEEKLY))
        repository().refresh()
        client.connectCode = BillingCode.UNAVAILABLE
        val r = repository()
        assertEquals(Outcome.Failed(BillingCode.UNAVAILABLE), r.refresh())
        assertEquals(listOf("connect", "query", "connect"), client.calls)
        assertEquals(Entitlements(VipPlan.WEEKLY), r.entitlements.value, "last known, from the cache file")
    }

    @Test
    fun everyUnacknowledgedPurchaseIsAcknowledgedAndAFailureIsRetriedNextQuery() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        client.owned = listOf(
            purchase(Products.VIP_YEARLY, acknowledged = false, token = "a"),
            purchase(Products.REMOVE_ADS, acknowledged = true, token = "b"),
            purchase(Products.VIP_WEEKLY, state = PurchaseState.PENDING, acknowledged = false, token = "c"),
        )
        client.ackCode = BillingCode.NETWORK
        r.refresh()
        assertEquals(1, reports.size, "a failed acknowledge is reported")
        assertTrue(client.acknowledged.isEmpty())
        client.ackCode = BillingCode.OK
        r.refresh()
        assertEquals(listOf("a"), client.acknowledged, "only PURCHASED and not yet acknowledged; pending never")
    }

    @Test
    fun vipLapsesAfterSevenDaysOfflineAndTheGalleryLineShowsOnce() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        client.owned = listOf(purchase(Products.VIP_WEEKLY))
        r.refresh()
        client.queryCodes += List(3) { BillingCode.NETWORK }
        now += 6 * day
        r.refresh()
        assertEquals(Entitlements(VipPlan.WEEKLY), r.entitlements.value, "day 6")
        assertNull(r.notice.value)
        now += day
        r.refresh()
        assertEquals(Entitlements.FREE, r.entitlements.value, "day 7")
        assertEquals(Membership.VIP_UNCONFIRMED, r.membership.value)
        assertEquals(Notice.VIP_LAPSED, r.notice.value)
        r.noticeShown(Notice.VIP_LAPSED)
        r.refresh()
        assertNull(r.notice.value, "once")
        assertNull(repository().notice.value, "a new process doesn't show it again")
        r.refresh()
        assertEquals(Entitlements(VipPlan.WEEKLY), r.entitlements.value, "Play answered: VIP is back")
    }

    @Test
    fun pricesLoadFailAndRetry() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        assertEquals(PricesUi.Loading, r.prices.value)
        client.prices = Outcome.Failed(BillingCode.NETWORK)
        r.loadPrices()
        assertEquals(PricesUi.Failed, r.prices.value)
        client.prices = Outcome.Ok(PRICES)
        r.loadPrices()
        assertEquals(PricesUi.Ready(PRICES), r.prices.value)
        r.loadPrices()
        assertEquals(2, client.calls.count { it == "prices" }, "loaded prices are kept, not asked again")
    }

    @Test
    fun aSuccessfulPurchaseCountsAcknowledgesAndAsksPlayAgain() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.buy(ACTIVITY, Products.VIP_YEARLY)
        assertEquals(PurchaseUi.Launching(Products.VIP_YEARLY), r.purchase.value)
        val bought = purchase(Products.VIP_YEARLY, acknowledged = false, token = "y")
        client.owned = listOf(bought)
        client.calls.clear()
        client.updates.emit(PurchaseUpdate(BillingCode.OK, listOf(bought)))
        assertEquals(listOf("ack y", "connect", "query"), client.calls, "acknowledged, then the full list")
        assertEquals(PurchaseUi.Succeeded(Products.VIP_YEARLY), r.purchase.value)
        assertEquals(Notice.VIP_STARTED, r.notice.value)
        assertEquals(Entitlements(VipPlan.YEARLY), r.entitlements.value)
        r.purchaseHandled()
        assertEquals(PurchaseUi.Idle, r.purchase.value)
    }

    @Test
    fun aPurchaseCountsEvenWhenTheFollowUpQueryFails() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        client.owned = listOf(purchase(Products.REMOVE_ADS))
        r.refresh()
        r.buy(ACTIVITY, Products.VIP_WEEKLY)
        client.queryCodes += BillingCode.NETWORK
        client.updates.emit(PurchaseUpdate(BillingCode.OK, listOf(purchase(Products.VIP_WEEKLY))))
        assertEquals(Entitlements(VipPlan.WEEKLY, removeAds = true), r.entitlements.value)
    }

    @Test
    fun removeAdsSucceedsWithItsOwnLine() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.buy(ACTIVITY, Products.REMOVE_ADS)
        client.owned = listOf(purchase(Products.REMOVE_ADS))
        client.updates.emit(PurchaseUpdate(BillingCode.OK, client.owned))
        assertEquals(Notice.ADS_OFF, r.notice.value)
        assertEquals(Membership.ADS_REMOVED, r.membership.value)
    }

    @Test
    fun cancelGoesBackWithNoMessageAndAnErrorSaysSo() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.buy(ACTIVITY, Products.VIP_WEEKLY)
        client.updates.emit(PurchaseUpdate(BillingCode.CANCELLED, emptyList()))
        assertEquals(PurchaseUi.Idle, r.purchase.value)
        r.buy(ACTIVITY, Products.VIP_WEEKLY)
        client.updates.emit(PurchaseUpdate(BillingCode.ERROR, emptyList()))
        assertEquals(PurchaseUi.Failed, r.purchase.value)
        assertEquals(Entitlements.FREE, r.entitlements.value)
        client.launchCode = BillingCode.NETWORK
        r.purchaseHandled()
        r.buy(ACTIVITY, Products.VIP_WEEKLY)
        assertEquals(PurchaseUi.Failed, r.purchase.value, "Play's sheet didn't open")
        client.launchCode = BillingCode.CANCELLED
        r.buy(ACTIVITY, Products.VIP_WEEKLY)
        assertEquals(PurchaseUi.Idle, r.purchase.value, "a new try clears the old error; cancelled shows nothing")
    }

    @Test
    fun aSecondTapWhileLaunchingStartsNothing() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.buy(ACTIVITY, Products.VIP_WEEKLY)
        r.buy(ACTIVITY, Products.VIP_YEARLY)
        assertEquals(listOf("launch vip_weekly"), client.calls)
    }

    @Test
    fun aPendingPaymentGrantsNothingUntilPlayConfirmsIt() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.buy(ACTIVITY, Products.REMOVE_ADS)
        val pending = purchase(Products.REMOVE_ADS, state = PurchaseState.PENDING, acknowledged = false)
        client.owned = listOf(pending)
        client.updates.emit(PurchaseUpdate(BillingCode.OK, listOf(pending)))
        assertEquals(PurchaseUi.Pending(Products.REMOVE_ADS), r.purchase.value)
        assertEquals(Entitlements.FREE, r.entitlements.value)
        assertTrue(client.acknowledged.isEmpty())
        r.purchaseHandled()
        assertEquals(PurchaseUi.Pending(Products.REMOVE_ADS), r.purchase.value, "still pending when the paywall reopens")

        // Play confirms it while the app is away: the next resume's query finds it.
        client.owned = listOf(pending.copy(state = PurchaseState.PURCHASED))
        r.onForeground()
        assertEquals(Entitlements(removeAds = true), r.entitlements.value)
        assertEquals(listOf("t-remove_ads"), client.acknowledged)
        assertEquals(Notice.ADS_OFF, r.notice.value)
        assertEquals(PurchaseUi.Succeeded(Products.REMOVE_ADS), r.purchase.value)
    }

    @Test
    fun aDeclinedPendingPaymentEndsInTheErrorLine() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.buy(ACTIVITY, Products.VIP_YEARLY)
        val pending = purchase(Products.VIP_YEARLY, state = PurchaseState.PENDING)
        client.owned = listOf(pending)
        client.updates.emit(PurchaseUpdate(BillingCode.OK, listOf(pending)))
        assertEquals(PurchaseUi.Pending(Products.VIP_YEARLY), r.purchase.value)
        client.owned = emptyList()
        r.refresh()
        assertEquals(PurchaseUi.Failed, r.purchase.value)
        assertNull(r.notice.value)
    }

    @Test
    fun alreadyOwnedIsSettledByAskingPlay() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        client.owned = listOf(purchase(Products.VIP_WEEKLY))
        r.buy(ACTIVITY, Products.VIP_YEARLY)
        client.updates.emit(PurchaseUpdate(BillingCode.ALREADY_OWNED, emptyList()))
        assertEquals(PurchaseUi.Succeeded(Products.VIP_YEARLY), r.purchase.value)
        assertTrue(r.entitlements.value.vip)

        client.owned = emptyList()
        r.purchaseHandled()
        r.buy(ACTIVITY, Products.REMOVE_ADS)
        client.updates.emit(PurchaseUpdate(BillingCode.ALREADY_OWNED, emptyList()))
        assertEquals(PurchaseUi.Failed, r.purchase.value, "Play doesn't actually list it")
    }

    @Test
    fun restoreReportsWhatPlayHolds() = runTest(UnconfinedTestDispatcher()) {
        val r = repository()
        r.restore()
        assertEquals(RestoreUi.Done(Entitlements.FREE), r.restore.value)
        client.owned = listOf(purchase(Products.VIP_WEEKLY), purchase(Products.REMOVE_ADS))
        r.restore()
        assertEquals(RestoreUi.Done(Entitlements(VipPlan.WEEKLY, removeAds = true)), r.restore.value)
        client.queryCodes += BillingCode.NETWORK
        r.restore()
        assertEquals(RestoreUi.Failed, r.restore.value)
        assertTrue(r.entitlements.value.vip, "a failed restore changes nothing")
        r.restoreHandled()
        assertEquals(RestoreUi.Idle, r.restore.value)
    }

    @Test
    fun aRestoredReinstallGetsEverythingBack() = runTest(UnconfinedTestDispatcher()) {
        // A reinstall has no cache (it's never backed up): Free until Play answers.
        val r = repository()
        assertEquals(Entitlements.FREE, r.entitlements.value)
        client.owned = listOf(purchase(Products.VIP_YEARLY), purchase(Products.REMOVE_ADS))
        r.restore()
        assertEquals(Membership.VIP_YEARLY, r.membership.value)
        assertEquals(Entitlements(VipPlan.YEARLY, removeAds = true), r.entitlements.value)
    }

    private companion object {
        val PRICES = Prices(Price("$2.99", 2_990_000, "USD"), Price("$29.99", 29_990_000, "USD"), Price("$4.99", 4_990_000, "USD"))

        /** The repository only hands the activity to the client; the fake never touches it. */
        val ACTIVITY: Activity by lazy {
            sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let { f ->
                f.isAccessible = true
                (f.get(null) as sun.misc.Unsafe).allocateInstance(Activity::class.java) as Activity
            }
        }
    }
}
