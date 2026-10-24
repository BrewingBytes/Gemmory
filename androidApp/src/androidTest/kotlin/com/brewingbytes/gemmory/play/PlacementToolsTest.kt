package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.copy.strings
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
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
import com.brewingbytes.gemmory.game.PlacementTool
import com.brewingbytes.gemmory.game.PlacementTools
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The placement tools (#20) on a Big canvas (106×142, 24 colors): each tool places what the pure rules pick, in their
 * order; the saved log is that order and survives a process death; the tray and TalkBack offer them to VIP only.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlacementToolsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val billing = BillingBackends.fake
    private val root = File(context.filesDir, "tools-test")
    private val ref = CanvasRef(CanvasKind.PHOTO, "tools")
    private val scopes = mutableListOf<CoroutineScope>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    /**
     * 10×10 blocks, block (bx, by) is color (bx + by) % 24, so every block is its own region. Color 1 (slot 0) is
     * block (0, 0) and the 6×2 corner block (10, 14): 112 cells. Color 2 (slot 1) is blocks (1, 0) and (0, 1).
     */
    private val big = Pattern(
        width = W,
        height = H,
        palette = List(24) { PaletteColor(it + 1, (0xFF shl 24) or (0x203040 + it * 0x050607), Lab(it * 4.0, 0.0, 0.0)) },
        cells = IntArray(W * H) { (it % W / 10 + it / W / 10) % 24 },
    )

    @Before
    fun setUp() {
        assertTrue(BuildConfig.FAKE_BILLING, "connected tests run on the fake billing backend")
        root.deleteRecursively()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        setVip(false)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        scopes.forEach { it.cancel() }
        setVip(false)
        root.deleteRecursively()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setVip(vip: Boolean) {
        billing.reset()
        if (vip) billing.own(Products.VIP_YEARLY)
        runBlocking { app.billing.refresh() }
        compose.waitUntil(5_000) { app.monetization.entitlements.value.vip == vip }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    // Store level, as KillAndResumeTest: a process is a store with its own scope; a kill is that scope dying.

    private fun newStore(): Pair<CanvasStore, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        val noEncodes = object : EncodeCanceller {
            override suspend fun cancelAndClear(ref: CanvasRef) = Unit
        }
        return CanvasStore(root, scope, Dispatchers.IO.limitedParallelism(1), encodes = noEncodes, strings = context.strings) to scope
    }

    private fun open(store: CanvasStore): PlaySession = runBlocking {
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(ref))
        lateinit var session: PlaySession
        onMain { session = store.start(opened) }
        session
    }

    private fun PlaySession.savedLog(): IntArray {
        var log = IntArray(0)
        onMain { log = placedLogForTest() }
        return log
    }

    /** Fill region, the brush and Fill color, each checked against the pure rules; returns the expected log. */
    private fun useEveryTool(s: PlaySession): IntArray {
        val expected = ArrayList<Int>()
        onMain {
            s.offerTools(true)
            s.select(0)
            // Fill region from (5, 5): the whole of block (0, 0), ring by ring from the tap.
            val region = assertNotNull(s.fillRegion(5 * W + 5))
            assertContentEquals(PlacementTools.region(big, 5 * W + 5, 0).cells, region.placed)
            assertEquals(100, region.placed.size)
            expected += region.placed.toList()
            // The brush at (15, 5), color 2: its 3×3, nearest first.
            s.select(1)
            val stamp = assertNotNull(s.brush(5 * W + 15))
            assertContentEquals(PlacementTools.brush(big, 5 * W + 15).cells, stamp.placed)
            expected += stamp.placed.toList()
            // Color 1 has 12 left (the corner block): Fill color places them and finishes it.
            s.select(0)
            assertTrue(s.fillColorVisible)
            val rest = assertNotNull(s.fillColor(from = 0))
            assertEquals(12, rest.placed.size)
            assertEquals(0, assertNotNull(rest.colorFinished).slot)
            assertFalse(s.isFinished)
            expected += rest.placed.toList()
        }
        return expected.toIntArray()
    }

    @Test
    fun everyToolPlacesItsCellsAndTheLogSurvivesAKillAfterOnStop() {
        val (store, scope) = newStore()
        assertEquals(WriteResult.Written, runBlocking { store.create(ref, big) })
        val s = open(store)
        val expected = useEveryTool(s)
        assertContentEquals(expected, s.savedLog())
        lateinit var stopped: kotlinx.coroutines.Deferred<Unit>
        onMain { stopped = s.onStop() }
        runBlocking { stopped.await() }
        scope.cancel()

        val resumed = open(newStore().first)
        assertContentEquals(expected, resumed.savedLog())
        assertEquals(121, resumed.placedCount)
    }

    @Test
    fun aBatchIsSavedOnTheOneSecondCadenceWithoutOnStop() {
        val (store, scope) = newStore()
        assertEquals(WriteResult.Written, runBlocking { store.create(ref, big) })
        val s = open(store)
        val expected = useEveryTool(s)
        // No onStop: the debounce writes 1 s after the last batch.
        SystemClock.sleep(1_500)
        scope.cancel()
        val resumed = open(newStore().first)
        assertContentEquals(expected, resumed.savedLog())
    }

    // The app: VIP sees the tool well, the Fill button and two TalkBack actions; a free player sees none of them.

    private fun launchCanvas(): PlaySession {
        runBlocking { app.store.create(ref, big) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-photo-tools")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-photo-tools").performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
        compose.waitForIdle()
        return session()!!
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun PlaySession.centerOf(cell: Int): Offset {
        val vp = viewport
        return Offset(vp.offsetX + (cell % W + 0.5f) * vp.cellPx, vp.offsetY + (cell / W + 0.5f) * vp.cellPx)
    }

    private fun actions(): List<String> =
        compose.onNodeWithTag("play-canvas").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    @Test
    fun vipPicksAToolInTheTrayAndATapFillsTheRegion() {
        setVip(true)
        val s = launchCanvas()
        compose.waitUntil(5_000) { exists("tool-well") }
        compose.onNodeWithTag("tool-well").performClick()
        compose.onNodeWithTag("tool-region").performClick()
        assertEquals(PlacementTool.REGION, s.tool)
        assertFalse(exists("tool-region"), "picking a tool swaps the troughs back")
        // 24dp cells around (10, 5), color 1 selected: a tap on (5, 5) fills block (0, 0).
        onMain {
            s.select(0)
            s.viewport.set(24f, 10.5f, 5.5f)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("play-canvas").performTouchInput { click(s.centerOf(5 * W + 5)) }
        compose.waitForIdle()
        assertEquals(100, s.placedCount)
        assertContentEquals(PlacementTools.region(big, 5 * W + 5, 0).cells, s.savedLog())

        // The brush: a tap on (15, 5) with color 2 places its 3×3.
        compose.onNodeWithTag("tool-well").performClick()
        compose.onNodeWithTag("tool-brush").performClick()
        onMain { s.select(1) }
        compose.onNodeWithTag("play-canvas").performTouchInput { click(s.centerOf(5 * W + 15)) }
        compose.waitForIdle()
        assertEquals(109, s.placedCount)

        // TalkBack: "Fill region at next gem" fills the rest of block (1, 0), the region holding cell (10, 0).
        assertTrue("Fill region at next gem" in actions())
        compose.onNodeWithTag("play-canvas").performCustomAccessibilityActionWithLabel("Fill region at next gem")
        compose.waitForIdle()
        assertEquals(200, s.placedCount)

        // Color 1's last 12: the Fill button and its action show; the button finishes the color.
        onMain { s.select(0) }
        compose.waitForIdle()
        assertTrue("Fill the rest of color 1" in actions())
        compose.onNodeWithTag("fill-color").performClick()
        compose.waitForIdle()
        assertEquals(212, s.placedCount)
        assertEquals(0, s.remaining(0))
        assertFalse(exists("fill-color"))

        // VIP lapses: plain taps again, no well, no tool actions.
        setVip(false)
        compose.waitForIdle()
        assertFalse(exists("tool-well"))
        assertEquals(PlacementTool.TAP, s.tool)
        assertFalse("Fill region at next gem" in actions())
    }

    @Test
    fun aFreePlayerSeesNoTools() {
        val s = launchCanvas()
        onMain { s.select(0) }
        compose.waitForIdle()
        assertFalse(exists("tool-well"))
        assertFalse(exists("fill-color"))
        assertEquals(
            listOf("Place next gem", "Find next gem", "Zoom in", "Zoom out", "Whole picture", "Peek at picture"),
            actions(),
        )
    }

    private companion object {
        const val W = 106
        const val H = 142
    }
}
