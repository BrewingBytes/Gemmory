package com.brewingbytes.gemmory.remote

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.MYSTERY_LABEL_TAG
import com.brewingbytes.gemmory.gallery.TODAY_TAG
import com.brewingbytes.gemmory.gallery.seedFirstFinish
import com.brewingbytes.gemmory.gallery.Tile
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.play.SHARE_PROGRESS_TAG
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mystery picture (T43, D23) through the real app: on its date no surface shows its title or art (Today card, tile,
 * canvas top bar, TalkBack), the canvas has no Peek or Share progress before the finish, and the completion screen
 * names it. The catalog is read as an app that knows mysteries (versionCode 16), signed by a test key.
 */
@RunWith(AndroidJUnit4::class)
class MysteryFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val catalog = TestCatalog()
    private val source = FakeSource(catalog)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val zone = TimeZone.currentSystemDefault()

    private object NoAds : AdGateway {
        override val sdkReady = true
        override fun online() = true
        override suspend fun ensureConsent(activity: Activity) = true
        override suspend fun start() = Unit
        override fun preloadRewarded() = Unit
        override suspend fun loadRewarded() = RewardedLoad.Loaded
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false
        override fun preloadInterstitial() = Unit
        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }

    /** A fixed date far from the real one. */
    private val dropDay = LocalDate(2027, 3, 10)

    @Before
    fun setUp() {
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        clearState()
        // T45 (DR4): the Today card and the Tomorrow line wait for the player's first finish.
        app.seedFirstFinish()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val base = LocalDateTime(dropDay, LocalTime(9, 0)).toInstant(zone).toEpochMilliseconds()
        val since = SystemClock.elapsedRealtime()
        app.testNowMillis = { base + SystemClock.elapsedRealtime() - since }
        app.resetMonetizationForTest(NoAds)
        app.resetRemoteForTest(source, listOf(TestCatalog.KEY), appVersion = RemoteCatalogs.MYSTERY_MIN_APP)
        app.resetTodaysForTest()
    }

    @After
    fun tearDown() {
        app.resetStreakForTest()
        if (::scenario.isInitialized) scenario.close()
        clearState()
        app.testNowMillis = null
        app.resetRemoteForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TodaysPictures.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private val lantern = CanvasRef(CanvasKind.REMOTE, "lantern")

    private fun publish() {
        catalog.add("owl", CanvasSize.QUICK, locked = false)
        catalog.add("lantern", CanvasSize.QUICK, locked = true, availableFrom = dropDay.toString(), mystery = true)
        assertEquals(CheckOutcome.UPDATED, runBlocking { app.remote.check(force = true) })
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("tile-bundled-starter"))
        compose.waitUntil("gallery loaded", 10_000) { tiles().isNotEmpty() }
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(matcher.description, timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun tiles(): List<Tile> {
        var tiles = emptyList<Tile>()
        scenario.onActivity { tiles = ViewModelProvider(it)[GalleryViewModel::class.java].state.value.pictures }
        return tiles
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    /** No text, content description or custom action anywhere on screen names the picture. */
    private fun assertNothingNamesIt(where: String) {
        val nodes = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        for (node in nodes) {
            val said = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
            assertTrue(said.none { "Lantern" in it }, "$where: $said")
        }
    }

    @Test
    fun nothingShowsTheMysteryUntilItsFinished() {
        publish()
        launch()
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's mystery picture, new today, hidden until finished, Quick, free to start today"))
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(tileTag(lantern)))
        waitFor(hasTestTag(tileTag(lantern)) and hasContentDescription("Today's mystery picture, hidden until finished, 0% done"))
        compose.onNode(hasTestTag(MYSTERY_LABEL_TAG), useUnmergedTree = true).assertExists()
        val tile = tiles().single { it.ref == lantern }
        assertTrue(tile.mystery)
        assertTrue(!tile.isNew, "Mystery takes New's place")
        assertNothingNamesIt("gallery")

        // The canvas: the neutral name, no Peek, no Share progress, past the share minimum too.
        compose.onNodeWithTag(TODAY_TAG).performClick()
        compose.waitUntil("canvas", 10_000) { session()?.viewport?.isLaidOut == true }
        val session = session()!!
        assertEquals("Today's mystery picture", session.hiddenAs)
        instrumentation.runOnMainSync { repeat(session.cellCount / 2) { session.placeNext() } }
        compose.waitForIdle()
        waitFor(hasText("Today's mystery picture"))
        assertTrue(compose.onAllNodes(hasContentDescription("Peek")).fetchSemanticsNodes().isEmpty(), "no Peek eye")
        assertTrue(compose.onAllNodes(hasTestTag(SHARE_PROGRESS_TAG)).fetchSemanticsNodes().isEmpty(), "no Share progress")
        val canvasActions = compose.onNodeWithTag("play-canvas").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertTrue("Peek at picture" !in canvasActions, "$canvasActions")
        assertNothingNamesIt("canvas")

        // The finish is the reveal: the completion meta line names it. The celebration keeps Compose busy, so the
        // test clock is stepped by hand, as FinishCelebrationTest does.
        compose.mainClock.autoAdvance = false
        instrumentation.runOnMainSync { while (!session.isFinished) session.placeNext() }
        val deadline = System.currentTimeMillis() + 15_000
        while (!(exists(hasTestTag("completion-fresh")) && exists(hasText("Lantern", substring = true)))) {
            assertTrue(System.currentTimeMillis() < deadline, "the completion screen names it")
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(16)
        }
    }

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
