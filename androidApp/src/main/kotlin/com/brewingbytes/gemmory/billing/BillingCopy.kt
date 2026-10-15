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

    fun yearly(yearly: Price) = "${yearly.formatted} / year (${perWeek(yearly)} a week)"

    fun weekly(weekly: Price) = "${weekly.formatted} / week"

    fun removeAds(price: Price) = "Just remove ads · ${price.formatted} once"

    /**
     * The yearly price over 52 weeks, rounded half up, written the way Play wrote the yearly price: Play formats for
     * the account's country, not the phone's language, so its own string is the pattern ("129,99 RON" → "2,50 RON",
     * "$29.99" → "$0.58", "¥3,000" → "¥58"). The decimals are the ones Play showed (none for "¥3,000").
     */
    fun perWeek(yearly: Price): String {
        val number = NUMBER.find(yearly.formatted) ?: return fallbackPerWeek(yearly)
        val text = number.value
        val lastSep = text.indexOfLast { it == ',' || it == '.' }
        val decimals = if (lastSep >= 0 && text.length - lastSep - 1 in 1..2) text.length - lastSep - 1 else 0
        val decimalSep = if (decimals > 0) text[lastSep] else null
        val grouping = text.firstOrNull { !it.isDigit() && it != decimalSep }
        val amount = BigDecimal.valueOf(yearly.micros).divide(BigDecimal.valueOf(52_000_000L), decimals, RoundingMode.HALF_UP)
        val (whole, fraction) = amount.toPlainString().split('.').let { it[0] to it.getOrNull(1) }
        val grouped = if (grouping == null) whole else whole.reversed().chunked(3).joinToString(grouping.toString()).reversed()
        val plain = if (fraction == null || decimalSep == null) grouped else "$grouped$decimalSep$fraction"
        return yearly.formatted.replaceRange(number.range, plain)
    }

    /** A price string with no figures in it (never seen from Play): the currency's own format. */
    private fun fallbackPerWeek(yearly: Price): String {
        val currency = runCatching { Currency.getInstance(yearly.currency) }.getOrNull()
        val digits = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        if (currency != null) format.currency = currency
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(BigDecimal.valueOf(yearly.micros).divide(BigDecimal.valueOf(52_000_000L), digits, RoundingMode.HALF_UP))
    }

    private val NUMBER = Regex("""\d(?:[\d.,\u00A0\u202F ]*\d)?""")

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
