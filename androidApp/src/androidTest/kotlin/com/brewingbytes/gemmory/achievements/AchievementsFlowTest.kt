package com.brewingbytes.gemmory.achievements

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.share.ShareCompletedReceiver
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Play Games achievements (#49) end to end against a fake games client: a finish, a share and a streak day are
 * counted as they happen and reach Play Games only once the Gallery shows, never on the canvas or the completion
 * screen; offline, they wait and go out later.
 */
@RunWith(AndroidJUnit4::class)
class AchievementsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val ref = CanvasRef(CanvasKind.PHOTO, "achievements")
    private val games = FakeGames()
    private lateinit var scenario: ActivityScenario<MainActivity>

    private val small = Pattern(
        width = 8,
        height = 6,
        palette = List(3) { PaletteColor(it + 1, (0xFF shl 24) or (0x305070 + it * 0x303030), Lab(it * 20.0, 0.0, 0.0)) },
        cells = IntArray(48) { it % 3 },
    )

    @Before
    fun setUp() {
        clear()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetStreakForTest()
        // No real AdMob: an interstitial after Done would cover the gallery (lessons, #50).
        app.resetMonetizationForTest(NoAds)
        app.resetAchievementsForTest(games) { it.name.lowercase() }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        app.resetMonetizationForTest()
        clear()
        app.resetStreakForTest()
        app.resetAchievementsForTest()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun clear() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.ACHIEVEMENTS).delete()
        context.getSharedPreferences("streak", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aFinishAndItsStreakDayReachPlayGamesOnceTheGalleryShows() {
        launchOnMyPhotos()
        openTile()
        instrumentation.runOnMainSync { val s = session()!!; while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { exists("completion-done") }
        // Give a stray send time to arrive: nothing goes out on the completion screen.
        Thread.sleep(1_000)
        assertEquals(emptyList(), games.server.toList(), "nothing is sent on the canvas or the completion screen")
        compose.onNodeWithTag("completion-done").performClick()
        val expected = listOf(
            "first_sparkle",
            "your_own_gem",
            "tray_regular=1",
            "gallery_wall=1",
            "photo_album=1",
            "a_week_of_gems=1",
            "a_month_of_gems=1",
        )
        compose.waitUntil("sent from the gallery", 5_000) { games.server.size >= expected.size }
        assertEquals(expected.sorted(), games.server.toList().sorted())
    }

    @Test
    fun aShareIsCountedAndSentWhileTheGalleryShows() {
        launchOnMyPhotos()
        share(progress = true)
        compose.waitUntil("progress share sent", 5_000) { games.server.contains("work_in_progress") }
        share(progress = false)
        compose.waitUntil("finished share sent", 5_000) { games.server.contains("show_and_tell") }
        assertEquals(listOf("work_in_progress", "show_and_tell"), games.server.toList())
    }

    @Test
    fun offlineUnlocksWaitAndGoOutFromSettingsLater() {
        games.online = false
        launchOnMyPhotos()
        share(progress = false)
        compose.waitUntil("counted", 5_000) { app.achievements.state.unlocked.isNotEmpty() }
        assertTrue(games.server.isEmpty())
        games.online = true
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil("sent from Settings", 5_000) { games.server.contains("show_and_tell") }
        assertEquals(listOf("show_and_tell"), games.server.toList(), "sent once")
    }

    /** What the share sheet's callback does when the player picks an app (#36 kinds). */
    private fun share(progress: Boolean) {
        context.sendBroadcast(
            Intent(context, ShareCompletedReceiver::class.java)
                .putExtra(AnalyticsEvent.SOURCE, "photo")
                .putExtra(AnalyticsEvent.SIZE, "other")
                .putExtra(AnalyticsEvent.KIND, AnalyticsEvent.kind(progress)),
        )
    }

    private fun openTile() {
        compose.onNodeWithTag(TILE).performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
    }

    private fun launchOnMyPhotos() {
        runBlocking { app.store.create(ref, small) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        compose.waitUntil(5_000) { exists(TILE) }
    }

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
        const val TILE = "tile-photo-achievements"
    }
}
