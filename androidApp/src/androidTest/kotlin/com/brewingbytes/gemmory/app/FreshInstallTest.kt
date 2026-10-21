package com.brewingbytes.gemmory.app

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.gallery.openFinishedTile
import com.brewingbytes.gemmory.play.CanvasViewport
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import java.io.File
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fresh install (design D13): the starter is the first Pictures tile, it can be played to the end with real taps on
 * the canvas, the lights-down completion follows, and reopening the finished tile shows the completion still.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun freshInstall() {
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        // The UMP form belongs to a real first launch (ConsentManager); in the EEA it would cover the canvas here.
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        app.resetStreakForTest()
        scenario.close()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    @Test
    fun starterTileFirstThenPlayToCompletionThenStillOnReopen() {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("The Starry Night, 0% done")).assertExists()
        compose.onNodeWithTag("tile-bundled-starter").performClick()

        compose.waitUntil(5_000) { session() != null && session()!!.viewport.isLaidOut }
        val session = session()!!
        assertTrue(
            session.viewport.zoomDp >= CanvasViewport.PLACE_MIN_DP,
            "the starter must be playable at fit zoom on this screen, was ${session.viewport.zoomDp}dp",
        )
        val width = session.pattern.width
        while (!session.isFinished) {
            val slot = session.selectedSlot
            val cells = (0 until session.cellCount).filter { !session.isPlaced(it) && session.pattern.cells[it] == slot }
            for (cell in cells) {
                val vp = session.viewport
                val x = vp.offsetX + (cell % width + 0.5f) * vp.cellPx
                val y = vp.offsetY + (cell / width + 0.5f) * vp.cellPx
                compose.onNodeWithTag("play-canvas").performTouchInput { click(Offset(x, y)) }
            }
            compose.waitForIdle()
        }

        // Lights down: dim, sweep, chime, 2 s hold, then the text fades in.
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("completion-fresh")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Finished").assertExists()
        // Animations run on the test clock: 250 ms dim + 2.4 s sweep + 2 s hold + 400 ms fade.
        // The picture loads in real time; the lights-down sequence starts only then.
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithTag("completion-done").performClick()

        // #89: the finished starter waits in the Finished section at the end of Pictures.
        compose.openFinishedTile(CanvasRef(CanvasKind.BUNDLED, "starter"))
        compose.onNode(hasContentDescription("The Starry Night, done")).assertDoesNotExist()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("completion-still")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Play again").assertExists()
        compose.onNodeWithText("The Starry Night · 768 gems", substring = true).assertExists()
    }
}
