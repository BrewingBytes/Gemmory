package com.brewingbytes.gemmory.share

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.play.SHARE_PROGRESS_TAG
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.timelapse.EncodeState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Share progress (#36): offered on the play canvas's top bar and the gallery tile's ⋮ menu from the first gem, and a
 * tap reaches the share progress sheet without touching the save log.
 */
@RunWith(AndroidJUnit4::class)
class ProgressShareTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val ref = CanvasRef(CanvasKind.PHOTO, "progress-share")
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** 8×6 in three colors. */
    private val small = Pattern(
        width = 8,
        height = 6,
        palette = List(3) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x303030), Lab(it * 20.0, 0.0, 0.0)) },
        cells = IntArray(48) { it % 3 },
    )

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
    }

    @After
    fun tearDown() {
        runBlocking { app.encodes.cancelAndClear(ref) }
        if (::scenario.isInitialized) scenario.close()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theCanvasOffersItFromTheFirstGem() {
        val session = open()
        assertFalse(exists(SHARE_PROGRESS_TAG), "a blank canvas: not yet")
        onMain { session.placeNext() }
        compose.waitUntil(2_000) { exists(SHARE_PROGRESS_TAG) }
    }

    @Test
    fun aProgressShareFromTheCanvasReachesTheSheetAndLeavesTheLogAlone() {
        val session = open()
        onMain { repeat(10) { session.placeNext() } }
        val before = log(session)
        compose.onNodeWithTag(SHARE_PROGRESS_TAG).performClick()
        compose.waitUntil(10_000) { exists("share-percent") }
        compose.onNode(hasText("Making your video")).assertExists()
        compose.onNodeWithTag("share-cancel").performClick()
        compose.waitUntil(5_000) { !exists("share-percent") }
        assertEquals(EncodeState.Idle, app.encodes.state(ref).value, "Cancel: back on the canvas, no message")
        assertEquals(before, log(session), "sharing places nothing and takes nothing back")
    }

    @Test
    fun theGalleryTileOffersItFromTheFirstGemAndReachesTheSheet() {
        open()
        backToGallery()
        assertFalse("Share progress" in tileActions(), "a blank canvas: not in the tile's actions")

        open().also { s -> onMain { s.placeNext() } }
        backToGallery()
        // The tile shows what the gallery last read; its refresh after back is asynchronous (lessons, #50).
        compose.waitUntil(5_000) { tileActions().firstOrNull() == "Share progress" }
        compose.onNodeWithTag(TILE).performTouchInput { longClick() }
        compose.waitUntil(5_000) { exists("tile-share-progress") }
        compose.onNodeWithTag("tile-share-progress").performClick()
        compose.waitUntil(10_000) { exists("share-percent") }
        compose.onNodeWithTag("share-cancel").performClick()
        compose.waitUntil(5_000) { !exists("share-percent") }
    }

    private fun open(): PlaySession {
        if (!::scenario.isInitialized) {
            runBlocking { app.store.create(ref, small) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.onNode(hasText("My Photos")).performClick()
        }
        compose.waitUntil(5_000) { exists(TILE) }
        compose.onNodeWithTag(TILE).performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
        compose.waitForIdle()
        return session()!!
    }

    /** System back to the gallery, then wait for its refresh to show the tile's new progress. */
    private fun backToGallery() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { exists(TILE) }
        compose.waitForIdle()
    }

    private fun tileActions(): List<String> {
        compose.waitForIdle()
        val node = compose.onNodeWithTag(TILE).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    private fun log(session: PlaySession): List<Int> {
        var log = IntArray(0)
        instrumentation.runOnMainSync { log = session.log() }
        return log.toList()
    }

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TILE = "tile-photo-progress-share"
    }
}
