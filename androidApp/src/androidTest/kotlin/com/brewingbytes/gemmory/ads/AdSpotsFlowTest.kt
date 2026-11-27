package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.LOAD_WAIT_MS
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.completion.BONUS_TAG
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.gallery.INVENTORY_TAG
import com.brewingbytes.gemmory.openMyPhotos
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.AdPolicyCodec
import com.brewingbytes.gemmory.policy.AdPolicyState
import com.brewingbytes.gemmory.policy.InventoryCodec
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * The 1.0.7 ad spots in the app (ad-spots.md), with a fake gateway that has every slot loaded and pays each video at
 * once: Today's gift popup and its Inventory row, "same again" on the 5-day wheel, the completion bonus from the 2nd
 * finish, and the rewarded interstitial's intro on the next return. Saves each state to /data/local/tmp/spots/ for the
 * PR (AGENTS.md: every PR shows what it changes).
 */
@RunWith(AndroidJUnit4::class)
class AdSpotsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val ads = FakeSlots()

    /** Every slot loaded; a shown video pays at once, and its slot loads again. */
    private class FakeSlots : AdGateway {
        val ready = MutableStateFlow(AdSlot.entries.toSet())
        override val slotsReady: StateFlow<Set<AdSlot>> = ready
        override val sdkReady = true

        override fun online() = true

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded() = RewardedLoad.Loaded

        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false

        override fun preloadInterstitial() = Unit

        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false

        override fun showSlot(activity: Activity, slot: AdSlot, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit): Boolean {
            Handler(Looper.getMainLooper()).post {
                onEarned()
                onClosed(true)
            }
            return true
        }
    }

    @Before
    fun setUp() {
        clearState()
        // A celebration would sit between Done and the gallery; this tests what the return shows.
        app.resetStreakForTest(celebrations = false)
        app.resetTodaysForTest(enabled = false)
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).putBoolean("formMissed", false).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        // The reminder ask (T35) would take the 1st finish's return; this tests the return's ad.
        reminders.ask = ReminderAskState.DONE
        A11yTestSupport.shell("mkdir -p /data/local/tmp/spots")
        // A player past the first session with one picture finished, and one 5-day spin waiting.
        write(StorageLayout.LEDGER_ADS, AdPolicyCodec.encode(AdPolicyState(launchedBefore = true, firstSessionOver = true, finishes = 1)))
        write(StorageLayout.INVENTORY, InventoryCodec.encode(InventoryState(spinsEarned = 1)))
        runBlocking {
            app.store.create(CanvasRef(CanvasKind.PHOTO, "spot1"), pattern())
            app.store.create(CanvasRef(CanvasKind.PHOTO, "spot2"), pattern())
        }
        app.resetMonetizationForTest(ads)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clearState()
        app.resetMonetizationForTest()
        app.resetStreakForTest()
        app.resetTodaysForTest()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        reminders.ask = ReminderAskState.NOT_ASKED
    }

    private val reminders get() = ReminderPrefs(context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE))

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.LEDGER_DAILY).delete()
        File(context.filesDir, StorageLayout.LEDGER_ADS).delete()
        File(context.filesDir, StorageLayout.INVENTORY).delete()
        // Its two finishes leave a streak day and a pending celebration, which the next test's launch would show.
        File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
        context.getSharedPreferences("streak", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun write(path: String, bytes: ByteArray) = File(context.filesDir, path).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }

    private fun pattern() = Pattern(
        width = 8,
        height = 6,
        palette = List(2) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x303030), Lab(it * 30.0, 0.0, 0.0)) },
        cells = IntArray(48) { it % 2 },
    )

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = LOAD_WAIT_MS) =
        compose.waitUntil(timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        Thread.sleep(800)
        compose.waitForIdle()
        A11yTestSupport.shell("screencap -p /data/local/tmp/spots/$name.png")
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    /** Opens My Photos' [id] canvas and places every gem. */
    private fun finish(id: String) {
        compose.openMyPhotos()
        waitFor(hasTestTag("tile-photo-$id"))
        compose.onNodeWithTag("tile-photo-$id").performClick()
        compose.waitUntil(LOAD_WAIT_MS) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        waitFor(hasTestTag("completion-fresh"))
    }

    @Test
    fun everySpotShowsAndPays() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("tile-bundled-starter"))

        // Today's gift: the popup on the day's first idle gallery visit (DR-D5/D8). "Not now" first: a watched video
        // starts the 2-min quiet, which would hold the intro below back.
        waitFor(hasText("Today's gift"), timeout = 15_000)
        shot("1-gift-popup")
        compose.onNodeWithTag("wheel-not-now").performClick()

        // The completion bonus, from the 2nd finish (DR-D3/D4), decided once Share and Done have faded in.
        finish("spot1")
        waitFor(hasTestTag(BONUS_TAG), timeout = 15_000)
        shot("2-completion-bonus")
        // Not watched: leaving shows the rewarded interstitial's intro (DR-D13), as the return's ad.
        compose.onNodeWithTag("completion-done").performClick()
        waitFor(hasTestTag(INTRO_SKIP_TAG), timeout = 15_000)
        shot("3-intro")
        val beforeIntro = app.monetization.inventory.value.fills
        compose.onNodeWithTag(INTRO_WATCH_TAG).performClick()
        waitFor(hasText("1 fill added to your inventory."), timeout = 15_000)
        shot("4-intro-granted")
        assertEquals(beforeIntro + 1, app.monetization.inventory.value.fills)

        // The next finish's bonus, watched: 1 fill, and its exit shows no ad of either format.
        finish("spot2")
        waitFor(hasTestTag(BONUS_TAG), timeout = 15_000)
        val fills = app.monetization.inventory.value.fills
        compose.onNodeWithTag(BONUS_TAG).performClick()
        waitFor(hasTestTag("$BONUS_TAG-granted"), timeout = 15_000)
        shot("5-completion-bonus-granted")
        assertEquals(fills + 1, app.monetization.inventory.value.fills)
        compose.onNodeWithTag("completion-done").performClick()
        waitFor(hasTestTag("gallery-grid"))
        Thread.sleep(2_000)
        assertTrue(compose.onAllNodes(hasTestTag(INTRO_SKIP_TAG)).fetchSemanticsNodes().isEmpty(), "no intro after a bonus")

        // Dismissed this morning: the Inventory row keeps Today's gift until midnight (DR-D7).
        compose.onNodeWithTag(INVENTORY_TAG).performClick()
        waitFor(hasTestTag("inventory-gift"))
        shot("6-inventory-gift-row")
        compose.onNodeWithTag("inventory-gift").performClick()
        waitFor(hasTestTag("wheel-spin"))
        compose.onNodeWithTag("wheel-spin").performClick()
        compose.waitUntil(15_000) { !app.adSpots.giftAllowed() }
        waitFor(hasText("added to your inventory.", substring = true), timeout = 15_000)
        shot("7-gift-won")
        compose.onNodeWithTag("wheel-done").performClick()

        // The 5-day wheel: "same again" once it lands (DR-D12).
        compose.onNodeWithTag(INVENTORY_TAG).performClick()
        waitFor(hasTestTag("inventory-spin"))
        compose.onNodeWithTag("inventory-spin").performClick()
        waitFor(hasTestTag("wheel-spin"))
        compose.onNodeWithTag("wheel-spin").performClick()
        waitFor(hasTestTag("wheel-again"), timeout = 15_000)
        shot("8-same-again")
        val before = app.monetization.inventory.value
        compose.onNodeWithTag("wheel-again").performClick()
        waitFor(hasTestTag("wheel-again-granted"), timeout = 15_000)
        shot("9-same-again-granted")
        val after = app.monetization.inventory.value
        val held = { s: InventoryState -> s.fills + s.canvases + s.tokens + s.freezes }
        assertTrue(held(after) > held(before), "the slice paid once more")
    }
}
