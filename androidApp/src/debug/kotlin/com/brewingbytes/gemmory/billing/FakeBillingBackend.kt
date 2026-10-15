package com.brewingbytes.gemmory.billing

import android.app.Activity
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.PurchaseState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A scriptable Play for debug builds and device checks: prices that load, hang or fail, purchases that succeed,
 * go pending, get cancelled or fail, restore results, and a Play that can't be reached (for the 7-day fallback).
 * The prices mirror the Console and exist only here; release builds read them from Play.
 */
class FakeBillingBackend : BillingBackend {
    /** What a purchase does when Play's sheet would close. */
    enum class PurchaseResult { SUCCESS, PENDING, CANCELLED, ERROR, ALREADY_OWNED }

    @Volatile
    var reachable: Boolean = true

    @Volatile
    var pricesFail: Boolean = false

    /** While set, the price query waits for it (the paywall's loading placeholders). */
    @Volatile
    var pricesGate: CompletableDeferred<Unit>? = null

    /** While set, the purchases query waits for it (Restore's "Checking Google Play…"). */
    @Volatile
    var queryGate: CompletableDeferred<Unit>? = null

    @Volatile
    var outcome: PurchaseResult = PurchaseResult.SUCCESS

    @Volatile
    var offerRemoveAds: Boolean = true

    /** Whether launching a purchase is rejected outright (Play said no before its sheet opened). */
    @Volatile
    var launchFails: Boolean = false

    private val owned = mutableListOf<StorePurchase>()
    val acknowledged = mutableListOf<String>()
    private var nextToken = 0

    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    /** The Console's base prices (EUR, founder call #8), so debug builds and store screenshots match Play. */
    val prices = Prices(
        weekly = Price("€0.99", 990_000, "EUR"),
        yearly = Price("€19.99", 19_990_000, "EUR"),
        removeAds = Price("€2.99", 2_990_000, "EUR"),
    )

    /** Back to a fresh account that owns nothing, with Play reachable. */
    @Synchronized
    fun reset() {
        reachable = true
        pricesFail = false
        // Anything still waiting finishes first.
        pricesGate?.complete(Unit)
        queryGate?.complete(Unit)
        pricesGate = null
        queryGate = null
        outcome = PurchaseResult.SUCCESS
        offerRemoveAds = true
        launchFails = false
        owned.clear()
        acknowledged.clear()
    }

    /** The account already holds [product] (bought before a reinstall, or on another phone). */
    @Synchronized
    fun own(product: String, state: PurchaseState = PurchaseState.PURCHASED, acknowledged: Boolean = true) {
        owned.removeAll { product in it.products }
        owned += StorePurchase(listOf(product), state, "fake-${nextToken++}", acknowledged)
    }

    /** Play confirms the pending payment for [product] and tells the app, as it does while the app is open. */
    fun completePending(product: String, approved: Boolean = true) {
        val update = synchronized(this) {
            val p = owned.firstOrNull { product in it.products && it.state == PurchaseState.PENDING } ?: return
            owned -= p
            if (!approved) return
            val done = p.copy(state = PurchaseState.PURCHASED)
            owned += done
            PurchaseUpdate(BillingCode.OK, listOf(done))
        }
        _updates.tryEmit(update)
    }

    @Synchronized
    fun owns(product: String): Boolean = owned.any { product in it.products && it.state == PurchaseState.PURCHASED }

    override suspend fun connect(): BillingCode = if (reachable) BillingCode.OK else BillingCode.NETWORK

    override suspend fun queryPurchases(): Outcome<List<StorePurchase>> {
        queryGate?.await()
        return if (reachable) Outcome.Ok(synchronized(this) { owned.toList() }) else Outcome.Failed(BillingCode.NETWORK)
    }

    override suspend fun queryPrices(): Outcome<Prices> {
        pricesGate?.await()
        if (!reachable || pricesFail) return Outcome.Failed(BillingCode.NETWORK)
        return Outcome.Ok(if (offerRemoveAds) prices else prices.copy(removeAds = null))
    }

    override suspend fun acknowledge(purchase: StorePurchase): BillingCode {
        if (!reachable) return BillingCode.NETWORK
        val token = purchase.token
        synchronized(this) {
            val i = owned.indexOfFirst { it.token == token }
            if (i >= 0) owned[i] = owned[i].copy(acknowledged = true)
            acknowledged += token
        }
        return BillingCode.OK
    }

    override fun launchPurchase(activity: Activity, product: String): BillingCode {
        if (launchFails || !reachable) return BillingCode.ERROR
        val update = synchronized(this) {
            when (outcome) {
                PurchaseResult.SUCCESS -> {
                    val p = StorePurchase(listOf(product), PurchaseState.PURCHASED, "fake-${nextToken++}", acknowledged = false)
                    if (product != Products.REMOVE_ADS) owned.removeAll { it.products.any { id -> id != Products.REMOVE_ADS } }
                    owned += p
                    PurchaseUpdate(BillingCode.OK, listOf(p))
                }
                PurchaseResult.PENDING -> {
                    val p = StorePurchase(listOf(product), PurchaseState.PENDING, "fake-${nextToken++}", acknowledged = false)
                    owned += p
                    PurchaseUpdate(BillingCode.OK, listOf(p))
                }
                PurchaseResult.CANCELLED -> PurchaseUpdate(BillingCode.CANCELLED, emptyList())
                PurchaseResult.ERROR -> PurchaseUpdate(BillingCode.ERROR, emptyList())
                PurchaseResult.ALREADY_OWNED -> PurchaseUpdate(BillingCode.ALREADY_OWNED, emptyList())
            }
        }
        _updates.tryEmit(update)
        return BillingCode.OK
    }
}
