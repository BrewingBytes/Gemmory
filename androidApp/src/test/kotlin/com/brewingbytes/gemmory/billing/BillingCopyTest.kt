package com.brewingbytes.gemmory.billing

import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.VipPlan
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

/** The decided copy (design system, Paywall and Settings; founder answers #8). */
class BillingCopyTest {
    @Test
    fun planRows() {
        assertEquals("$29.99 / year ($0.58 a week)", BillingCopy.yearly(Price("$29.99", 29_990_000, "USD"), Locale.US))
        assertEquals("$2.99 / week", BillingCopy.weekly(Price("$2.99", 2_990_000, "USD")))
        assertEquals("Just remove ads · $4.99 once", BillingCopy.removeAds(Price("$4.99", 4_990_000, "USD")))
    }

    @Test
    fun perWeekRoundsHalfUpInTheCurrencysOwnDecimals() {
        assertEquals("$1.00", BillingCopy.perWeek(Price("$52.00", 52_000_000, "USD"), Locale.US))
        assertEquals("$0.96", BillingCopy.perWeek(Price("$49.99", 49_990_000, "USD"), Locale.US), "0.9613…")
        assertEquals("$0.01", BillingCopy.perWeek(Price("$0.26", 260_000, "USD"), Locale.US), "0.005 rounds up")
    }

    @Test
    fun currenciesWithoutCentsAndOtherLocales() {
        assertEquals("¥58", BillingCopy.perWeek(Price("¥3,000", 3_000_000_000, "JPY"), Locale.JAPAN).replace('￥', '¥'))
        assertEquals("1,92 RON", BillingCopy.perWeek(Price("99,99 RON", 99_990_000, "RON"), Locale.forLanguageTag("ro-RO")).replace('\u00A0', ' '))
    }

    @Test
    fun membershipRestoreAndNoticeLines() {
        assertEquals(
            listOf("VIP · yearly", "VIP · weekly", "VIP · connect to Google Play to confirm", "Ads removed", "Free"),
            Membership.entries.map(BillingCopy::membership),
        )
        assertEquals("Purchases restored: VIP (yearly)", BillingCopy.restored(Entitlements(VipPlan.YEARLY, removeAds = true)))
        assertEquals("Purchases restored: VIP (weekly)", BillingCopy.restored(Entitlements(VipPlan.WEEKLY)))
        assertEquals("Purchases restored: Ads removed", BillingCopy.restored(Entitlements(removeAds = true)))
        assertEquals("No purchases found on this account", BillingCopy.restored(Entitlements.FREE))
        assertEquals("You're VIP. Every picture is unlocked.", BillingCopy.notice(Notice.VIP_STARTED))
        assertEquals("Ads are off for good.", BillingCopy.notice(Notice.ADS_OFF))
        assertEquals("We couldn't confirm VIP for a week. Connect to the internet to restore it.", BillingCopy.notice(Notice.VIP_LAPSED))
        assertEquals("Payment pending. VIP starts when Google Play confirms it.", BillingCopy.pending(Products.VIP_YEARLY))
        assertEquals("Payment pending. Ads turn off when Google Play confirms it.", BillingCopy.pending(Products.REMOVE_ADS))
    }
}
