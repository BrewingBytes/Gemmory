package com.brewingbytes.gemmory.billing

import android.net.Uri
import androidx.core.net.toUri
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.VipPlan
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Every billing string the app shows (design system: Paywall, Settings; Phase 5 D6, D7; founder answers #8). */
object BillingCopy {
    const val TITLE = "Gemmory VIP"
    val PERKS = listOf("Every picture unlocked", "Unlimited photo canvases", "No ads ever", "No watermark on your videos")
    const val START_VIP = "Start VIP"
    const val FINE_PRINT = "VIP renews automatically until you cancel. Cancel anytime in Google Play."
    const val PRICES_FAILED = "Prices couldn't load. Connect and try again."
    const val TRY_AGAIN = "Try again"
    const val PURCHASE_FAILED = "Google Play couldn't finish the purchase. You weren't charged."
    const val PENDING_VIP = "Payment pending. VIP starts when Google Play confirms it."
    const val PENDING_REMOVE_ADS = "Payment pending. Ads turn off when Google Play confirms it."

    const val CHECKING = "Checking Google Play…"
    const val RESTORE_NONE = "No purchases found on this account"
    const val RESTORE_FAILED = "Couldn't reach Google Play. Try again."

    fun yearly(yearly: Price, locale: Locale = Locale.getDefault()) = "${yearly.formatted} / year (${perWeek(yearly, locale)} a week)"

    fun weekly(weekly: Price) = "${weekly.formatted} / week"

    fun removeAds(price: Price) = "Just remove ads · ${price.formatted} once"

    /** The yearly price over 52 weeks, rounded half up in the currency's own decimals. */
    fun perWeek(yearly: Price, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(yearly.currency) }.getOrNull()
        val digits = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        val amount = BigDecimal.valueOf(yearly.micros).divide(BigDecimal.valueOf(52_000_000L), digits, RoundingMode.HALF_UP)
        val format = NumberFormat.getCurrencyInstance(locale)
        if (currency != null) format.currency = currency
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(amount)
    }

    fun membership(m: Membership): String = when (m) {
        Membership.VIP_YEARLY -> "VIP · yearly"
        Membership.VIP_WEEKLY -> "VIP · weekly"
        Membership.VIP_UNCONFIRMED -> "VIP · connect to Google Play to confirm"
        Membership.ADS_REMOVED -> "Ads removed"
        Membership.FREE -> "Free"
    }

    fun restored(found: Entitlements): String = when {
        found.plan == VipPlan.YEARLY -> "Purchases restored: VIP (yearly)"
        found.plan == VipPlan.WEEKLY -> "Purchases restored: VIP (weekly)"
        found.removeAds -> "Purchases restored: Ads removed"
        else -> RESTORE_NONE
    }

    fun notice(n: Notice): String = when (n) {
        Notice.VIP_STARTED -> "You're VIP. Every picture is unlocked."
        Notice.ADS_OFF -> "Ads are off for good."
        Notice.VIP_LAPSED -> "We couldn't confirm VIP for a week. Connect to the internet to restore it."
    }

    fun pending(product: String) = if (product == Products.REMOVE_ADS) PENDING_REMOVE_ADS else PENDING_VIP
}

/** Play's subscription page for this app, on [plan] when it's known (Settings' Manage subscription, design D6). */
fun subscriptionPage(packageName: String, plan: VipPlan?): Uri =
    "https://play.google.com/store/account/subscriptions".toUri().buildUpon()
        .apply { plan?.let { appendQueryParameter("sku", it.productId) } }
        .appendQueryParameter("package", packageName)
        .build()
