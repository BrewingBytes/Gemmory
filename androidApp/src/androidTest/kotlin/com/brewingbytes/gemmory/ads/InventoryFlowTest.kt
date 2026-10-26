package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.gallery.INVENTORY_TAG
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.policy.InventoryCodec
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The inventory (#38), earn → hold → spend, in the app: fills from a video watched from the gallery's Inventory sheet,
 * spent by a fill on a canvas; an unlock token spent on a locked picture's sheet; the 5-day gift wheel; nothing for
 * VIP. Ads are a fake that always fills; the inventory file is seeded before the app reads it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class InventoryFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val billing = BillingBackends.fake
    private val ads = FakeAds()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val photo = CanvasRef(CanvasKind.PHOTO, "inv")

    private class FakeAds : AdGateway {
        override val sdkReady = true

        override fun online() = true

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded() = RewardedLoad.Loaded

        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
            Handler(Looper.getMainLooper()).post {
                onEarned()
                onClosed(true)
            }
            return true
        }

        override fun preloadInterstitial() = Unit

        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }

    @Before
    fun setUp() {
        assertTrue(BuildConfig.FAKE_BILLING, "connected tests run on the fake billing backend")
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetTodaysForTest(enabled = false)
        setVip(false)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        setVip(false)
        clearState()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.LEDGER_DAILY).delete()
        File(context.filesDir, StorageLayout.LEDGER_ADS).delete()
        File(context.filesDir, StorageLayout.INVENTORY).delete()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Writes [state] as the inventory file, then makes the app read it again (with the fake ads). */
    private fun seed(state: InventoryState) {
        File(context.filesDir, StorageLayout.INVENTORY).apply {
            parentFile?.mkdirs()
            writeBytes(InventoryCodec.encode(state))
        }
        app.resetMonetizationForTest(ads)
        compose.waitUntil(5_000) { app.monetization.inventory.value == state }
    }

    private fun setVip(vip: Boolean) {
        billing.reset()
        if (vip) billing.own(Products.VIP_YEARLY)
        runBlocking { app.billing.refresh() }
        compose.waitUntil(5_000) { app.monetization.entitlements.value.vip == vip }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("tile-bundled-starter"))
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun fillsFromAVideoAreSpentOneUseAtATimeOnACanvas() {
        seed(InventoryState())
        runBlocking { app.store.create(photo, pattern()) }
        launch()
        compose.onNodeWithTag(INVENTORY_TAG).performClick()
        waitFor(hasTestTag("inventory-fills") and hasContentDescription("Fills: 0", substring = true))
        compose.onNodeWithTag("inventory-watch").performClick()
        waitFor(hasTestTag("rewarded-primary") and hasText("Watch video"))
        compose.onNodeWithTag("rewarded-primary").performClick()
        waitFor(hasText("5 fills added"))
        compose.waitUntil(5_000) { app.monetization.inventory.value.fills == 5 }

        // On a canvas the tool well shows the 5; TalkBack's "Fill region at next gem" spends one.
        compose.onNode(hasText("My Photos")).performClick()
        waitFor(hasTestTag("tile-photo-inv"))
        compose.onNodeWithTag("tile-photo-inv").performClick()
        waitFor(hasTestTag("tool-well") and hasContentDescription("Tool: Tap, 5 fills"))
        compose.onNodeWithTag("play-canvas").performCustomAccessibilityActionWithLabel("Fill region at next gem")
        compose.waitUntil(5_000) { app.monetization.inventory.value.fills == 4 }
        waitFor(hasTestTag("tool-well") and hasContentDescription("Tool: Tap, 4 fills"))
    }

    @Test
    fun anUnlockTokenOpensALockedPictureForGood() {
        seed(InventoryState(tokens = 1))
        launch()
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag("tile-bundled-wave"))
        compose.onNodeWithTag("tile-bundled-wave").performClick()
        waitFor(hasTestTag("rewarded-token") and hasText("Use an unlock token · 1 left"))
        compose.onNodeWithTag("rewarded-token").performClick()
        waitFor(hasTestTag("play-canvas"))
        assertTrue("wave" in app.monetization.unlocks.unlocked.value)
        assertEquals(0, app.monetization.inventory.value.tokens)
    }

    @Test
    fun theFiveDayGiftWheelAddsOnePrize() {
        seed(InventoryState(spinsEarned = 1))
        launch()
        waitFor(hasTestTag(INVENTORY_TAG) and hasContentDescription("Inventory, gift spin waiting"))
        compose.onNodeWithTag(INVENTORY_TAG).performClick()
        compose.onNodeWithTag("inventory-spin").performClick()
        waitFor(hasTestTag("wheel") and hasContentDescription("Gift wheel", substring = true))
        compose.onNodeWithTag("wheel-spin").performClick()
        waitFor(hasTestTag("wheel-done"), timeout = 15_000)
        val after = app.monetization.inventory.value
        assertEquals(0, after.spinsWaiting)
        assertEquals(1, listOf(after.fills > 0, after.canvases > 0, after.tokens > 0, after.freezes > 0).count { it }, "exactly one prize: $after")
        val result = compose.onNodeWithTag("wheel-result", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertTrue(result.endsWith("added to your inventory."), result)
        compose.onNodeWithTag("wheel-done").performClick()
        waitFor(hasTestTag(INVENTORY_TAG) and hasContentDescription("Inventory"))
    }

    /** #102: the freeze row shows what's held, and at 2 held the wheel's freeze slice reads 5 fills (E7). */
    @Test
    fun streakFreezesShowInTheInventoryAndFillTheWheelAtTheCap() {
        seed(InventoryState(spinsEarned = 1, freezes = 2))
        launch()
        compose.onNodeWithTag(INVENTORY_TAG).performClick()
        waitFor(hasTestTag("inventory-freezes") and hasContentDescription("Streak freezes: 2. Covers a missed day after your day off. Hold up to 2."))
        compose.onNodeWithTag("inventory-spin").performClick()
        waitFor(
            hasTestTag("wheel") and
                hasContentDescription("Gift wheel. 5 fills 45%, 10 fills 20%, 1 photo canvas 15%, 1 unlock token 10%, 5 fills 10%"),
        )
        compose.onNodeWithTag("wheel-spin").performClick()
        waitFor(hasTestTag("wheel-done"), timeout = 15_000)
        assertEquals(2, app.monetization.inventory.value.freezes, "never a 3rd")
    }

    @Test
    fun vipHasNoInventory() {
        seed(InventoryState(fills = 3, tokens = 1, spinsEarned = 1))
        setVip(true)
        launch()
        assertFalse(exists(INVENTORY_TAG))
        compose.waitForIdle()
        assertFalse(exists("inventory-fills"))
        assertTrue(compose.onNodeWithTag("tile-bundled-starter").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    /** 8×6, two colors in columns, so a region is a column. */
    private fun pattern() = Pattern(
        width = 8,
        height = 6,
        palette = List(2) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x303030), Lab(it * 30.0, 0.0, 0.0)) },
        cells = IntArray(48) { it % 2 },
    )
}
