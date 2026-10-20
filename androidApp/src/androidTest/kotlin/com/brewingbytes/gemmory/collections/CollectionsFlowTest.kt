package com.brewingbytes.gemmory.collections

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.gallery.ALL_COLLECTIONS_TAG
import com.brewingbytes.gemmory.gallery.COLLECTION_LINE_TAG
import com.brewingbytes.gemmory.gallery.COMING_TAG
import com.brewingbytes.gemmory.gallery.collectionTag
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_NOT_NOW_TAG
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_YES_TAG
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
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
import kotlin.test.assertTrue

/**
 * Collections (T37) through the real app, with a catalog from a fake source signed by a test key: no rows before the
 * first finish, the row's progress and "more coming" well, a reward return that completes a collection with no
 * interstitial or reminder ask, the keepsake and token once, offline completion with a pending keepsake that arrives
 * on the next check, and one row with the short well at 200% font on a 360dp phone. Weekly events (T38): 3 finishes in
 * the window earn the exclusive on a reward return; after the window the row is an ordinary collection's.
 */
@RunWith(AndroidJUnit4::class)
class CollectionsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val catalog = TestCatalog()
    private val source = FakeSource(catalog)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val gateway = CountingAds()

    /** Shows nothing, counts every interstitial the app asks for. */
    private class CountingAds : AdGateway {
        var interstitials = 0
        override val sdkReady = true
        override fun online() = true
        override suspend fun ensureConsent(activity: Activity) = true
        override suspend fun start() = Unit
        override fun preloadRewarded() = Unit
        override suspend fun loadRewarded() = RewardedLoad.Loaded
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false
        override fun preloadInterstitial() = Unit
        override fun showInterstitial(activity: Activity, onShown: () -> Unit): Boolean {
            interstitials++
            return false
        }
    }

    @Before
    fun setUp() {
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetMonetizationForTest(gateway)
        app.resetRemoteForTest(source, listOf(TestCatalog.KEY))
        // No Today's picture: it would take the card above the rows and change what's on the first screen.
        app.resetTodaysForTest(enabled = false)
        app.resetCollectionsForTest()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        A11yTestSupport.shell("settings put system font_scale 1.0")
        A11yTestSupport.shell("wm size reset")
        A11yTestSupport.shell("wm density reset")
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        app.resetRemoteForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetCollectionsForTest()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        File(context.filesDir, StorageLayout.INVENTORY).delete()
        for (file in listOf(PictureUnlocks.FILE, TodaysPictures.FILE, CollectionPrefs.FILE, ReminderPrefs.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun exists(tag: String) = exists(hasTestTag(tag))

    /** On a timeout, the screen is saved to /data/local/tmp/collections-<what>.png for a look at what showed instead. */
    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/collections-${what.replace(' ', '-')}.png")
        throw e
    }

    /** The row's heading, one TalkBack node: "Seaside, 0 of 3 finished, 1 more coming". */
    private fun heading(text: String) = exists(androidx.compose.ui.test.hasContentDescription(text))

    /**
     * Seeds a photo canvas for [finishPhoto], runs the catalog check now (a check that leaves a grid out ends
     * incomplete and doesn't reload a showing gallery) and opens the app.
     */
    private fun launch(photo: String? = "warm-up") {
        photo?.let { runBlocking { app.store.create(CanvasRef(CanvasKind.PHOTO, it), A11yTestSupport.pattern(8, 6, 3)) } }
        runBlocking { app.remote.check(force = true) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("the starter tile") { exists("tile-bundled-starter") }
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    /** Opens [ref] from the gallery grid, places every gem, and goes back with Done: a fresh finish's return. */
    private fun finish(ref: CanvasRef) {
        val grid = compose.onNodeWithTag("gallery-grid")
        waitFor("${ref.id} in the grid") { runCatching { grid.performScrollToKey("${ref.kind}/${ref.id}") }.isSuccess }
        compose.onNodeWithTag(tileTag(ref)).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        done()
    }

    /** Finishes the photo [launch] seeded, the player's first finish (D5), and answers the reminder ask. */
    /** Done on the completion screen; a tap during its entrance can be missed, so it's tapped until the gallery shows. */
    private fun done() {
        waitFor("the completion screen") { exists("completion-done") }
        waitFor("the gallery") {
            if (!exists("gallery-grid") && exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            exists("gallery-grid")
        }
    }

    private fun finishPhoto() {
        val photo = CanvasRef(CanvasKind.PHOTO, "warm-up")
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photo") { exists(tileTag(photo)) }
        compose.onNodeWithTag(tileTag(photo)).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        done()
        // The first fresh finish asks for reminders (T35); "Not now", so the next returns are ordinary ones.
        waitFor("the reminder ask") { exists(REMINDER_ASK_YES_TAG) }
        compose.onNodeWithTag(REMINDER_ASK_NOT_NOW_TAG).performClick()
        waitFor("the ask closed") { !exists(REMINDER_ASK_YES_TAG) }
        compose.onNode(hasText("Pictures")).performClick()
    }

    private fun remote(id: String) = CanvasRef(CanvasKind.REMOTE, id)

    private fun line(id: String): String {
        val grid = compose.onNodeWithTag("gallery-grid")
        grid.performScrollToKey(collectionTag(id))
        val node = compose.onAllNodes(hasTestTag(COLLECTION_LINE_TAG)).fetchSemanticsNodes().first()
        return node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
    }

    /** The reminder ask (T35), if this return shows it: "Not now". */
    private fun notNowIfAsked() {
        compose.waitForIdle()
        if (exists(REMINDER_ASK_YES_TAG)) {
            compose.onNodeWithTag(REMINDER_ASK_NOT_NOW_TAG).performClick()
            waitFor("the ask closed") { !exists(REMINDER_ASK_YES_TAG) }
        }
    }

    /** A week of 7 drops, the first on [firstDay] days from today, and its exclusive. */
    private fun week(firstDay: Long) {
        val today = java.time.LocalDate.now()
        (1..7).forEach { catalog.add("w$it", locked = false, availableFrom = today.plusDays(firstDay + it - 1).toString()) }
        catalog.add("golden-field", rewardOf = "golden-week")
        catalog.collection("golden-week", *(1..7).map { "w$it" }.toTypedArray(), title = "Golden Week", event = true)
    }

    /**
     * T38: in its window the event row shows its own line and the "one a day" well; the 3rd finish in the window is a
     * reward return (no interstitial, no ask) that earns the exclusive, for good and with no token.
     */
    @Test
    fun threeFinishesInTheWindowEarnTheExclusiveOnARewardReturn() {
        week(firstDay = -2)
        launch(photo = null)
        waitFor("the catalog", 15_000) { exists(tileTag(remote("w3"))) }
        // The first finish shows the rows (D5) and asks for reminders.
        finish(remote("w1"))
        notNowIfAsked()
        waitFor("the event row") { exists(collectionTag("golden-week")) }
        assertTrue(heading("Golden Week, 1 of 7 finished, 4 more coming"))
        assertEquals("One new picture a day. Finish any 3 this week for a keepsake picture.", line("golden-week"))
        assertTrue(!exists(tileTag(remote("golden-field"))), "hidden until earned")
        finish(remote("w2"))
        val before = gateway.interstitials
        // The 3rd fresh finish would ask again after "Not now"; this one earns the exclusive.
        finish(remote("w3"))
        waitFor("the earned line") { runCatching { line("golden-week") }.getOrNull()?.startsWith("Keepsake earned") == true }
        assertEquals("Keepsake earned: Golden field.", line("golden-week"))
        assertEquals(before, gateway.interstitials, "no interstitial on the reward return")
        assertTrue(!exists(REMINDER_ASK_YES_TAG), "no reminder ask on the reward return")
        assertTrue("golden-field" in app.monetization.unlocks.unlocked.value)
        assertEquals(0, app.monetization.inventory.value.tokens, "the token is for all 7")
        waitFor("the exclusive in the grid") { runCatching { compose.onNodeWithTag("gallery-grid").performScrollToKey("REMOTE/golden-field") }.isSuccess }
    }

    /** T38: after its window the event is an ordinary collection row, and finishes then don't earn the exclusive. */
    @Test
    fun afterItsWindowTheEventIsAnOrdinaryCollection() {
        week(firstDay = -12)
        launch(photo = null)
        waitFor("the catalog", 15_000) { exists(tileTag(remote("w7"))) }
        finish(remote("w1"))
        notNowIfAsked()
        finish(remote("w2"))
        notNowIfAsked()
        finish(remote("w3"))
        notNowIfAsked()
        waitFor("the row") { exists(collectionTag("golden-week")) }
        assertEquals("Finish all 7 for an unlock token.", line("golden-week"))
        assertTrue("golden-field" !in app.monetization.unlocks.unlocked.value, "finishes after the window don't count")
        assertTrue(!exists(tileTag(remote("golden-field"))))
    }

    /** D5: no rows and no link before the first finish; then the row with its count and the "more coming" well. */
    @Test
    fun noRowsBeforeTheFirstFinishThenTheRowWithItsWell() {
        catalog.add("shell", locked = false)
        catalog.add("tide", locked = false)
        // A member dated far ahead: in the catalog, stored, but not on the gallery yet.
        catalog.add("dune", locked = false, availableFrom = "2099-01-01")
        catalog.collection("seaside", "shell", "tide", "dune", title = "Seaside")
        launch()
        waitFor("the catalog", 15_000) { exists(tileTag(remote("shell"))) }
        assertTrue(!exists(collectionTag("seaside")) && !exists(ALL_COLLECTIONS_TAG), "no rows before the first finish")

        finishPhoto()
        waitFor("the row") { exists(collectionTag("seaside")) }
        assertTrue(heading("Seaside, 0 of 3 finished, 1 more coming"))
        assertTrue(exists(COMING_TAG))
        assertEquals("Finish all 3 for an unlock token.", line("seaside"), "no keepsake in this catalog")
    }

    /**
     * D7: the return that completes a collection shows no interstitial and no reminder ask (it waits for the next
     * finish); the keepsake joins the grid and the strip, and the token is added once.
     */
    @Test
    fun aRewardReturnHasNoInterruptionAndAddsTheKeepsakeOnce() {
        catalog.add("shell", locked = false)
        catalog.add("harbour-lights", rewardOf = "seaside")
        catalog.collection("seaside", "shell", "lemons", title = "Seaside")
        launch()
        waitFor("the catalog", 15_000) { exists(tileTag(remote("shell"))) }
        finishPhoto()
        finish(CanvasRef(CanvasKind.BUNDLED, "lemons"))
        val before = gateway.interstitials
        // The 3rd fresh finish would ask again after "Not now" (ReminderAsk.SECOND_AT); this one completes Seaside.
        finish(remote("shell"))
        waitFor("the keepsake line") { runCatching { line("seaside") }.getOrNull()?.startsWith("Keepsake added") == true }
        assertEquals("Keepsake added: Harbour lights. 1 unlock token added.", line("seaside"))
        assertEquals(before, gateway.interstitials, "no interstitial on the reward return")
        assertTrue(!exists(REMINDER_ASK_YES_TAG), "no reminder ask on the reward return")
        assertEquals(1, app.monetization.inventory.value.tokens)
        assertTrue("harbour-lights" in app.monetization.unlocks.unlocked.value)
        assertTrue(app.store.repository.dirOf(remote("harbour-lights")).isDirectory, "copied into its canvas")
        waitFor("the keepsake in the grid") { runCatching { compose.onNodeWithTag("gallery-grid").performScrollToKey("REMOTE/harbour-lights") }.isSuccess }

        // A relaunch settles again: nothing more is given.
        scenario.close()
        launch(photo = null)
        waitFor("the row") { exists(collectionTag("seaside")) }
        assertEquals(1, app.monetization.inventory.value.tokens)
    }

    /** States table: offline with a cached catalog still completes; a keepsake whose grid isn't here comes later. */
    @Test
    fun offlineCompletionWithAPendingKeepsakeThatArrivesLater() {
        catalog.add("shell", locked = false)
        val keepsake = catalog.add("harbour-lights", rewardOf = "seaside")
        catalog.collection("seaside", "shell", title = "Seaside")
        val grid = catalog.files.remove(keepsake.grid)!!
        launch()
        waitFor("the catalog", 15_000) { exists(tileTag(remote("shell"))) }
        source.offline = true
        finishPhoto()
        finish(remote("shell"))
        waitFor("the pending line") { runCatching { line("seaside") }.getOrNull()?.startsWith("Keepsake earned") == true }
        assertEquals("Keepsake earned. It arrives next time you're online.", line("seaside"))
        assertEquals(1, app.monetization.inventory.value.tokens, "the token at once")

        catalog.files[keepsake.grid] = grid
        source.offline = false
        scenario.close()
        launch(photo = null)
        waitFor("the keepsake") { runCatching { line("seaside") }.getOrNull()?.startsWith("Keepsake added") == true }
        assertEquals(1, app.monetization.inventory.value.tokens)
    }

    /** D4, D18: at 200% font on a 360dp phone one row shows, its well says "+1" and the link holds the other. */
    @Test
    fun oneRowWithTheShortWellAtTwoTimesFontOn360dp() {
        catalog.add("shell", locked = false)
        catalog.add("tide", locked = false)
        catalog.add("dune", locked = false, availableFrom = "2099-01-01")
        catalog.add("pine", locked = false)
        catalog.collection("forest", "pine", title = "Forest")
        catalog.collection("seaside", "shell", "tide", "dune", title = "Seaside")
        launch()
        waitFor("the catalog", 15_000) { exists(tileTag(remote("shell"))) }
        finishPhoto()
        waitFor("the row") { exists(collectionTag("seaside")) }
        assertTrue(exists(ALL_COLLECTIONS_TAG), "the older collection is in the sheet")
        scenario.close()

        A11yTestSupport.shell("wm size 1080x2400")
        A11yTestSupport.shell("wm density 480")
        A11yTestSupport.shell("settings put system font_scale 2.0")
        launch(photo = null)
        waitFor("the row") { exists(collectionTag("seaside")) }
        assertTrue(!exists(collectionTag("forest")), "one row above 1.5× font")
        assertTrue(heading("Seaside, 0 of 3 finished, 1 more coming"), "TalkBack unchanged at 200% (D18)")
        assertTrue(exists(COMING_TAG))
        compose.onNodeWithTag("gallery-grid").performScrollToKey(ALL_COLLECTIONS_TAG)
        compose.onNodeWithTag(ALL_COLLECTIONS_TAG).performClick()
        waitFor("the sheet") { exists(hasText("Collections")) && exists(collectionTag("forest")) }
    }
}
