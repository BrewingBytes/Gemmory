package com.brewingbytes.gemmory.remote

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
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
import com.brewingbytes.gemmory.gallery.NEW_TODAY_TAG
import com.brewingbytes.gemmory.gallery.TODAY_TAG
import com.brewingbytes.gemmory.gallery.seedFirstFinish
import com.brewingbytes.gemmory.gallery.TOAST_TAG
import com.brewingbytes.gemmory.gallery.TOMORROW_TAG
import com.brewingbytes.gemmory.gallery.Tile
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scheduled drops (T36) through the real app, with the catalog from a fake source signed by a test key and the app's
 * day clock moved by the test: a drop stored ahead stays hidden, the Today card shows its frosted Tomorrow line, the
 * gallery reloads at local midnight with the drop on the card ("New today") and free to start, it's locked the day
 * after, and an id from outside can't open it early.
 */
@RunWith(AndroidJUnit4::class)
class ScheduledDropsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val catalog = TestCatalog()
    private val source = FakeSource(catalog)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val zone = TimeZone.currentSystemDefault()

    /** The test's wall time: [base] plus the real time since it was set, so the gallery's midnight wait runs for real. */
    private var base = 0L
    private var since = 0L

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

    /** A fixed date far from the real one, so nothing here depends on the day it runs. */
    private val eve = LocalDate(2027, 3, 9)
    private val dropDay = eve.plus(DatePeriod(days = 1))

    private fun setClock(date: LocalDate, time: LocalTime) {
        base = LocalDateTime(date, time).toInstant(zone).toEpochMilliseconds()
        since = SystemClock.elapsedRealtime()
    }

    @Before
    fun setUp() {
        clearState()
        // T45 (DR4): the Today card and the Tomorrow line wait for the player's first finish.
        app.seedFirstFinish()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        setClock(eve, LocalTime(23, 59, 50))
        app.testNowMillis = { base + SystemClock.elapsedRealtime() - since }
        app.resetMonetizationForTest(NoAds)
        app.resetRemoteForTest(source, listOf(TestCatalog.KEY))
        app.resetTodaysForTest()
    }

    @After
    fun tearDown() {
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

    private fun ref(id: String) = CanvasRef(CanvasKind.REMOTE, id)

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

    /** Publishes a locked drop for [dropDay] beside an undated picture, and stores both (as a check weeks ahead would). */
    private fun publishDrop() {
        catalog.add("owl", CanvasSize.QUICK, locked = false)
        catalog.add("harbor-lights", CanvasSize.QUICK, locked = true, availableFrom = dropDay.toString())
        assertEquals(CheckOutcome.UPDATED, runBlocking { app.remote.check(force = true) })
    }

    @Test
    fun aDropOpensAtLocalMidnightOnTheTodayCardAndIsFreeThatDayOnly() {
        publishDrop()
        launch()
        // The eve: hidden in the grid, frosted on the Tomorrow line.
        assertFalse(tiles().any { it.ref.id == "harbor-lights" })
        waitFor(hasTestTag(TOMORROW_TAG) and hasContentDescription("Tomorrow's picture, opens tomorrow"))

        // Local midnight while the gallery shows: it reloads by itself.
        compose.waitUntil("the drop after midnight", 20_000) { tiles().any { it.ref.id == "harbor-lights" } }
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's picture, new today: Harbor lights, Quick, free to start today"))
        compose.onNode(hasTestTag(NEW_TODAY_TAG), useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasTestTag(TOMORROW_TAG)).fetchSemanticsNodes().isEmpty(), "no drop for the day after")
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(tileTag(ref("harbor-lights"))))
        waitFor(hasTestTag(tileTag(ref("harbor-lights"))) and hasContentDescription("Harbor lights, new, 0% done"))

        // The day after: its catalog lock applies (eng D4). Today's picture is off, since any locked picture may be
        // picked by date and would open without a video.
        scenario.close()
        setClock(dropDay.plus(DatePeriod(days = 1)), LocalTime(9, 0))
        app.resetTodaysForTest(enabled = false)
        launch()
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(tileTag(ref("harbor-lights"))))
        waitFor(hasTestTag(tileTag(ref("harbor-lights"))) and hasContentDescription("Harbor lights, new, locked, watch a video to unlock"))
    }

    /** The phone locked on the Gallery overnight (stopped, not destroyed): it reloads for the new day as it resumes. */
    @Test
    fun aGalleryComingBackAfterMidnightShowsTheNewDay() {
        setClock(eve, LocalTime(22, 0))
        publishDrop()
        launch()
        waitFor(hasTestTag(TOMORROW_TAG))
        assertTrue(compose.onAllNodes(hasTestTag(NEW_TODAY_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        scenario.moveToState(Lifecycle.State.CREATED)
        setClock(dropDay, LocalTime(8, 0))
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil("the drop after the night", 10_000) { tiles().any { it.ref.id == "harbor-lights" } }
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's picture, new today: Harbor lights, Quick, free to start today"))
        compose.onNode(hasTestTag(NEW_TODAY_TAG), useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(hasTestTag(TOMORROW_TAG)).fetchSemanticsNodes().isEmpty(), "the Tomorrow line moved on")
    }

    @Test
    fun theTomorrowLineSaysOpensTomorrowAndOpensNothing() {
        setClock(eve, LocalTime(20, 0))
        publishDrop()
        launch()
        compose.onNodeWithTag(TOMORROW_TAG).performClick()
        waitFor(hasTestTag(TOAST_TAG) and hasText("Opens tomorrow"))
        assertTrue(compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isEmpty(), "nothing opened")
        assertFalse(tiles().any { it.ref.id == "harbor-lights" })
    }

    @Test
    fun anIdFromOutsideCantOpenTomorrowsDrop() {
        setClock(eve, LocalTime(20, 0))
        publishDrop()
        assertNull(app.openableRef("harbor-lights"), "a crafted or stale id stays on the gallery")
        assertIs<CanvasStore.Opened.Damaged>(runBlocking { app.store.open(ref("harbor-lights")) })
        assertFalse(app.store.repository.dirOf(ref("harbor-lights")).isDirectory, "nothing copied in")
        assertEquals(ref("owl"), app.openableRef("owl"))
        setClock(dropDay, LocalTime(0, 5))
        assertEquals(ref("harbor-lights"), app.openableRef("harbor-lights"))
    }
}
