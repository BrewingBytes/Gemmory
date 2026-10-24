package com.brewingbytes.gemmory.billing

import android.net.Uri
import androidx.core.net.toUri
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings
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
class BillingCopy(private val s: Strings) {
    val title: String get() = s.text(R.string.billing_title)
    val perks: List<String>
        get() = listOf(R.string.billing_perk_pictures, R.string.billing_perk_canvases, R.string.billing_perk_ads, R.string.billing_perk_watermark, R.string.billing_perk_tools)
            .map { s.text(it) }
    val startVip: String get() = s.text(R.string.billing_start_vip)
    val finePrint: String get() = s.text(R.string.billing_fine_print)
    val pricesFailed: String get() = s.text(R.string.billing_prices_failed)
    val purchaseFailed: String get() = s.text(R.string.billing_purchase_failed)
    val checking: String get() = s.text(R.string.billing_checking)
    val restoreFailed: String get() = s.text(R.string.billing_restore_failed)

    fun yearly(yearly: Price) = s.text(R.string.billing_yearly, yearly.formatted, perWeek(yearly))

    fun weekly(weekly: Price) = s.text(R.string.billing_weekly, weekly.formatted)

    fun removeAds(price: Price) = s.text(R.string.billing_remove_ads, price.formatted)

    fun membership(m: Membership): String = s.text(
        when (m) {
            Membership.VIP_YEARLY -> R.string.membership_vip_yearly
            Membership.VIP_WEEKLY -> R.string.membership_vip_weekly
            Membership.VIP_UNCONFIRMED -> R.string.membership_vip_unconfirmed
            Membership.ADS_REMOVED -> R.string.membership_ads_removed
            Membership.FREE -> R.string.membership_free
        },
    )

    fun restored(found: Entitlements): String = s.text(
        when {
            found.plan == VipPlan.YEARLY -> R.string.billing_restored_yearly
            found.plan == VipPlan.WEEKLY -> R.string.billing_restored_weekly
            found.removeAds -> R.string.billing_restored_ads
            else -> R.string.billing_restore_none
        },
    )

    fun notice(n: Notice): String = s.text(
        when (n) {
            Notice.VIP_STARTED -> R.string.billing_notice_vip
            Notice.ADS_OFF -> R.string.billing_notice_ads_off
            Notice.VIP_LAPSED -> R.string.billing_notice_lapsed
        },
    )

    fun pending(product: String) = s.text(if (product == Products.REMOVE_ADS) R.string.billing_pending_ads else R.string.billing_pending_vip)

    companion object {
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
    }
}

/** Play's subscription page for this app, on [plan] when it's known (Settings' Manage subscription, design D6). */
fun subscriptionPage(packageName: String, plan: VipPlan?): Uri =
    "https://play.google.com/store/account/subscriptions".toUri().buildUpon()
        .apply { plan?.let { appendQueryParameter("sku", it.productId) } }
        .appendQueryParameter("package", packageName)
        .build()
