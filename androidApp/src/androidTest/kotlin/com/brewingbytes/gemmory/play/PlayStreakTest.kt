package com.brewingbytes.gemmory.play

import android.app.Activity
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.gallery.STREAK_TAG
import com.brewingbytes.gemmory.streak.STREAK_SCREEN_TAG
import com.brewingbytes.gemmory.policy.Cover
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.HistoryDay
import com.brewingbytes.gemmory.policy.InventoryCodec
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.StreakHistoryCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.FileStateStore
import com.brewingbytes.gemmory.storage.StorageLayout
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The daily play streak (#33): finishing a picture today extends yesterday's streak (a gem alone doesn't), and the
 * gallery shows it from two days on; a streak that broke shows nothing. #101: the new day is committed with its
 * pending celebration, which survives a recreate and a restart, and logged in the day log with any covered day; the
 * chip shows from day 1 and opens the streak screen.
 */
@RunWith(AndroidJUnit4::class)
class PlayStreakTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val ref = CanvasRef(CanvasKind.PHOTO, "streak")
    private val prefs get() = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
    private lateinit var scenario: ActivityScenario<MainActivity>

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
        // No real AdMob: an interstitial after Done would cover the gallery (lessons, #50).
        app.resetMonetizationForTest(NoAds)
    }

    /**
     * #102, E5/O3 (R1): the process dies after the new day is committed but before its streak freeze is spent. The
     * day is kept, the freeze too (a crash never loses a day; it may leave a freeze), and the restarted app neither
     * counts the day again nor spends the freeze for it.
     */
    @Test
    fun aCrashBetweenTheSaveAndTheSpendKeepsTheDayAndTheFreeze() = runBlocking<Unit> {
        val today = LocalDate.now().toEpochDay()
        // Yesterday was missed and the day off was used 3 days ago: only a streak freeze covers it.
        prefs.edit().clear().putInt("length", 4).putLong("lastDay", today - 2).putLong("restDay", today - 5).commit()
        File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
        val inventory = File(context.filesDir, StorageLayout.INVENTORY)
        inventory.parentFile!!.mkdirs()
        inventory.writeBytes(InventoryCodec.encode(InventoryState(freezes = 1)))
        app.resetMonetizationForTest(NoAds)
        val history = FileStateStore(File(context.filesDir, StorageLayout.STREAK_HISTORY), StreakHistoryCodec::encode, StreakHistoryCodec::decode)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // The policy thread resolves and saves the day, then "dies" before Monetization spends the freeze.
        val dying = PlayStreak(context, app.dayClock, app.analytics, history, scope, resolve = { block ->
            block(false, 1)
            throw IllegalStateException("killed before the spend")
        })
        assertTrue(runCatching { dying.played("PHOTO/streak").await() }.isFailure)
        assertEquals(5, prefs.getInt("length", 0), "the day is committed")
        assertEquals(today, prefs.getLong("lastDay", 0))
        assertEquals(1, app.monetization.freezes(), "the freeze is kept")

        // The restarted app: the day is already counted, so nothing is resolved or spent again.
        app.resetStreakForTest(celebrations = false)
        assertNull(app.streak.played("PHOTO/streak").await())
        assertEquals(5, app.streak.current(Cover(freezes = 1)))
        assertEquals(1, app.monetization.freezes())
        scope.cancel()
        inventory.delete()
    }

    @After
    fun tearDown() {
        app.resetMonetizationForTest()
        if (::scenario.isInitialized) scenario.close()
        prefs.edit().clear().commit()
        File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
        app.resetStreakForTest()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** The app reads the streak once at start; this runs before the activity, in the same process. */
    private fun seed(length: Int, daysAgo: Long) {
        prefs.edit().clear()
            .putInt("length", length)
            .putLong("lastDay", LocalDate.now().toEpochDay() - daysAgo)
            .commit()
        File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
        // The day itself, not its celebration (#101, StreakFlowTest): Done goes straight to the gallery.
        app.resetStreakForTest(celebrations = false)
    }

    @Test
    fun finishingAPictureTodayExtendsYesterdaysStreak() {
        seed(length = 2, daysAgo = 1)
        launchOnMyPhotos()
        compose.waitUntil(5_000) { chipText()?.startsWith("2 days in a row,") == true }
        openTile()
        instrumentation.runOnMainSync { session()!!.placeNext() }
        compose.waitForIdle()
        back()
        compose.waitUntil(5_000) { exists(TILE) }
        compose.waitForIdle()
        assertEquals("2 days in a row, not done today, opens your streak", chipText(), "a gem alone doesn't count the day")

        openTile()
        instrumentation.runOnMainSync { val s = session()!!; while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { exists("completion-done") }
        compose.onNodeWithTag("completion-done").performClick()
        compose.waitUntil(5_000) { chipText()?.startsWith("3 days in a row,") == true }
    }

    /** #101 E2, O3: the new day and its celebration are committed together, and outlive a recreate and a restart. */
    @Test
    fun aNewDayLeavesACelebrationThatSurvivesARecreateAndARestart() {
        seed(length = 2, daysAgo = 1)
        launchOnMyPhotos()
        finishAndLeave()
        compose.waitUntil(5_000) { chipText()?.startsWith("3 days in a row,") == true }
        val today = LocalDate.now().toEpochDay()
        val pending = assertNotNull(app.streak.pendingCelebration())
        assertEquals(today, pending.day)
        assertEquals(3, pending.length)
        assertTrue(pending.covered.isEmpty())
        assertEquals("PHOTO/streak", pending.canvas)
        assertEquals(3, prefs.getInt("length", 0), "committed, not waiting on apply()")
        assertEquals(today, prefs.getLong("pendingDay", 0))

        scenario.recreate()
        assertEquals(pending, app.streak.pendingCelebration(), "a rotation keeps it")
        app.resetStreakForTest(celebrations = false) // what a new process reads
        assertEquals(pending, app.streak.pendingCelebration(), "so does a restart")
        assertEquals(pending, app.streak.takeCelebration())
        assertNull(app.streak.takeCelebration(), "shown once")
        app.resetStreakForTest()
        assertNull(app.streak.pendingCelebration())
        assertEquals(3, app.streak.state().length, "taking it leaves the streak alone")
    }

    /** #101 E4: the finish is logged with its canvas, and yesterday's day off with it; a second finish adds nothing. */
    @Test
    fun aDayOffIsLoggedWithTheFinishThatUsedIt() {
        seed(length = 2, daysAgo = 2)
        launchOnMyPhotos()
        finishAndLeave()
        compose.waitUntil(5_000) { chipText()?.startsWith("3 days in a row,") == true }
        val today = LocalDate.now().toEpochDay()
        assertEquals(listOf(CoveredDay(today - 1, DayKind.DAY_OFF)), app.streak.pendingCelebration()?.covered)
        val logged = listOf(HistoryDay(today - 1, DayKind.DAY_OFF), HistoryDay(today, DayKind.FINISHED, "PHOTO/streak"))
        assertEquals(logged, app.streak.history())
        assertNull(runBlocking { app.streak.played("PHOTO/other").await() }, "the same day counts once")
        assertEquals(logged, app.streak.history())
    }

    /** #101 G18: the chip shows from day 1, its flame lit once today is done, and it opens "Your streak". */
    @Test
    fun theChipShowsFromDayOneAndOpensTheStreakScreen() {
        seed(length = 0, daysAgo = 0)
        prefs.edit().clear().commit()
        app.resetStreakForTest(celebrations = false)
        launchOnMyPhotos()
        assertFalse(exists(STREAK_TAG), "no streak yet: no chip")
        finishAndLeave()
        compose.waitUntil(5_000) { chipText() == "1 day in a row, done today, opens your streak" }
        compose.onNodeWithTag(STREAK_TAG).performClick()
        compose.waitUntil(5_000) { exists(STREAK_SCREEN_TAG) }
        compose.onNode(hasText("Your streak")).assertExists()
        back()
        compose.waitUntil(5_000) { exists(STREAK_TAG) }
    }

    @Test
    fun aBrokenStreakShowsNothing() {
        seed(length = 5, daysAgo = 3)
        launchOnMyPhotos()
        compose.waitForIdle()
        assertFalse(exists(STREAK_TAG), "two days missed: no chip, and no message about it")
    }

    private fun finishAndLeave() {
        openTile()
        instrumentation.runOnMainSync { val s = session()!!; while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { exists("completion-done") }
        compose.onNodeWithTag("completion-done").performClick()
        runBlocking { app.streak.settled() }
    }

    private fun openTile() {
        compose.onNodeWithTag(TILE).performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
    }

    private fun back() = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun launchOnMyPhotos() {
        runBlocking { app.store.create(ref, small) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        compose.waitUntil(5_000) { exists(TILE) }
    }

    private fun chipText(): String? = compose.onAllNodes(hasTestTag(STREAK_TAG)).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("")

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

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

    private companion object {
        const val TILE = "tile-photo-streak"
    }
}
