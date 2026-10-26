package com.brewingbytes.gemmory.completion

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
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
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.save.PlayTimes
import com.brewingbytes.gemmory.reminder.REMINDER_ASK_NOT_NOW_TAG
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        app.whereFoundSeedForTest = null
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
        app.whereFoundSeedForTest = null
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

    /** T56 (#157, eng R2): with the row due, a first-run install's first finish plays the same sequence. */
    @Test
    fun aFirstRunFinishKeepsTheSequenceWithWhereFoundDue() {
        firstRunAsked()
        val seen = finishAndWatch()
        val sparkle = seen.filter { SPARKLE in it.value }.keys
        val confetti = seen.filter { CONFETTI in it.value }.keys
        assertTrue(sparkle.isNotEmpty() && confetti.isNotEmpty(), "sparkle and confetti both play: $seen")
        assertTrue(sparkle.max() < confetti.min(), "sparkle first, confetti from the chime: $sparkle then $confetti")
        assertTrue(sparkle.min() >= 200 && sparkle.max() <= 2_800, "sparkle within the sweep (250-2,650ms): $sparkle")
        assertTrue(confetti.max() <= 4_700, "confetti gone by the end of the hold (4,650ms): $confetti")
        assertTrue(seen.filterKeys { it >= 4_800 }.values.all { it.isEmpty() }, "nothing over Share and Done")
        assertTrue(exists(WHERE_FOUND_ROW_TAG), "the row once the text is in")
    }

    /**
     * T56 (#157, eng R2/R3): the row is laid out from the start, so the picture never moves through the celebration,
     * after it, or when the shorter thanks line takes the row's place; but it isn't in the tree (no tag, no TalkBack
     * node) until the text has faded in. An answer pays one photo canvas, once; the install is never asked again.
     */
    @Test
    fun theRowJoinsOnlyOnceTheTextIsInAndThePictureNeverMoves() {
        firstRunAsked()
        val canvasesBefore = app.monetization.inventory.value.canvases
        start()
        val bounds = mutableSetOf<androidx.compose.ui.geometry.Rect>()
        val row = sortedSetOf<Int>()
        val confetti = sortedSetOf<Int>()
        for (ms in 0..WATCH_MS step STEP_MS) {
            bounds += compose.onNode(hasContentDescription("Finished picture")).fetchSemanticsNode().boundsInRoot
            if (exists(WHERE_FOUND_ROW_TAG)) row += ms
            if (exists(CONFETTI)) confetti += ms
            Thread.sleep(STEP_MS.toLong())
            compose.mainClock.advanceTimeBy(STEP_MS.toLong())
        }
        compose.mainClock.autoAdvance = true
        assertEquals(1, bounds.size, "the picture never resizes: $bounds")
        // The text fades in over 400ms (TEXT_MS) from the end of the hold, when the confetti has gone: the row joins at 1f.
        assertTrue(confetti.isNotEmpty() && row.isNotEmpty(), "confetti $confetti, row from ${row.firstOrNull()}")
        assertTrue(row.first() >= confetti.last() + 350, "the row joins only at text 1f: from ${row.first()}, confetti until ${confetti.last()}")
        assertEquals((row.first()..WATCH_MS step STEP_MS).toList(), row.toList(), "and stays")
        compose.onNodeWithTag(WHERE_FOUND_ROW_TAG).performClick()
        compose.waitUntil(5_000) { exists("$WHERE_FOUND_ANSWER_TAG-friend") }
        compose.onNodeWithTag("$WHERE_FOUND_ANSWER_TAG-friend").performClick()
        assertEquals(WhereFoundAnswer.FRIEND, completion().whereFound.selected, "the lamp outline holds")
        compose.waitUntil(5_000) { exists(WHERE_FOUND_THANKS_TAG) }
        compose.onNode(hasText("Thanks. 1 photo canvas added to your inventory.")).assertExists()
        val after = compose.onNode(hasContentDescription("Finished picture")).fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.single(), after, "the shorter thanks line doesn't move the picture")
        assertEquals(canvasesBefore + 1, app.monetization.inventory.value.canvases, "one photo canvas")
        assertEquals(FirstRunAsk.No, app.firstRun.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true), "never asked again")
    }

    /** T56 (#157, design D5): under reduced motion the row still comes after the text, and an answer has no hold. */
    @Test
    fun underReducedMotionTheRowComesAfterTheTextWithNoOutlineHold() {
        animatorScale = shell("settings get global animator_duration_scale").ifBlank { "1" }.let { if (it == "null") "1" else it }
        shell("settings put global animator_duration_scale 0")
        firstRunAsked()
        start()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { exists(WHERE_FOUND_ROW_TAG) }
        compose.onNodeWithTag(WHERE_FOUND_ROW_TAG).performClick()
        compose.waitUntil(5_000) { exists("$WHERE_FOUND_ANSWER_TAG-video") }
        compose.onNodeWithTag("$WHERE_FOUND_ANSWER_TAG-video").performClick()
        assertNull(completion().whereFound.selected, "no outline hold under reduced motion")
        compose.waitUntil(5_000) { exists(WHERE_FOUND_THANKS_TAG) }
        compose.onNode(hasText("Thanks. 1 photo canvas added to your inventory.")).assertExists()
    }

    /**
     * T56 (#157, eng outside voice #1): a rotation mid-celebration (the lights-down moment is marked played) still
     * shows the row, and one after an answer shows the thanks line, never the row again.
     */
    @Test
    fun aRotationMidCelebrationStillShowsTheRowAndTheThanksSurvivesOne() {
        firstRunAsked()
        start()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        scenario.recreate()
        compose.waitUntil(10_000) { exists("completion-fresh") && exists(WHERE_FOUND_ROW_TAG) }
        compose.onNodeWithTag(WHERE_FOUND_ROW_TAG).performClick()
        compose.waitUntil(5_000) { exists("$WHERE_FOUND_ANSWER_TAG-community") }
        compose.onNodeWithTag("$WHERE_FOUND_ANSWER_TAG-community").performClick()
        compose.waitUntil(5_000) { exists(WHERE_FOUND_THANKS_TAG) }
        scenario.recreate()
        compose.waitUntil(10_000) { exists("completion-fresh") && exists(WHERE_FOUND_THANKS_TAG) }
        assertTrue(!exists(WHERE_FOUND_ROW_TAG), "the row never comes back")
    }

    /**
     * T56 (#160 review): the completion ViewModel lives as long as the activity, so a later fresh finish in the same
     * activity shows neither the first finish's row nor its thanks line, and isn't asked.
     */
    @Test
    fun aSecondFinishInTheSameActivityShowsNoRowAndNoThanks() {
        firstRunAsked()
        start()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { exists(WHERE_FOUND_ROW_TAG) }
        compose.onNodeWithTag(WHERE_FOUND_ROW_TAG).performClick()
        compose.waitUntil(5_000) { exists("$WHERE_FOUND_ANSWER_TAG-friend") }
        compose.onNodeWithTag("$WHERE_FOUND_ANSWER_TAG-friend").performClick()
        compose.waitUntil(5_000) { exists(WHERE_FOUND_THANKS_TAG) }

        val second = CanvasRef(CanvasKind.PHOTO, "celebration-two")
        runBlocking { app.store.create(second, small) }
        compose.waitUntil(10_000) {
            if (!exists("gallery-grid") && exists("completion-done")) runCatching { compose.onNodeWithTag("completion-done").performClick() }
            exists("gallery-grid")
        }
        if (exists(REMINDER_ASK_NOT_NOW_TAG)) compose.onNodeWithTag(REMINDER_ASK_NOT_NOW_TAG).performClick()
        compose.onNode(hasText("My Photos")).performClick()
        compose.waitUntil(5_000) { exists("tile-photo-celebration-two") }
        compose.onNodeWithTag("tile-photo-celebration-two").performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        onMain { while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { exists("completion-fresh") && exists("completion-done") }
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        assertTrue(!exists(WHERE_FOUND_ROW_TAG), "no row at a later finish")
        assertTrue(!exists(WHERE_FOUND_THANKS_TAG), "nor the first finish's thanks line")
    }

    /**
     * T56 (#160 review): the row survives the process dying in the background (a Share round-trip on a low-memory
     * phone) through the ViewModel's saved state: a ViewModel built from the same [SavedStateHandle], as Android builds
     * one after the process died, shows it again on that canvas, still answerable; after an answer, the thanks line.
     * A test can't kill its own process, so the restore is driven here, on the real app's first-run state.
     */
    @Test
    fun theRowAndTheThanksComeBackFromSavedState() {
        firstRunAsked()
        val handle = SavedStateHandle()
        fun viewModel(): CompletionViewModel {
            var vm: CompletionViewModel? = null
            instrumentation.runOnMainSync { vm = CompletionViewModel(app, handle) }
            return vm!!
        }
        val first = viewModel()
        instrumentation.runOnMainSync { first.decideWhereFound(ref) }
        assertTrue(first.whereFound.ui is WhereFoundUi.Row, "asked: ${first.whereFound.ui}")

        val restored = viewModel()
        assertEquals(first.whereFound.ui, restored.whereFound.ui, "the row comes back, in its order")
        assertTrue(restored.whereFoundOn(ref), "on that canvas's completion (a still after the process died)")
        assertTrue(!restored.whereFoundOn(CanvasRef(CanvasKind.PHOTO, "another")))
        instrumentation.runOnMainSync { restored.whereFound.answer(WhereFoundAnswer.FRIEND, reducedMotion = true) }
        val deadline = System.currentTimeMillis() + 5_000
        while (restored.whereFound.ui !is WhereFoundUi.Thanks) {
            assertTrue(System.currentTimeMillis() < deadline, "the answer lands: ${restored.whereFound.ui}")
            Thread.sleep(50)
        }
        assertEquals(WhereFoundUi.Thanks(paid = true), viewModel().whereFound.ui, "then the thanks line comes back")
        instrumentation.runOnMainSync { restored.leftCompletion(ref) }
        assertEquals(null, viewModel().whereFound.ui, "and nothing once the completion was left")
    }

    /** T56 (#160 review round 2): an install that finished another picture before (it updated into T56) isn't asked. */
    @Test
    fun anInstallThatFinishedAnotherPictureBeforeIsNotAsked() {
        firstRunAsked()
        val earlier = CanvasRef(CanvasKind.PHOTO, "finished-earlier")
        runBlocking { app.store.create(earlier, small) }
        app.store.repository.writePlay(earlier, PlayTimes(firstFinishedDay = app.dayClock.today() - 5))
        assertNotAsked()
    }

    /** T56 (#160 review round 2): nor one that replays (Play again) the picture it finished on an earlier day. */
    @Test
    fun aPictureFinishedOnAnEarlierDayAndPlayedAgainIsNotAsked() {
        firstRunAsked()
        runBlocking { app.store.create(ref, small) }
        app.store.repository.writePlay(ref, PlayTimes(firstFinishedDay = app.dayClock.today() - 3))
        assertNotAsked()
    }

    private fun assertNotAsked() {
        start()
        compose.mainClock.advanceTimeBy(6_000)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { exists("completion-done") }
        assertTrue(!exists(WHERE_FOUND_ROW_TAG), "no row")
        assertTrue(!app.firstRun.whereFoundDue() && !app.firstRun.whereFoundOpen(), "decided as not shown, for good")
    }

    /** A first-run install with analytics consent, an empty inventory (the reward is once per inventory) and a pinned order. */
    private fun firstRunAsked() {
        app.resetFirstRunForTest(fresh = true)
        app.whereFoundConsentForTest = true
        app.whereFoundSeedForTest = 1
        File(context.filesDir, StorageLayout.INVENTORY).delete()
        app.resetMonetizationForTest()
    }

    private fun completion(): CompletionViewModel {
        var vm: CompletionViewModel? = null
        scenario.onActivity { vm = ViewModelProvider(it)[CompletionViewModel::class.java] }
        return vm!!
    }

    /**
     * Places every gem but the last, holds the test clock, places the last, waits (in real time) for the finished
     * picture, then watches the celebration's layers frame by frame from the moment the sequence starts.
     */
    private fun finishAndWatch(): Map<Int, Set<String>> {
        start()
        return watch()
    }

    /** Places every gem but the last, holds the test clock, places the last and waits for the finished picture. */
    private fun start() {
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
        // A case may have made it already (with a play file of its own).
        if (!app.store.repository.dirOf(ref).isDirectory) runBlocking { app.store.create(ref, small) }
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
