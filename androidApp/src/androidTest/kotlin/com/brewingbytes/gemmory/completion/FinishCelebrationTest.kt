package com.brewingbytes.gemmory.completion

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.gallery.openFinishedTile
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The finish celebration (#57): a fresh finish sparkles across the picture during the sweep, then drops gem confetti
 * through the hold, and both are gone by the time Share fades in. Reduced motion (D21) plays neither, and the
 * completion still (a finished picture reopened from the gallery) never celebrates.
 */
@RunWith(AndroidJUnit4::class)
class FinishCelebrationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val ref = CanvasRef(CanvasKind.PHOTO, "celebration")
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var animatorScale: String? = null
    private lateinit var finished: PlaySession

    /** 8×6 in three colors, so a whole canvas is finished in 48 placements. */
    private val small = Pattern(
        width = 8,
        height = 6,
        palette = List(3) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x303030), Lab(it * 20.0, 0.0, 0.0)) },
        cells = IntArray(48) { it % 3 },
    )

    @Before
    fun setUp() {
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        // T56's row only on a first-run install's first finish: these cases run as an install that isn't one.
        app.resetFirstRunForTest(fresh = false)
        app.whereFoundConsentForTest = null
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
    }

    @After
    fun tearDown() {
        app.resetStreakForTest()
        app.resetFirstRunForTest(fresh = false)
        app.whereFoundConsentForTest = null
        compose.mainClock.autoAdvance = true
        if (::scenario.isInitialized) scenario.close()
        animatorScale?.let { shell("settings put global animator_duration_scale $it") }
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aFreshFinishSparklesThenDropsConfettiAndClearsBeforeShare() {
        val seen = finishAndWatch()
        val sparkle = seen.filter { SPARKLE in it.value }.keys
        val confetti = seen.filter { CONFETTI in it.value }.keys
        assertTrue(sparkle.isNotEmpty(), "the sparkle plays during the sweep")
        assertTrue(confetti.isNotEmpty(), "the confetti falls after it")
        assertTrue(sparkle.max() < confetti.min(), "sparkle first, confetti from the chime: $sparkle then $confetti")
        assertTrue(sparkle.min() >= 200 && sparkle.max() <= 2_800, "sparkle within the sweep (250-2,650ms): $sparkle")
        assertTrue(confetti.max() <= 4_700, "confetti gone by the end of the hold (4,650ms): $confetti")
        assertTrue(seen.filterKeys { it >= 4_800 }.values.all { it.isEmpty() }, "nothing over Share and Done")
        var log = IntArray(0)
        instrumentation.runOnMainSync { log = finished.placedLogForTest() }
        assertEquals((0 until 3).flatMap { slot -> (0 until 48).filter { it % 3 == slot } }, log.toList(), "the save log is the 48 placements, nothing more")
    }

    @Test
    fun underReducedMotionThereIsNoSparkleOrConfetti() {
        animatorScale = shell("settings get global animator_duration_scale").ifBlank { "1" }.let { if (it == "null") "1" else it }
        shell("settings put global animator_duration_scale 0")
        val seen = finishAndWatch()
        assertTrue(seen.values.all { it.isEmpty() }, "no particles or glints under reduced motion: $seen")
        assertTrue(exists("completion-done"), "the text and buttons still come")
    }

    @Test
    fun theCompletionStillPlaysNoCelebration() {
        val session = open()
        onMain { while (!session.isFinished) session.placeNext() }
        compose.waitUntil(5_000) { exists("completion-fresh") }
        scenario.close()
        // A new activity: the finish is no longer fresh, so the gallery tile opens the still.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        // #89: the finished canvas sits in My Photos' Finished section.
        compose.openFinishedTile(ref)
        compose.waitUntil(5_000) { exists("completion-still") }
        compose.waitUntil(10_000) { picture() }
        val seen = watch()
        assertTrue(seen.values.all { it.isEmpty() }, "the still stays still: $seen")
    }

    /**
     * T56 (#157): a first-run install's first finish, consent granted. The row is in the column from the start, so the
     * picture never moves through the celebration or after it; an answer pays one photo canvas, once, and the thanks
     * line takes the row's place; the install is never asked again.
     */
    @Test
    fun aFirstRunFinishAsksWhereFoundWithoutMovingThePicture() {
        app.resetFirstRunForTest(fresh = true)
        app.whereFoundConsentForTest = true
        val canvasesBefore = app.monetization.inventory.value.canvases
        val session = open().also { finished = it }
        onMain { repeat(47) { session.placeNext() } }
        compose.mainClock.autoAdvance = false
        instrumentation.runOnMainSync { session.placeNext() }
        val deadline = System.currentTimeMillis() + 10_000
        while (!(exists("completion-fresh") && picture())) {
            assertTrue(System.currentTimeMillis() < deadline, "the fresh completion and its picture show")
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(16)
        }
        val bounds = mutableSetOf<androidx.compose.ui.geometry.Rect>()
        for (ms in 0..WATCH_MS step STEP_MS) {
            bounds += compose.onNode(hasContentDescription("Finished picture")).fetchSemanticsNode().boundsInRoot
            Thread.sleep(STEP_MS.toLong())
            compose.mainClock.advanceTimeBy(STEP_MS.toLong())
        }
        compose.mainClock.autoAdvance = true
        assertEquals(1, bounds.size, "the picture never resizes: $bounds")
        assertTrue(exists(WHERE_FOUND_ROW_TAG), "the row under Done")
        compose.onNodeWithTag(WHERE_FOUND_ROW_TAG).performClick()
        compose.waitUntil(5_000) { exists("$WHERE_FOUND_ANSWER_TAG-friend") }
        compose.onNodeWithTag("$WHERE_FOUND_ANSWER_TAG-friend").performClick()
        compose.waitUntil(5_000) { exists(WHERE_FOUND_THANKS_TAG) }
        compose.onNode(hasText("Thanks. 1 photo canvas added to your inventory.")).assertExists()
        assertEquals(canvasesBefore + 1, app.monetization.inventory.value.canvases, "one photo canvas")
        assertEquals(FirstRunAsk.No, app.firstRun.firstRunAsk(analyticsGranted = true, seed = 1), "never asked again")
    }

    /**
     * Places every gem but the last, holds the test clock, places the last, waits (in real time) for the finished
     * picture, then watches the celebration's layers frame by frame from the moment the sequence starts.
     */
    private fun finishAndWatch(): Map<Int, Set<String>> {
        val session = open().also { finished = it }
        onMain { repeat(47) { session.placeNext() } }
        compose.mainClock.autoAdvance = false
        instrumentation.runOnMainSync { session.placeNext() }
        val deadline = System.currentTimeMillis() + 10_000
        while (!(exists("completion-fresh") && picture())) {
            assertTrue(System.currentTimeMillis() < deadline, "the fresh completion and its picture show")
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(16)
        }
        return watch()
    }

    /** Which celebration layers exist at each 50ms step over 6s, moving both the test clock and real time. */
    private fun watch(): Map<Int, Set<String>> {
        compose.mainClock.autoAdvance = false
        val seen = linkedMapOf<Int, Set<String>>()
        for (ms in 0..WATCH_MS step STEP_MS) {
            seen[ms] = setOf(SPARKLE, CONFETTI).filter(::exists).toSet()
            Thread.sleep(STEP_MS.toLong())
            compose.mainClock.advanceTimeBy(STEP_MS.toLong())
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return seen
    }

    private fun open(): PlaySession {
        runBlocking { (context.applicationContext as GemmoryApp).store.create(ref, small) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        compose.waitUntil(5_000) { exists(TILE) }
        compose.onNodeWithTag(TILE).performClick()
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

    private fun picture() = compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty()

    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    private companion object {
        const val TILE = "tile-photo-celebration"
        const val SPARKLE = "finish-sparkle"
        const val CONFETTI = "finish-confetti"
        const val WATCH_MS = 6_000
        const val STEP_MS = 50
    }
}
