package com.brewingbytes.gemmory.gallery

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.Products
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Today's picture (#34) through the real app: the card above the Pictures grid with its copy and TalkBack line, a
 * locked picture that opens without the rewarded sheet, its first gem unlocking it for good, VIP's "Today's pick",
 * and no card when every picture is open. The ad SDK is a fake that fails the test if a video is ever asked for.
 */
@RunWith(AndroidJUnit4::class)
class TodaysPictureTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val billing = BillingBackends.fake
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        assertTrue(BuildConfig.FAKE_BILLING, "connected tests run on the fake billing backend")
        clearState()
        // T45 (DR4): the Today card and the Tomorrow line wait for the player's first finish.
        app.seedFirstFinish()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        NoVideos.asked = false
        app.resetMonetizationForTest(NoVideos)
        app.resetTodaysForTest()
        // An empty catalog from a fake source (#111): live pictures/v1 drops would add tiles and win today's pick.
        app.resetRemoteForTest(FakeSource(TestCatalog()), listOf(TestCatalog.KEY))
        setVip(false)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        setVip(false)
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetRemoteForTest()
    }

    /** No canvases, no unlocks, no pick yet today. */
    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.LEDGER_DAILY).delete()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TodaysPictures.FILE, Context.MODE_PRIVATE).edit().clear().commit()
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
        assertEquals(emptyList(), app.remote.listed().map { it.id }, "no remote pictures: the counts and picks below are bundled ones")
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(matcher.description, timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun gallery(): GalleryState {
        var state = GalleryState()
        scenario.onActivity { state = ViewModelProvider(it)[GalleryViewModel::class.java].state.value }
        return state
    }

    /** Today's card as the gallery last read it, once it has. */
    private fun card(): TodayCard {
        compose.waitUntil("today's card", 10_000) { gallery().today != null }
        return gallery().today!!
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor(hasTestTag("tile-bundled-starter"))
    }

    @Test
    fun aFreePlayerGetsALockedPictureFreeTodayAndItsFirstGemUnlocksIt() {
        launch()
        val card = card()
        val id = card.tile.ref.id
        val picture = assertNotNull(BundledPictures.byId(id))
        assertTrue(picture.locked && id !in BundledPictures.FREE, "today's picture is a locked one: $id")
        val size = Messages.size(context.strings, assertNotNull(BundledPictures.canvasSizeOf(id)))

        // The card: one TalkBack node with its copy, a quiet Start, and the finished picture.
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's picture: ${picture.title}, $size, free to start today"))
        compose.onNode(hasTestTag(TODAY_TAG) and hasAnyDescendant(hasText("Today's picture")), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag(TODAY_TAG) and hasAnyDescendant(hasText("$size · free today")), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag(TODAY_TAG) and hasAnyDescendant(hasText("Start")), useUnmergedTree = true).assertExists()
        assertTrue(card.free && !card.vip)

        // Its own tile is open today: no "Watch to unlock" label, and it sits with the open pictures.
        val order = gallery().pictures.map { it.ref.id }
        assertTrue(order.indexOf(id) <= BundledPictures.FREE.size + 1, "today's picture is among the open ones: $order")
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag("tile-bundled-$id"))
        compose.onNode(hasTestTag("tile-bundled-$id") and hasAnyDescendant(hasTestTag("locked-label")), useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(TODAY_TAG))

        // Tapping the card opens the canvas straight away: no rewarded sheet, no video.
        compose.onNodeWithTag(TODAY_TAG).performClick()
        compose.waitUntil("canvas", 10_000) { session()?.viewport?.isLaidOut == true }
        assertFalse(compose.onAllNodes(hasText("Watch video"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertFalse(NoVideos.asked, "no rewarded video was asked for")
        assertFalse(id in app.monetization.unlocks.unlocked.value, "opening alone grants nothing")

        // The first gem unlocks it for good.
        instrumentation.runOnMainSync { session()!!.placeNext() }
        compose.waitUntil("unlocked", 5_000) { id in app.monetization.unlocks.unlocked.value }

        // Back in the gallery: still today's picture, now with its progress and Continue.
        back()
        compose.waitUntil("started", 10_000) { gallery().today?.tile?.started == true }
        assertEquals(id, gallery().today!!.tile.ref.id, "the same picture all day")
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(TODAY_TAG))
        compose.onNode(hasTestTag(TODAY_TAG) and hasAnyDescendant(hasText("Continue")), useUnmergedTree = true).assertExists()
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's picture: ${picture.title}, ${gallery().today!!.tile.percent}% done"))
    }

    @Test
    fun vipSeesTodaysPickWithNothingToUnlock() {
        setVip(true)
        launch()
        val card = card()
        val picture = assertNotNull(BundledPictures.byId(card.tile.ref.id))
        val size = Messages.size(context.strings, assertNotNull(BundledPictures.canvasSizeOf(picture.id)))
        assertTrue(card.vip && !card.free)
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's pick: ${picture.title}, $size"))
        compose.onNode(hasTestTag(TODAY_TAG) and hasAnyDescendant(hasText("Today's pick")), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag(TODAY_TAG) and hasAnyDescendant(hasText("$size · free today")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(TODAY_TAG).performClick()
        compose.waitUntil("canvas", 10_000) { session()?.viewport?.isLaidOut == true }
        instrumentation.runOnMainSync { session()!!.placeNext() }
        compose.waitForIdle()
        assertFalse(picture.id in app.monetization.unlocks.unlocked.value, "VIP's pick writes no unlock")
    }

    @Test
    fun noCardWhenEveryPictureIsOpen() {
        BundledPictures.ALL.filter { it.locked }.forEach { app.monetization.unlocks.unlock(it.id) }
        launch()
        compose.waitUntil("pictures listed", 10_000) { gallery().pictures.size == BundledPictures.ALL.size }
        compose.waitForIdle()
        assertEquals(null, gallery().today)
        assertTrue(compose.onAllNodes(hasTestTag(TODAY_TAG)).fetchSemanticsNodes().isEmpty())
    }

    /** Consent allows ads, but any rewarded video asked for is noted (and never shown). */
    private object NoVideos : AdGateway {
        @Volatile
        var asked = false

        override val sdkReady = true

        override fun online() = true

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded(): RewardedLoad {
            asked = true
            return RewardedLoad.Loaded
        }

        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
            asked = true
            return false
        }

        override fun preloadInterstitial() = Unit

        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }
}
