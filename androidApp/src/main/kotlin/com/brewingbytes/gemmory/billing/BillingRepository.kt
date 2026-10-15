package com.brewingbytes.gemmory.billing

import android.app.Activity
import com.brewingbytes.gemmory.policy.EntitlementCache
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.PurchaseState
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The paywall's price rows (design D6). */
sealed interface PricesUi {
    data object Loading : PricesUi

    /** "Prices couldn't load. Connect and try again." */
    data object Failed : PricesUi

    data class Ready(val prices: Prices) : PricesUi
}

/** The paywall's purchase line (design D6). A cancel goes back to [Idle] with no message. */
sealed interface PurchaseUi {
    data object Idle : PurchaseUi

    /** Play's sheet is opening or open: Start VIP is held. */
    data class Launching(val product: String) : PurchaseUi

    data class Pending(val product: String) : PurchaseUi

    /** "Google Play couldn't finish the purchase. You weren't charged." */
    data object Failed : PurchaseUi

    /** The paywall closes; the gallery shows its line. */
    data class Succeeded(val product: String) : PurchaseUi
}

/** Settings' Restore purchases line (design system, Settings). */
sealed interface RestoreUi {
    data object Idle : RestoreUi

    /** "Checking Google Play…" */
    data object Checking : RestoreUi

    /** What the account holds; Free reads "No purchases found on this account". */
    data class Done(val found: Entitlements) : RestoreUi

    /** "Couldn't reach Google Play. Try again." */
    data object Failed : RestoreUi
}

/** The gallery's one-time lines (design D6, D7). */
enum class Notice { VIP_STARTED, ADS_OFF, VIP_LAPSED }

/**
 * Play Billing for the app (eng E6): queries on cold start and every resume ([onForeground]; the process's first
 * foreground is the cold start) and after every finished purchase, acknowledges every unacknowledged purchase (Play
 * refunds one left unacknowledged for 3 days), reconnects and retries once when the service dropped, and keeps
 * [EntitlementCache] in step. [entitlements] is what ads, locked tiles, the photo allowance and the watermark follow.
 * The cache isn't thread-safe, so it's only touched on [worker].
 */
