package com.brewingbytes.gemmory.play

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
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
import com.brewingbytes.gemmory.game.CanvasZoom
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
import java.text.NumberFormat
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TalkBack on the play canvas (design D19): the one canvas node and its six custom actions, the polite live-region
 * announcements, the trough semantics, and the reduced-motion blink (D21). Also the progress milestones and the peek
 * (#35), which TalkBack reaches through the same live region and a canvas action. Actions are invoked the way TalkBack
 * invokes them, through the semantics tree.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CanvasAccessibilityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ref = CanvasRef(CanvasKind.PHOTO, "a11y")
    private val numbers = NumberFormat.getIntegerInstance()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var animatorScale: String? = null

    /** 8×6, slots 0 1 2 0 1 2 … in reading order: 16 cells per color, so a whole canvas can be finished by actions. */
    private val small = pattern(8, 6) { it % 3 }

    /**
     * 60×80, columns in pairs of colors 1 and 2 (slot (c / 2) % 2), and color 3 (slot 2) on only 12 cells, for Find.
     * Fit is well under 8dp on a phone.
     */
    private val findCells = IntArray(12) { 60 * (20 + it * 4) + 25 + it }
    private val large = pattern(60, 80) { if (it in findCells) 2 else (it % 60 / 2) % 2 }

    private fun pattern(width: Int, height: Int, slot: (Int) -> Int) = Pattern(
        width = width,
        height = height,
        palette = List(3) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x303030), Lab(it * 20.0, 0.0, 0.0)) },
        cells = IntArray(width * height, slot),
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
        if (::scenario.isInitialized) scenario.close()
        animatorScale?.let { shell("settings put global animator_duration_scale $it") }
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        (context.applicationContext as GemmoryApp).settings.setMilestones(true)
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    private fun open(pattern: Pattern): PlaySession {
        runBlocking { (context.applicationContext as GemmoryApp).store.create(ref, pattern) }
        return reopen()
    }

    /** Opens the canvas already on disk, as a player coming back to it from the gallery. */
    private fun reopen(): PlaySession {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(androidx.compose.ui.test.hasText("My Photos")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tile-photo-a11y")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-photo-a11y").performClick()
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

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
        compose.waitForIdle()
    }

    private fun action(label: String) {
        compose.onNodeWithTag("play-canvas").performCustomAccessibilityActionWithLabel(label)
        compose.waitForIdle()
    }

    /**
     * Runs [block] with the test clock held, then reads the live region before its 3s clear (an idle wait would run
     * the clock past it).
     */
    private fun heard(block: () -> Unit): String? {
        compose.mainClock.autoAdvance = false
        try {
            block()
            compose.mainClock.advanceTimeByFrame()
            return announced()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** What the live region says now (the trailing space that makes a repeat a change isn't spoken). */
    private fun announced(): String? {
        val node = compose.onNodeWithTag("announcer").fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
        return node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Runs [block] with the test clock held; [pass] then moves both it and real time (the peek's tap test is real time). */
    private fun <T> heldClock(block: () -> T): T {
        compose.mainClock.autoAdvance = false
        try {
            return block()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun pass(ms: Long) {
        Thread.sleep(ms)
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun PlaySession.centerOf(cell: Int): Offset {
        val vp = viewport
        return Offset(vp.offsetX + (cell % pattern.width + 0.5f) * vp.cellPx, vp.offsetY + (cell / pattern.width + 0.5f) * vp.cellPx)
    }

    @Test
    fun theCanvasIsOneNodeWithProgressSelectionAndSixActions() {
        val s = open(large)
        val canvas = compose.onNodeWithTag("play-canvas")
        val left = s.remaining(s.selectedSlot)
        canvas.assertContentDescriptionEquals(
            "Canvas. 0 of ${numbers.format(4800)} placed. Color ${s.selectedSlot + 1} selected, ${numbers.format(left)} left.",
        )
        val labels = canvas.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Place next gem", "Find next gem", "Zoom in", "Zoom out", "Whole picture", "Peek at picture"), labels)

        action("Place next gem")
        canvas.assertContentDescriptionEquals(
            "Canvas. 1 of ${numbers.format(4800)} placed. Color ${s.selectedSlot + 1} selected, ${numbers.format(left - 1)} left.",
        )
    }

    @Test
    fun placeNextGemFinishesAWholeCanvasInReadingOrderThroughTheSameRuleAsATap() {
        val s = open(small)
        assertEquals(0, s.selectedSlot)
        assertEquals("15 left", heard { action("Place next gem") }?.trim())
        repeat(14) { action("Place next gem") }
        assertEquals("Color 1 done. Color 2 selected.", heard { action("Place next gem") }?.trim())
        assertEquals(1, s.selectedSlot)
        // Only the canvas actions from here to the end, as a TalkBack player would.
        repeat(32) { action("Place next gem") }
        val expected = (0 until 3).flatMap { slot -> (0 until 48).filter { it % 3 == slot } }.toIntArray()
        assertContentEquals(expected, s.log(), "each color in reading order, every cell once")
        assertTrue(s.isFinished)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("completion-fresh")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun findNextGemMatchesTheFindButtonAndPlaceNextGemPlacesWhatWasFound() {
        val s = open(large)
        onMain { s.select(2) }
        action("Find next gem")
        assertEquals(CanvasZoom.TAP_ZOOM_DP, s.viewport.zoomDp, 0.01f)
        assertEquals(findCells[0] % 60 + 0.5f, s.viewport.centerX, 0.01f)
        assertEquals(findCells[0] / 60 + 0.5f, s.viewport.centerY, 0.01f)
        // The Find button (shown at 12 left) continues from the same cursor, to the next cell.
        compose.onNodeWithTag("find").performClick()
        compose.waitForIdle()
        assertEquals(findCells[1] / 60 + 0.5f, s.viewport.centerY, 0.01f)
        action("Place next gem")
        action("Place next gem")
        assertContentEquals(intArrayOf(findCells[1], findCells[2]), s.log(), "the found gem first, then on in reading order")
    }

    @Test
    fun aWrongTapIsAnnouncedAndChangesNothingEvenTwiceInARow() {
        val s = open(large)
        onMain { s.viewport.set(24f, 30.5f, 40.5f) }
        assertEquals(0, s.selectedSlot)
        val other = 40 * 60 + 30 // column 30: slot (30 / 2) % 2 = 1, color 2
        val tap: () -> Unit = { compose.onNodeWithTag("play-canvas").performTouchInput { click(s.centerOf(other)) } }
        val first = heard(tap)
        assertEquals("Not color 1", first?.trim())
        // Again soon (past the double-tap window, or it would be a double-tap), and again after the live region has
        // cleared: both must be changes TalkBack speaks.
        compose.mainClock.advanceTimeBy(500)
        val second = heard(tap)
        assertEquals("Not color 1", second?.trim())
        assertTrue(first != second, "a repeat must be a change, or TalkBack stays silent")
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertEquals(null, announced(), "cleared after it spoke")
        val third = heard(tap)
        assertTrue(third != second && third?.trim() == "Not color 1", "was <$third> after <$second>")
        assertContentEquals(IntArray(0), s.log())
        assertEquals(0, s.placedCount)
        assertEquals(s.cellCount - s.remaining(1) - 12, s.remaining(0))
    }

    @Test
    fun zoomActionsWalkTheZoomTableAndWholePictureFits() {
        val s = open(large)
        val fit = s.viewport.minZoomDp
        assertTrue(fit < 8f, "fit is ${fit}dp")
        action("Zoom in")
        assertEquals(8f, s.viewport.zoomDp, 0.01f)
        action("Zoom in")
        assertEquals(16f, s.viewport.zoomDp, 0.01f)
        action("Zoom in")
        action("Zoom in")
        assertEquals(48f, s.viewport.zoomDp, 0.01f)
        action("Zoom out")
        assertEquals(24f, s.viewport.zoomDp, 0.01f)
        action("Whole picture")
        assertEquals(fit, s.viewport.zoomDp, 0.01f)
    }

    @Test
    fun troughsReadTheirCountAndSelectionAndFinishedOnesReadDone() {
        val s = open(small)
        compose.onNodeWithTag("trough-1").assertContentDescriptionEquals("Color 1, 16 left").assertIsSelected()
        compose.onNodeWithTag("trough-2").assertContentDescriptionEquals("Color 2, 16 left").assertHasClickAction()
        repeat(16) { action("Place next gem") }
        compose.onNodeWithTag("trough-1").assertContentDescriptionEquals("Color 1, done")
        compose.onNodeWithTag("trough-2").assertIsSelected()
        assertEquals(1, s.selectedSlot)
    }

    @Test
    fun underReducedMotionAWrongTapBlinksTheLampOutlineFor120ms() {
        animatorScale = shell("settings get global animator_duration_scale").ifBlank { "1" }.let { if (it == "null") "1" else it }
        shell("settings put global animator_duration_scale 0")
        val s = open(large)
        onMain { s.viewport.set(24f, 30.5f, 40.5f) }
        val outlined = { compose.onNodeWithTag("trough-1").fetchSemanticsNode().config[LampOutline] }
        assertTrue(outlined())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("play-canvas").performTouchInput { click(s.centerOf(40 * 60 + 30)) }
        compose.mainClock.advanceTimeBy(48)
        assertEquals(false, outlined(), "the outline is off during the blink")
        compose.mainClock.advanceTimeBy(150)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(true, outlined(), "and back after 120ms")
        compose.onNodeWithTag("trough-1").assert(SemanticsMatcher.expectValue(LampOutline, true))
        assertEquals(0, s.placedCount)
    }

    @Test
    fun aMilestoneIsAnnouncedWithItsBannerOnceAndNotReplayedOnReopen() {
        // 48 gems: 25% is the 12th, 50% the 24th.
        val s = open(small)
        repeat(11) { action("Place next gem") }
        assertTrue(s.milestone == null)
        heldClock {
            action("Place next gem")
            compose.mainClock.advanceTimeByFrame()
            assertEquals("4 left. A quarter done", announced()?.trim())
            assertEquals(25, s.milestone?.percent)
            pass(500)
            assertTrue(exists("milestone-banner"))
            pass(3_000)
            assertTrue(!exists("milestone-banner"), "gone after 3s")
        }
        assertEquals("3 left", heard { action("Place next gem") }?.trim(), "the next gem crosses nothing")

        // Back to the gallery and in again at 13 of 48: 25% doesn't play again.
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        scenario.close()
        val again = reopen()
        assertEquals(13, again.placedCount)
        assertTrue(again.milestone == null)
        assertTrue(!exists("milestone-banner"))
        assertEquals("2 left", heard { action("Place next gem") }?.trim())
        repeat(9) { action("Place next gem") }
        assertEquals(23, again.placedCount)
        // The 24th finishes nothing but crosses 50%; color 2 is selected after color 1 finished at the 16th.
        assertEquals("${again.remaining(again.selectedSlot) - 1} left. Halfway there", heard { action("Place next gem") }?.trim())
        assertEquals(50, again.milestone?.percent)
    }

    @Test
    fun withMilestonesOffACrossingPlaysAndSaysNothing() {
        (context.applicationContext as GemmoryApp).settings.setMilestones(false)
        val s = open(small)
        repeat(11) { action("Place next gem") }
        assertEquals("4 left", heard { action("Place next gem") }?.trim())
        assertTrue(s.milestone == null)
        compose.waitForIdle()
        assertTrue(!exists("milestone-banner"))
    }

    @Test
    fun thePeekActionShowsTheFinishedPictureUntilBackToCanvas() {
        val s = open(large)
        assertTrue(!exists("peek-picture"))
        assertEquals("Showing the finished picture", heard { action("Peek at picture") }?.trim())
        compose.waitForIdle()
        assertTrue(exists("peek-picture"))
        val labels = compose.onNodeWithTag("play-canvas").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals("Back to canvas", labels.last())
        // It stays until the action is used again, and changes nothing on the canvas.
        heldClock { pass(2_000) }
        assertTrue(exists("peek-picture"))
        assertEquals("Back to canvas", heard { action("Back to canvas") }?.trim())
        compose.waitForIdle()
        assertTrue(!exists("peek-picture"))
        assertEquals(0, s.log().size)
    }

    @Test
    fun theEyeShowsThePictureWhileHeldAndForATap() {
        val s = open(large)
        compose.onNodeWithTag("peek").assertContentDescriptionEquals("Peek").assertHasClickAction()
        heldClock {
            // Hold: shown while the finger is down, gone once it lifts and the 150ms fade ends.
            compose.onNodeWithTag("peek").performTouchInput { down(center) }
            pass(400)
            assertTrue(exists("peek-picture"))
            pass(1_600)
            assertTrue(exists("peek-picture"), "still held past 1.5s")
            compose.onNodeWithTag("peek").performTouchInput { up() }
            pass(400)
            assertTrue(!exists("peek-picture"))
            // Tap: shown for 1.5s.
            compose.onNodeWithTag("peek").performTouchInput { click(center) }
            pass(700)
            assertTrue(exists("peek-picture"), "still within 1.5s")
            pass(1_200)
            assertTrue(!exists("peek-picture"))
            // A touch on the picture hides it and places nothing.
            compose.onNodeWithTag("peek").performTouchInput { click(center) }
            pass(300)
            compose.onNodeWithTag("peek-picture").performTouchInput { click(center) }
            pass(400)
            assertTrue(!exists("peek-picture"))
        }
        assertEquals(0, s.log().size)
    }
}
