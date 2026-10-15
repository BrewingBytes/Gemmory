package com.brewingbytes.gemmory.billing

import android.app.Activity
import com.brewingbytes.gemmory.policy.Purchase
import com.brewingbytes.gemmory.policy.PurchaseState
import kotlinx.coroutines.flow.Flow

/** Play Billing's response codes, folded into what the app treats differently. */
enum class BillingCode {
    OK,
    CANCELLED,

    /** The connection to Play dropped: reconnect and try once more. */
    DISCONNECTED,

    /** No Play Store, or billing isn't available for this account. */
    UNAVAILABLE,

    /** Offline, a timeout, or Play's service was busy. */
    NETWORK,
    ALREADY_OWNED,
    ERROR,
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>

    data class Failed(val code: BillingCode) : Outcome<Nothing>
}

/** A purchase as Play reports it, with what acknowledging needs. */
data class StorePurchase(
    val products: List<String>,
    val state: PurchaseState,
    val token: String,
    val acknowledged: Boolean,
) {
    val policy: Purchase get() = Purchase(products, state)
}

/** A price exactly as Play formats it, plus the amount for the per-week line. */
data class Price(val formatted: String, val micros: Long, val currency: String)

/** The base plans and the one-time product (design D6: prices only from ProductDetails). [removeAds] is null when Play doesn't offer it. */
data class Prices(val weekly: Price, val yearly: Price, val removeAds: Price?)

/** A purchase flow finished (Play's PurchasesUpdatedListener). */
data class PurchaseUpdate(val code: BillingCode, val purchases: List<StorePurchase>)

/**
 * The Play Billing calls the app makes, so [BillingRepository] runs the same against Play, the debug fake backend
 * (device checks without a Play Store) and unit-test fakes.
 */
interface BillingBackend {
    /** Connects if needed. OK when ready. */
    suspend fun connect(): BillingCode

    /** Every subscription and one-time purchase the account holds now. */
    suspend fun queryPurchases(): Outcome<List<StorePurchase>>

    suspend fun queryPrices(): Outcome<Prices>

    suspend fun acknowledge(token: String): BillingCode

    /** Opens Play's purchase sheet for [product] (a base plan for subscriptions). The result arrives on [updates]. */
    fun launchPurchase(activity: Activity, product: String): BillingCode

    val updates: Flow<PurchaseUpdate>
}
