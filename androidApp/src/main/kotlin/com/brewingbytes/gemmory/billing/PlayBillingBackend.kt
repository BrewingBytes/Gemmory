package com.brewingbytes.gemmory.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.PurchaseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.android.billingclient.api.Purchase as PlayPurchase

/**
 * Play Billing Library 9 (eng E6): pending purchases enabled for one-time products, automatic service reconnection,
 * and both product types queried every time. Base plans only, no offers or trials (founder call #8).
 */
class PlayBillingBackend(context: Context) : BillingBackend {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            _updates.tryEmit(PurchaseUpdate(code(result), purchases.orEmpty().map(::toStore)))
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()

    /** From the last price query; a purchase can only start from these. */
    @Volatile
    private var details: Map<String, ProductDetails> = emptyMap()

    override suspend fun connect(): BillingCode = connecting.withLock {
        if (client.isReady) return BillingCode.OK
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(code(result))
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(BillingCode.DISCONNECTED)
                    }
                })
            }
        } ?: BillingCode.NETWORK
    }

    override suspend fun queryPurchases(): Outcome<List<StorePurchase>> {
        val all = mutableListOf<StorePurchase>()
        for (type in listOf(ProductType.SUBS, ProductType.INAPP)) {
            val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            val c = code(result.billingResult)
            if (c != BillingCode.OK) return Outcome.Failed(c)
            result.purchasesList.mapTo(all, ::toStore)
        }
        return Outcome.Ok(all)
    }

    override suspend fun queryPrices(): Outcome<Prices> {
        val found = mutableMapOf<String, ProductDetails>()
        for ((type, ids) in listOf(
            ProductType.SUBS to listOf(Products.VIP_WEEKLY, Products.VIP_YEARLY),
            ProductType.INAPP to listOf(Products.REMOVE_ADS),
        )) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() },
            ).build()
            val result = client.queryProductDetails(params)
            val c = code(result.billingResult)
            if (c != BillingCode.OK) return Outcome.Failed(c)
            result.productDetailsList.orEmpty().associateByTo(found) { it.productId }
        }
        details = found
        val weekly = found[Products.VIP_WEEKLY]?.let(::basePlanPrice)
        val yearly = found[Products.VIP_YEARLY]?.let(::basePlanPrice)
        // A subscription missing from the Console (or not active yet) can't be sold: the paywall shows its error state.
        if (weekly == null || yearly == null) return Outcome.Failed(BillingCode.ERROR)
        val removeAds = found[Products.REMOVE_ADS]?.oneTimePurchaseOfferDetails?.let {
            Price(it.formattedPrice, it.priceAmountMicros, it.priceCurrencyCode)
        }
        return Outcome.Ok(Prices(weekly, yearly, removeAds))
    }

    override suspend fun acknowledge(token: String): BillingCode =
        code(client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()))

    override fun launchPurchase(activity: Activity, product: String): BillingCode {
        val d = details[product] ?: return BillingCode.ERROR
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        when (d.productType) {
            ProductType.SUBS -> params.setOfferToken(basePlan(d)?.offerToken ?: return BillingCode.ERROR)
            else -> d.oneTimePurchaseOfferDetails?.offerToken?.let(params::setOfferToken)
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build()
        return code(client.launchBillingFlow(activity, flow))
    }

    private fun basePlan(d: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        d.subscriptionOfferDetails?.firstOrNull { it.offerId == null }

    private fun basePlanPrice(d: ProductDetails): Price? =
        basePlan(d)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.let {
            Price(it.formattedPrice, it.priceAmountMicros, it.priceCurrencyCode)
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L

        fun toStore(p: PlayPurchase) = StorePurchase(
            products = p.products,
            state = when (p.purchaseState) {
                PlayPurchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
                PlayPurchase.PurchaseState.PENDING -> PurchaseState.PENDING
                else -> PurchaseState.UNSPECIFIED
            },
            token = p.purchaseToken,
            acknowledged = p.isAcknowledged,
        )

        @Suppress("DEPRECATION") // SERVICE_TIMEOUT: older Play Store versions still send it.
        fun code(result: BillingResult): BillingCode = when (result.responseCode) {
            BillingResponseCode.OK -> BillingCode.OK
            BillingResponseCode.USER_CANCELED -> BillingCode.CANCELLED
            BillingResponseCode.SERVICE_DISCONNECTED -> BillingCode.DISCONNECTED
            BillingResponseCode.BILLING_UNAVAILABLE -> BillingCode.UNAVAILABLE
            BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.NETWORK_ERROR, BillingResponseCode.SERVICE_TIMEOUT ->
                BillingCode.NETWORK
            BillingResponseCode.ITEM_ALREADY_OWNED -> BillingCode.ALREADY_OWNED
            else -> BillingCode.ERROR
        }
    }
}
