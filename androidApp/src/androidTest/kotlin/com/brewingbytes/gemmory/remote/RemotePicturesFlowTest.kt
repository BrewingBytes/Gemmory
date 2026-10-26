package com.brewingbytes.gemmory.remote

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.NEW_LABEL_TAG
import com.brewingbytes.gemmory.gallery.Tile
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
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
 * Remote pictures (#43) through the real app, with the catalog served by a fake source and signed with a test key:
 * a published picture shows in the gallery (with its "New" label), plays, saves, survives a restart and being
 * offline; a tampered catalog is refused; a withdrawn picture stays only once started.
 */
@RunWith(AndroidJUnit4::class)
class RemotePicturesFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val catalog = TestCatalog()
    private val source = FakeSource(catalog)
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** No ads at all: nothing here finishes a picture or opens a locked one. */
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

    @Before
    fun setUp() {
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        app.resetMonetizationForTest(NoAds)
        app.resetTodaysForTest(enabled = false)
        app.resetRemoteForTest(source, listOf(TestCatalog.KEY))
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clearState()
        app.resetRemoteForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun ref(id: String) = CanvasRef(CanvasKind.REMOTE, id)

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("tile-bundled-starter"))
        compose.waitUntil("pictures listed", 10_000) { tiles().size > 1 }
    }

    private fun relaunch() {
        scenario.close()
        launch()
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(matcher.description, timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun tiles(): List<Tile> {
        var tiles = emptyList<Tile>()
        scenario.onActivity { tiles = ViewModelProvider(it)[GalleryViewModel::class.java].state.value.pictures }
        return tiles
    }

    private fun scrollTo(id: String) = compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(tileTag(ref(id))))

    private fun playUi(): PlayUi? {
        var ui: PlayUi? = null
        scenario.onActivity { ui = ViewModelProvider(it)[PlayViewModel::class.java].ui }
        return ui
    }

    /** Opens [id]'s tile and places [gems] gems, then goes back (the closing write runs). */
    private fun play(id: String, gems: Int) {
        scrollTo(id)
        compose.onNodeWithTag(tileTag(ref(id))).performClick()
        waitFor(hasTestTag("play-canvas"))
        compose.waitUntil("canvas ready", 10_000) { playUi() is PlayUi.Ready }
        scenario.onActivity { repeat(gems) { (playUi() as PlayUi.Ready).session.placeNext() } }
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag("tile-bundled-starter"))
    }

    @Test
    fun aPublishedPictureShowsPlaysSavesAndSurvivesARestartAndBeingOffline() {
        catalog.add("owl", CanvasSize.QUICK, locked = false)
        assertEquals(CheckOutcome.UPDATED, runBlocking { app.remote.check(force = true) })
        // Published the next day: new on this phone.
        catalog.add("fox-in-snow", CanvasSize.QUICK, locked = false)
        catalog.add("lanterns", CanvasSize.MEDIUM, locked = true)
        assertEquals(CheckOutcome.UPDATED, runBlocking { app.remote.check(force = true) })
        launch()

        scrollTo("fox-in-snow")
        waitFor(hasTestTag(tileTag(ref("fox-in-snow"))) and hasContentDescription("Fox in snow, new, 0% done"))
        compose.onNode(hasTestTag(tileTag(ref("fox-in-snow"))) and hasAnyDescendant(hasTestTag(NEW_LABEL_TAG)), useUnmergedTree = true).assertExists()
        scrollTo("lanterns")
        waitFor(hasTestTag(tileTag(ref("lanterns"))) and hasContentDescription("Lanterns, new, locked, watch a video to unlock"))
        scrollTo("owl")
        waitFor(hasTestTag(tileTag(ref("owl"))) and hasContentDescription("Owl, 0% done"))

        // The open remote picture sits with the open Quick pictures; the locked one after every open picture.
        val order = tiles().map { it.ref.id }
        assertTrue(order.indexOf("fox-in-snow") < order.indexOf("cherries"), "$order")
        assertTrue(order.indexOf("lanterns") > order.indexOf("cat"), "$order")

        play("fox-in-snow", gems = 5)
        compose.waitUntil("fox started", 10_000) { tiles().any { it.ref.id == "fox-in-snow" && it.started && !it.isNew } }

        // A restart with no network: a fresh store and catalog read from disk, and every check offline.
        source.offline = true
        scenario.close()
        app.resetRemoteForTest(source, listOf(TestCatalog.KEY))
        assertEquals(CheckOutcome.OFFLINE, runBlocking { app.remote.check(force = true) })
        launch()
        val fox = tiles().first { it.ref.id == "fox-in-snow" }
        assertTrue(fox.started && !fox.isNew, "progress kept, label gone")
        assertTrue(tiles().any { it.ref.id == "lanterns" }, "the cached catalog still lists the rest")
        scrollTo("fox-in-snow")
        compose.onNodeWithTag(tileTag(ref("fox-in-snow"))).performClick()
        compose.waitUntil("canvas ready", 10_000) { playUi() is PlayUi.Ready }
        var placed = 0
        scenario.onActivity { placed = (playUi() as PlayUi.Ready).session.placedCount }
        assertEquals(5, placed)
    }

    @Test
    fun aTamperedCatalogIsRefusedAndShowsNothing() {
        catalog.add("fox-in-snow", CanvasSize.QUICK, locked = false)
        val signed = catalog.files[RemotePictures.CATALOG]!!
        catalog.files[RemotePictures.CATALOG] = signed.decodeToString().replace("Fox in snow", "Fox in snow!").encodeToByteArray()
        assertEquals(CheckOutcome.FAILED, runBlocking { app.remote.check(force = true) })
        launch()
        assertFalse(tiles().any { it.ref.kind == CanvasKind.REMOTE })
        assertFalse(File(context.filesDir, StorageLayout.REMOTE_CATALOG).exists())
    }

    @Test
    fun aWithdrawnPictureStaysOnlyOnceStarted() {
        catalog.add("fox-in-snow", CanvasSize.QUICK, locked = false)
        catalog.add("owl", CanvasSize.QUICK, locked = false)
        runBlocking { app.remote.check(force = true) }
        launch()
        play("fox-in-snow", gems = 3)
        compose.waitUntil("fox started", 10_000) { tiles().any { it.ref.id == "fox-in-snow" && it.started } }

        catalog.remove("fox-in-snow")
        catalog.remove("owl")
        assertEquals(CheckOutcome.UPDATED, runBlocking { app.remote.check(force = true) })
        relaunch()
        val remote = tiles().filter { it.ref.kind == CanvasKind.REMOTE }
        assertEquals(listOf("fox-in-snow"), remote.map { it.ref.id })
        assertEquals("Fox in snow", remote.single().title)
    }
}
