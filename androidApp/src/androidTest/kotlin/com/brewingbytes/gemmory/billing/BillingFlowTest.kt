package com.brewingbytes.gemmory.billing

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.PurchaseState
import com.brewingbytes.gemmory.policy.VipPlan
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Billing through the real app on the debug fake backend (no Play Store on the emulator): buy from Membership, a
 * pending payment confirmed later, cancel and error, every Restore result, and the query on coming to the
 * foreground. The rewarded sheet's VIP texts are covered by RewardedSheetTest (onVip); reaching their states here
 * needed the emulator's radios turned off, which hung the emulator (#8). The 7-day fallback needs a moved clock, so it lives in EntitlementsTest and
 * BillingRepositoryTest.
 */
@RunWith(AndroidJUnit4::class)
class BillingFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val fake = BillingBackends.fake
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        assertTrue(BuildConfig.FAKE_BILLING, "connected tests run on the fake billing backend")
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        resetBilling()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        resetBilling()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    /** A Free account with Play reachable, and nothing left over from the last test. */
    private fun resetBilling() {
        fake.reset()
        runBlocking { app.billing.refresh() }
        app.billing.purchaseHandled()
        app.billing.restoreHandled()
        app.billing.notice.value?.let(app.billing::noticeShown)
        app.billing.loadPrices()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("tile-bundled-starter"))
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(matcher.description, timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String) = waitFor(hasText(text, substring = true))

    private fun openSettings() {
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor(hasTestTag("settings-membership"))
    }

    private fun membershipIs(label: String) {
        compose.waitUntil("membership $label", 5_000) {
            compose.onAllNodes(hasTestTag("settings-membership-value") and hasText(label), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPaywallFromMembership() {
        compose.onNodeWithTag("settings-membership").performClick()
        waitFor(hasTestTag(PAYWALL_TITLE_TAG))
        waitFor(hasTestTag("plan-yearly"))
    }

    @Test
    fun buyYearlyFromMembership() {
        launch()
        openSettings()
        membershipIs("Free")
        compose.onNodeWithTag("settings-manage-subscription").assertDoesNotExist()
        openPaywallFromMembership()
        compose.onNodeWithTag("paywall-start").performClick()

        // Success: the paywall closes, VIP shows in Settings, the purchase was acknowledged.
        waitFor(hasTestTag("settings-membership"))
        membershipIs("VIP · yearly")
        compose.onNodeWithTag("settings-manage-subscription").assertExists()
        assertEquals(Entitlements(VipPlan.YEARLY), app.billing.entitlements.value)
        assertTrue(fake.owns(Products.VIP_YEARLY))
        assertEquals(1, fake.acknowledged.size)
        assertEquals(Allowance.Unlimited, app.monetization.allowance.value, "unlimited photo canvases")

        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("You're VIP. Every picture is unlocked.")
    }

    @Test
    fun removeAdsPendingThenConfirmed() {
        launch()
        openSettings()
        openPaywallFromMembership()
        fake.outcome = FakeBillingBackend.PurchaseResult.PENDING
        compose.onNodeWithTag("paywall-remove-ads").performClick()
        waitForText("Payment pending. Ads turn off when Google Play confirms it.")
        assertEquals(Entitlements.FREE, app.billing.entitlements.value, "pending grants nothing")
        assertTrue(fake.acknowledged.isEmpty())

        fake.completePending(Products.REMOVE_ADS)
        waitFor(hasTestTag("settings-membership"))
        membershipIs("Ads removed")
        assertTrue(app.billing.entitlements.value.adsRemoved)
        assertEquals(1, fake.acknowledged.size)
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Ads are off for good.")

        // Already owned: the paywall no longer offers it.
        openSettings()
        openPaywallFromMembership()
        compose.onNodeWithTag("paywall-remove-ads").assertDoesNotExist()
    }

    @Test
    fun cancelShowsNothingAndAnErrorSaysNothingWasCharged() {
        launch()
        openSettings()
        openPaywallFromMembership()
        fake.outcome = FakeBillingBackend.PurchaseResult.CANCELLED
        compose.onNodeWithTag("paywall-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("paywall-message").assertDoesNotExist()
        compose.onNodeWithTag("paywall-start").assertIsEnabled()

        fake.outcome = FakeBillingBackend.PurchaseResult.ERROR
        compose.onNodeWithTag("plan-weekly").performClick()
        compose.onNodeWithTag("paywall-start").performClick()
        waitForText("Google Play couldn't finish the purchase. You weren't charged.")
        assertEquals(Entitlements.FREE, app.billing.entitlements.value)

        compose.onNodeWithTag("paywall-close").performClick()
        membershipIs("Free")
        // A new visit starts clean.
        openPaywallFromMembership()
        compose.onNodeWithTag("paywall-message").assertDoesNotExist()
    }

    @Test
    fun everyRestoreResult() {
        launch()
        openSettings()
        val restore = compose.onNodeWithTag("settings-restore")
        val result = compose.onNodeWithTag("settings-restore-result", useUnmergedTree = true)

        fake.own(Products.VIP_WEEKLY, acknowledged = false)
        val gate = CompletableDeferred<Unit>()
        fake.queryGate = gate
        restore.performClick()
        waitForText("Checking Google Play…")
        restore.assertIsNotEnabled()
        gate.complete(Unit)
        fake.queryGate = null
        waitForText("Purchases restored: VIP (weekly)")
        membershipIs("VIP · weekly")
        assertEquals(1, fake.acknowledged.size, "restore acknowledges too")

        fake.reset()
        restore.performClick()
        waitForText("No purchases found on this account")
        membershipIs("Free")

        fake.own(Products.REMOVE_ADS)
        fake.reachable = false
        restore.performClick()
        waitForText("Couldn't reach Google Play. Try again.")
        membershipIs("Free")

        fake.reachable = true
        restore.performClick()
        waitForText("Purchases restored: Ads removed")
        membershipIs("Ads removed")

        fake.own(Products.VIP_YEARLY)
        restore.performClick()
        waitForText("Purchases restored: VIP (yearly)")
        result.assertTextEquals("Purchases restored: VIP (yearly)")

        // The line is for this visit only.
        compose.onNodeWithContentDescription("Back").performClick()
        openSettings()
        result.assertDoesNotExist()
    }

    @Test
    fun comingToTheForegroundAsksPlay() {
        // Bought on another phone, or before a reinstall (the cache is never backed up or transferred).
        fake.own(Products.VIP_YEARLY, state = PurchaseState.PURCHASED)
        // The app is in the background once the process lifecycle has seen the last test's activity stop (700ms).
        SystemClock.sleep(1_500)
        assertEquals(Entitlements.FREE, app.billing.entitlements.value)
        launch()
        compose.waitUntil("VIP from Play", 10_000) { app.billing.entitlements.value.vip }
        openSettings()
        membershipIs("VIP · yearly")
    }
}