class BillingRepository(
    private val backend: BillingBackend,
    private val cache: EntitlementCache,
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    private val analytics: Analytics = Analytics.None,
) {
    // Read once here, before the first frame, so a VIP never sees a locked tile flash at launch.
    private val _entitlements = MutableStateFlow(cache.entitlements())
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _membership = MutableStateFlow(cache.membership())
    val membership: StateFlow<Membership> = _membership.asStateFlow()

    private val _prices = MutableStateFlow<PricesUi>(PricesUi.Loading)
    val prices: StateFlow<PricesUi> = _prices.asStateFlow()

    private val _purchase = MutableStateFlow<PurchaseUi>(PurchaseUi.Idle)
    val purchase: StateFlow<PurchaseUi> = _purchase.asStateFlow()

    private val _restore = MutableStateFlow<RestoreUi>(RestoreUi.Idle)
    val restore: StateFlow<RestoreUi> = _restore.asStateFlow()

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private val querying = Mutex()
    private val pricing = Mutex()

    /** Products whose payment is pending (worker only). Settled by a later update or query. */
    private val pending = mutableSetOf<String>()

    init {
        scope.launch { backend.updates.collect { onUpdate(it) } }
    }

    private suspend fun <T> onWorker(block: () -> T): T = withContext(worker) { block() }

    /** Connects first; when the service dropped mid-call, reconnects and tries once more. */
    private suspend fun <T> call(op: suspend () -> Outcome<T>): Outcome<T> {
        backend.connect().let { if (it != BillingCode.OK) return Outcome.Failed(it) }
        val first = op()
        if (first is Outcome.Failed && first.code == BillingCode.DISCONNECTED && backend.connect() == BillingCode.OK) return op()
        return first
    }

    fun onForeground() {
        scope.launch { refresh() }
    }

    /** Asks Play what the account holds. A failure leaves the cache as it is (eng D5). */
    suspend fun refresh(): Outcome<Entitlements> = querying.withLock {
        val result = call { backend.queryPurchases() }
        if (result is Outcome.Ok) acknowledge(result.value)
        val outcome = onWorker {
            when (result) {
                is Outcome.Ok -> {
                    val verified = cache.onVerified(result.value.map { it.policy })
                    settlePending(result.value)
                    Outcome.Ok(verified)
                }
                is Outcome.Failed -> result
            }.also { publish() }
        }
        outcome
    }

    private suspend fun acknowledge(purchases: List<StorePurchase>) {
        for (p in purchases) {
            if (p.state != PurchaseState.PURCHASED || p.acknowledged) continue
            // Not acknowledged now: the next query tries again, well inside Play's 3 days. The backend reports it with
            // Play's own words.
            backend.acknowledge(p).let {
                if (it == BillingCode.DISCONNECTED && backend.connect() == BillingCode.OK) backend.acknowledge(p)
            }
        }
    }

    /** Worker only. A pending payment that went through is a success; one that's gone was declined. */
    private fun settlePending(purchases: List<StorePurchase>) {
        for (product in pending.toList()) {
            val matching = purchases.filter { product in it.products }
            when {
                matching.any { it.state == PurchaseState.PURCHASED } -> {
                    pending -= product
                    succeeded(product)
                    purchased(product)
                }
                matching.none { it.state == PurchaseState.PENDING } -> {
                    pending -= product
                    if (_purchase.value == PurchaseUi.Pending(product)) _purchase.value = PurchaseUi.Failed
                }
            }
        }
    }

    /** A purchase that went through just now (not one restored or already owned): vip_purchased / remove_ads_purchased. */
    private fun purchased(product: String) {
        analytics.log(
            when (product) {
                Products.REMOVE_ADS -> AnalyticsEvent.removeAdsPurchased
                Products.VIP_WEEKLY -> AnalyticsEvent.vipPurchased("weekly")
                Products.VIP_YEARLY -> AnalyticsEvent.vipPurchased("yearly")
                else -> return
            },
        )
    }

    /** Worker only. */
    private fun succeeded(product: String) {
        _purchase.value = PurchaseUi.Succeeded(product)
        _notice.value = if (product == Products.REMOVE_ADS) Notice.ADS_OFF else Notice.VIP_STARTED
    }

    /** Worker only. */
    private fun publish() {
        val held = cache.entitlements()
        _entitlements.value = held
        _membership.value = cache.membership()
        // A purchase Play took back before the gallery showed its line (revoked, or cancelled unacknowledged): the
        // line would contradict Membership, so it goes.
        val notice = _notice.value
        if ((notice == Notice.VIP_STARTED && !held.vip) || (notice == Notice.ADS_OFF && !held.adsRemoved)) {
            _notice.compareAndSet(notice, null)
        }
        if (_notice.value == null && cache.lapseNoticeDue()) _notice.value = Notice.VIP_LAPSED
    }

    /** The paywall opened, or its "Try again". Keeps prices already loaded. */
    fun loadPrices() {
        scope.launch {
            pricing.withLock {
                if (_prices.value is PricesUi.Ready) return@withLock
                _prices.value = PricesUi.Loading
                _prices.value = when (val r = call { backend.queryPrices() }) {
                    is Outcome.Ok -> PricesUi.Ready(r.value)
                    is Outcome.Failed -> PricesUi.Failed
                }
            }
        }
    }

    /** Start VIP or Just remove ads. Main thread (Play's sheet opens over [activity]). */
    fun buy(activity: Activity, product: String) {
        if (_purchase.value is PurchaseUi.Launching) return
        _purchase.value = PurchaseUi.Launching(product)
        when (val code = backend.launchPurchase(activity, product)) {
            BillingCode.OK -> Unit
            BillingCode.CANCELLED -> _purchase.value = PurchaseUi.Idle
            else -> scope.launch { onUpdate(PurchaseUpdate(code, emptyList())) }
        }
    }

    private suspend fun onUpdate(update: PurchaseUpdate) {
        val launching = (_purchase.value as? PurchaseUi.Launching)?.product
        when (update.code) {
            BillingCode.OK -> {
                val purchases = update.purchases
                acknowledge(purchases)
                onWorker {
                    cache.onPurchased(purchases.map { it.policy })
                    for (p in purchases) {
                        for (product in p.products) {
                            when (p.state) {
                                PurchaseState.PURCHASED -> {
                                    pending -= product
                                    succeeded(product)
                                    purchased(product)
                                }
                                PurchaseState.PENDING -> {
                                    pending += product
                                    _purchase.value = PurchaseUi.Pending(product)
                                }
                                PurchaseState.UNSPECIFIED -> Unit
                            }
                        }
                    }
                    publish()
                }
                // The full list, so a switch or anything else bought elsewhere is in step too.
                refresh()
            }
            BillingCode.CANCELLED -> _purchase.value = PurchaseUi.Idle
            BillingCode.ALREADY_OWNED -> {
                // Bought on another device or before a reinstall: what Play holds is the answer.
                val owned = (refresh() as? Outcome.Ok)?.value
                val has = when (launching) {
                    Products.REMOVE_ADS -> owned?.removeAds == true
                    null -> false
                    else -> owned?.vip == true
                }
                onWorker { if (has && launching != null) succeeded(launching) else _purchase.value = PurchaseUi.Failed }
            }
            else -> _purchase.value = PurchaseUi.Failed
        }
    }

    /** The paywall closed, or a new visit starts: clears a finished result. A pending payment stays shown. */
    fun purchaseHandled() {
        val p = _purchase.value
        if (p is PurchaseUi.Failed || p is PurchaseUi.Succeeded) _purchase.compareAndSet(p, PurchaseUi.Idle)
    }

    /** Settings' Restore purchases: the same query, with its own line. */
    fun restore() {
        if (_restore.value == RestoreUi.Checking) return
        _restore.value = RestoreUi.Checking
        scope.launch {
            _restore.value = when (val r = refresh()) {
                is Outcome.Ok -> RestoreUi.Done(r.value)
                is Outcome.Failed -> RestoreUi.Failed
            }
        }
    }

    fun restoreHandled() {
        if (_restore.value != RestoreUi.Checking) _restore.value = RestoreUi.Idle
    }

    /** The gallery showed [shown]: it's gone for good (the lapse line once per lapse). */
    fun noticeShown(shown: Notice) {
        if (!_notice.compareAndSet(shown, null)) return
        if (shown == Notice.VIP_LAPSED) scope.launch { onWorker { cache.onLapseNoticeShown() } }
    }
}
