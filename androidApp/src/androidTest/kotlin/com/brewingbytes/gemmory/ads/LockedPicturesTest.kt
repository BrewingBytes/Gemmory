package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.Tile
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
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
 * The locked bundled pictures through the real app (#32): the locked label and TalkBack text, a rewarded video, the
 * offline and no-fill copy, the daily grace unlock, VIP showing everything unlocked, and a VIP lapse (video unlocks
 * and started pictures stay open, the rest lock again), and the Pictures order: open pictures first (#58). The ad SDK is a fake; billing is the debug fake backend.
 */
@RunWith(AndroidJUnit4::class)
class LockedPicturesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val billing = BillingBackends.fake
    private val ads = FakeAds()
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** Rewarded videos that load, fail or play as the test says. Consent always allows ads. */
    private class FakeAds : AdGateway {
        var load: RewardedLoad = RewardedLoad.Loaded
        var online = true
        override val sdkReady = true

        override fun online() = online

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded() = load

        /** Watched to the end, after the sheet has switched to its "showing" phase, as the real SDK would. */
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
            if (load != RewardedLoad.Loaded) return false
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
        app.resetMonetizationForTest(ads)
        // No picture of the day (#34): these tests pin which pictures are locked and where they sit.
        app.resetTodaysForTest(enabled = false)
        // An empty catalog from a fake source (#108): the live pictures/v1 drops would add tiles and move the order.
        app.resetRemoteForTest(FakeSource(TestCatalog()), listOf(TestCatalog.KEY))
        setVip(false)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        setVip(false)
        clearState()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetRemoteForTest()
    }

    /** No canvases, no unlocks and an unused grace today. */
    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.LEDGER_DAILY).delete()
        File(context.filesDir, StorageLayout.LEDGER_ADS).delete()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setVip(vip: Boolean) {
        billing.reset()
        if (vip) billing.own(Products.VIP_YEARLY)
        runBlocking { app.billing.refresh() }
        compose.waitUntil("vip=$vip", 5_000) { app.monetization.entitlements.value.vip == vip }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("tile-bundled-starter"))
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(matcher.description, timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun title(id: String) = BundledPictures.byId(id)!!.title

    private fun scrollTo(id: String) {
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag("tile-bundled-$id"))
    }

    /** The tile reads as locked to TalkBack and shows the "Watch to unlock" label (design D14). */
    private fun assertLocked(id: String) {
        scrollTo(id)
        waitFor(hasTestTag("tile-bundled-$id") and hasContentDescription("${title(id)}, locked, watch a video to unlock"))
        compose.onNode(hasTestTag("tile-bundled-$id") and hasAnyDescendant(hasTestTag("locked-label")), useUnmergedTree = true)
            .assertExists()
    }

    private fun assertOpen(id: String) {
        scrollTo(id)
        waitFor(hasTestTag("tile-bundled-$id") and hasContentDescription("${title(id)}, 0% done"))
        compose.onNode(hasTestTag("tile-bundled-$id") and hasAnyDescendant(hasTestTag("locked-label")), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    private fun tap(id: String) {
        scrollTo(id)
        compose.onNodeWithTag("tile-bundled-$id").performClick()
    }

    private fun text(s: String) = compose.onNodeWithText(s, useUnmergedTree = true)

    private fun waitForCanvas() = waitFor(hasTestTag("play-canvas"))

    private fun back() {
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag("tile-bundled-starter"))
    }

    @Test
    fun freePicturesOpenAndTheRestAreLocked() {
        launch()
        assertOpen("lemons")
        assertLocked("wave")
        assertLocked("the-kiss")
        assertLocked("misty-pier")
        assertOpen("puppy")
        tap("lemons")
        waitForCanvas()
    }

    @Test
    fun aVideoUnlocksThePictureForGoodAndOpensIt() {
        launch()
        tap("wave")
        waitFor(hasText("Watch video"))
        text("Watch a short video to unlock this picture for good.").assertExists()
        compose.onNodeWithTag("rewarded-primary").assertIsEnabled().performClick()
        waitFor(hasText("Unlocked"))
        waitForCanvas()
        assertTrue("wave" in app.monetization.unlocks.unlocked.value)

        // Not started, still unlocked: no video the second time.
        back()
        assertOpen("wave")
        assertLocked("sunrise")
    }

    /** The Pictures tab's tiles, as the grid lists them. */
    private fun pictureTiles(): List<Tile> {
        var tiles = emptyList<Tile>()
        scenario.onActivity { tiles = ViewModelProvider(it)[GalleryViewModel::class.java].state.value.pictures }
        return tiles
    }

    private fun pictureOrder(): List<String> = pictureTiles().map { it.ref.id }

    /** #58: the starter and the free pictures are the first 7 tiles; a video unlock moves up on the next visit. */
    @Test
    fun openPicturesComeFirstAndAnUnlockMovesUpOnTheNextVisit() {
        launch()
        val free = BundledPictures.ALL.filter { it.id in BundledPictures.FREE }.map { it.id }
        compose.waitUntil("pictures listed", 10_000) { pictureOrder().size == BundledPictures.ALL.size }
        assertEquals(listOf("starter") + free, pictureOrder().take(7))
        assertTrue(pictureOrder().indexOf("wave") > 6)

        tap("wave")
        waitFor(hasText("Watch video"))
        compose.onNodeWithTag("rewarded-primary").performClick()
        waitForCanvas()
        back()
        compose.waitUntil("wave moved up", 10_000) { pictureOrder().indexOf("wave") == 1 }
        assertEquals(listOf("starter", "wave") + free, pictureOrder().take(8))
    }

    @Test
    fun offlineNeverGivesGraceThenNoFillGivesOneGraceUnlockADay() {
        ads.online = false
        ads.load = RewardedLoad.Failed(LoadFailure.NETWORK)
        launch()
        tap("wave")
        waitFor(hasText("You're offline, connect to watch a video"))
        text("Get VIP: no videos needed").assertExists()

        // Back online, but the ad network has nothing: today's grace.
        ads.online = true
        ads.load = RewardedLoad.Failed(LoadFailure.NO_FILL)
        text("Try again").performClick()
        waitFor(hasText("No video right now. This one's on us today."))
        text("Or get VIP: every gallery picture unlocked").assertExists()
        text("Unlock picture").performClick()
        waitFor(hasText("Unlocked"))
        waitForCanvas()
        assertTrue("wave" in app.monetization.unlocks.unlocked.value)
        assertFalse(runBlocking { app.monetization.graceAvailable(RewardType.PICTURE) })

        // The grace is used for today: the next locked picture only offers to try again.
        back()
        tap("sunrise")
        waitFor(hasText("No video right now. Try again in a bit."))
        text("Unlock picture").assertDoesNotExist()
        assertFalse("sunrise" in app.monetization.unlocks.unlocked.value)
    }

    @Test
    fun vipOpensEverythingAndALapseLocksOnlyWhatWasNeitherWatchedNorStarted() {
        app.monetization.unlocks.unlock("wave")
        setVip(true)
        launch()
        for (id in listOf("wave", "sunrise", "the-kiss", "misty-pier")) assertOpen(id)

        // VIP opens a locked picture straight away; one gem makes it started.
        tap("the-kiss")
        waitForCanvas()
        compose.waitUntil("canvas ready", 10_000) { playUi() is PlayUi.Ready }
        scenario.onActivity { (playUi() as PlayUi.Ready).session.placeNext() }
        back()
        // The lapse comes after the gallery has re-read the canvases, so its order (VIP: the plain ramp) holds while
        // the test scrolls; one read after the lapse would list the started picture with the open ones (#58).
        compose.waitUntil("the-kiss started", 10_000) { pictureTiles().any { it.ref.id == "the-kiss" && it.started } }

        setVip(false)
        assertOpen("wave") // watched a video: unlocked for good
        assertOpen("the-kiss") // started under VIP: stays open
        assertLocked("sunrise")
        assertLocked("misty-pier")
        tap("the-kiss")
        waitForCanvas()
    }

    private fun playUi(): PlayUi? {
        var ui: PlayUi? = null
        scenario.onActivity { ui = ViewModelProvider(it)[PlayViewModel::class.java].ui }
        return ui
    }
}
