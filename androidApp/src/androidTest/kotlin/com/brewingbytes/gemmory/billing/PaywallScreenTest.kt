package com.brewingbytes.gemmory.billing

import com.brewingbytes.gemmory.copy.strings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.app.scannerValidator
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.ui.GemmoryTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every paywall state (design D6) with its exact copy, the 480dp column (D22), and the Accessibility Scanner checks. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PaywallScreenTest {
    private val copy = BillingCopy(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.strings)

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var prices by mutableStateOf<PricesUi>(PricesUi.Loading)
    private var purchase by mutableStateOf<PurchaseUi>(PurchaseUi.Idle)
    private var owned by mutableStateOf(Entitlements.FREE)
    private val events = mutableListOf<String>()
    private val ready = PricesUi.Ready(BillingBackends.fake.prices)

    @Before
    fun edgeToEdge() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    private fun show(width: Int? = null) {
        compose.setContent {
            GemmoryTheme {
                val paywall = @androidx.compose.runtime.Composable {
                    PaywallScreen(
                        prices = prices,
                        purchase = purchase,
                        entitlements = owned,
                        onBuy = { events += it },
                        onRetryPrices = { events += "retry" },
                        onClose = { events += "close" },
                    )
                }
                if (width == null) paywall() else Box(Modifier.requiredWidth(width.dp)) { paywall() }
            }
        }
    }

    private fun text(s: String) = compose.onNode(hasText(s, substring = true), useUnmergedTree = true)

    @Test
    fun everyStateWithItsCopy() {
        show()
        // Close is there from the first frame: no delayed close (D6).
        compose.onNodeWithContentDescription("Close").assertIsDisplayed().assertIsEnabled()
        text("Gemmory VIP").assertExists()
        copy.perks.forEach { text(it).assertExists() }
        text(copy.finePrint).assertExists()

        // Prices loading: placeholders, Start VIP disabled, no remove-ads row.
        compose.onNodeWithContentDescription("Loading prices").assertExists()
        compose.onNodeWithTag("paywall-start").assertIsNotEnabled()
        compose.onNodeWithTag("paywall-remove-ads").assertDoesNotExist()

        prices = PricesUi.Failed
        text("Prices couldn't load. Connect and try again.").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), events)
        compose.onNodeWithTag("paywall-start").assertIsNotEnabled()

        prices = ready
        val p = BillingBackends.fake.prices
        text("Yearly").assertExists()
        text(copy.yearly(p.yearly)).assertExists()
        text("Weekly").assertExists()
        text("${p.weekly.formatted} / week").assertExists()
        text("Just remove ads · ${p.removeAds!!.formatted} once").assertExists()
        compose.onNodeWithTag("plan-yearly").assertIsSelected()
        compose.onNodeWithTag("plan-weekly").assertIsNotSelected()
        compose.onNodeWithTag("paywall-start").assertIsEnabled().performClick()
        compose.onNodeWithTag("plan-weekly").performClick().assertIsSelected()
        compose.onNodeWithTag("plan-yearly").assertIsNotSelected()
        compose.onNodeWithTag("paywall-start").performClick()
        compose.onNodeWithTag("paywall-remove-ads").performClick()
        assertEquals(listOf("retry", Products.VIP_YEARLY, Products.VIP_WEEKLY, Products.REMOVE_ADS), events)

        // Play's sheet is open: Start is held, a second tap does nothing.
        purchase = PurchaseUi.Launching(Products.VIP_WEEKLY)
        compose.onNodeWithTag("paywall-start").performClick()
        compose.onNodeWithTag("paywall-remove-ads").assertIsNotEnabled()
        assertEquals(4, events.size)

        purchase = PurchaseUi.Pending(Products.VIP_WEEKLY)
        text("Payment pending. VIP starts when Google Play confirms it.").assertExists()
        compose.onNodeWithTag("paywall-start").assertIsNotEnabled()
        compose.onNodeWithTag("paywall-remove-ads").assertIsEnabled()

        purchase = PurchaseUi.Pending(Products.REMOVE_ADS)
        text("Payment pending. Ads turn off when Google Play confirms it.").assertExists()
        compose.onNodeWithTag("paywall-remove-ads").assertIsNotEnabled()
        compose.onNodeWithTag("paywall-start").assertIsEnabled()

        purchase = PurchaseUi.Failed
        text("Google Play couldn't finish the purchase. You weren't charged.").assertExists()

        // A cancel is back to the paywall with no message.
        purchase = PurchaseUi.Idle
        compose.onNodeWithTag("paywall-message").assertDoesNotExist()

        owned = Entitlements(removeAds = true)
        compose.onNodeWithTag("paywall-remove-ads").assertDoesNotExist()
        compose.onNodeWithTag("paywall-start").assertIsEnabled()

        // Success closes the paywall.
        purchase = PurchaseUi.Succeeded(Products.VIP_YEARLY)
        compose.waitForIdle()
        assertEquals("close", events.last())
    }

    @Test
    fun aMissingRemoveAdsProductHidesItsRow() {
        prices = PricesUi.Ready(BillingBackends.fake.prices.copy(removeAds = null))
        show()
        compose.onNodeWithTag("paywall-start").assertIsEnabled()
        compose.onNodeWithTag("paywall-remove-ads").assertDoesNotExist()
    }

    @Test
    fun theColumnIsAtMost480dpWideOnALargeScreen() {
        prices = ready
        show(width = 900)
        val start = compose.onNodeWithTag("paywall-start").getBoundsInRoot()
        val width = start.right - start.left
        // 480dp column minus the 16dp gutters.
        assertTrue(width <= 449.dp && width >= 447.dp, "Start VIP is $width wide")
        val plan = compose.onNodeWithTag("plan-yearly").getBoundsInRoot()
        assertEquals(start.left, plan.left)
    }

    @Test
    fun scannerCleanInEveryState() {
        compose.enableAccessibilityChecks(scannerValidator())
        show()
        val states = listOf<Pair<PricesUi, PurchaseUi>>(
            PricesUi.Loading to PurchaseUi.Idle,
            PricesUi.Failed to PurchaseUi.Idle,
            ready to PurchaseUi.Idle,
            ready to PurchaseUi.Launching(Products.VIP_YEARLY),
            ready to PurchaseUi.Pending(Products.VIP_YEARLY),
            ready to PurchaseUi.Pending(Products.REMOVE_ADS),
            ready to PurchaseUi.Failed,
        )
        for ((p, u) in states) {
            prices = p
            purchase = u
            runCatching {
                compose.waitForIdle()
                compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
            }.onFailure {
                A11yTestSupport.shell("screencap -p /data/local/tmp/a11y-paywall.png")
                throw AssertionError("$p $u", it)
            }
        }
        owned = Entitlements(removeAds = true)
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
    }
}
