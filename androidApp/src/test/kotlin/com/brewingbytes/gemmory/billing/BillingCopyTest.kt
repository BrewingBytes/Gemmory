package com.brewingbytes.gemmory.billing

import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.VipPlan
import org.junit.Test
import kotlin.test.assertEquals

/** The decided copy (design system, Paywall and Settings; founder answers #8). */
class BillingCopyTest {
    @Test
    fun planRows() {
        assertEquals("$29.99 / year ($0.58 a week)", BillingCopy.yearly(Price("$29.99", 29_990_000, "USD")))
        assertEquals("$2.99 / week", BillingCopy.weekly(Price("$2.99", 2_990_000, "USD")))
        assertEquals("Just remove ads · $4.99 once", BillingCopy.removeAds(Price("$4.99", 4_990_000, "USD")))
    }

    @Test
    fun perWeekRoundsHalfUp() {
        assertEquals("$1.00", BillingCopy.perWeek(Price("$52.00", 52_000_000, "USD")))
        assertEquals("$0.96", BillingCopy.perWeek(Price("$49.99", 49_990_000, "USD")), "0.9613…")
        assertEquals("$0.01", BillingCopy.perWeek(Price("$0.26", 260_000, "USD")), "0.005 rounds up")
    }

    /** Play formats for the account's country; the per-week price follows Play's string, whatever the phone's language. */
    @Test
    fun perWeekIsWrittenLikePlaysYearlyPrice() {
        assertEquals("2,50\u00A0RON", BillingCopy.perWeek(Price("129,99\u00A0RON", 129_990_000, "RON")), "seen on a Romanian tester account")
        assertEquals("€0.38", BillingCopy.perWeek(Price("€19.99", 19_990_000, "EUR")))
        assertEquals("0,38 €", BillingCopy.perWeek(Price("19,99 €", 19_990_000, "EUR")))
        assertEquals("US$0.58", BillingCopy.perWeek(Price("US$29.99", 29_990_000, "USD")))
        assertEquals("¥58", BillingCopy.perWeek(Price("¥3,000", 3_000_000_000, "JPY")), "no decimals when Play shows none")
        assertEquals("24,98 kr.", BillingCopy.perWeek(Price("1.299,00 kr.", 1_299_000_000, "DKK")))
        assertEquals("Rp 5.769", BillingCopy.perWeek(Price("Rp 299.990", 299_990_000_000, "IDR")), "grouping kept")
        assertEquals("CHF 1.15", BillingCopy.perWeek(Price("CHF 59.90", 59_900_000, "CHF")))
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
