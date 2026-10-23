package com.brewingbytes.gemmory.share

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.gallery.GalleryState
import com.brewingbytes.gemmory.gallery.GalleryViewModel
import com.brewingbytes.gemmory.gallery.SHARED_OFFER_TAG
import com.brewingbytes.gemmory.gallery.TODAY_TAG
import com.brewingbytes.gemmory.gallery.locked
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.play.SHARED_TOAST_TAG
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.FirstRun
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
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
 * T50 (shared picture landing) through the real app: a share link's picture on a fresh install's first launch. The
 * process can't be cold-started from a test (lessons 2026-10-20), so each case starts the landing as a fresh install's
 * first launch would, with the referrer string given (`resetSharedLandingForTest`, through the same parser as
 * `-Pgemmory.fakeReferrer`), and the catalog from a fake source signed by a test key.
 */
@RunWith(AndroidJUnit4::class)
class SharedLandingFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val catalog = TestCatalog()
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** A locked bundled picture: the landing opens it free. */
    private val locked = BundledPictures.ALL.first { it.locked }
    private val lockedRef = CanvasRef(CanvasKind.BUNDLED, locked.id)

    @Before
    fun freshInstall() {
        clear()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetMonetizationForTest(NoAds)
        app.resetTodaysForTest()
        app.resetAchievementsForTest()
        app.resetRemoteForTest(FakeSource(catalog), listOf(TestCatalog.KEY))
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        clear()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        // Back to this process's own launch: not fresh, no landing.
        app.resetSharedLandingForTest(null, fresh = false)
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetAchievementsForTest()
        app.resetRemoteForTest()
    }

    private fun clear() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        File(context.filesDir, StorageLayout.ACHIEVEMENTS).delete()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TodaysPictures.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(FirstRun.FUNNEL_FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun land(content: String) = app.resetSharedLandingForTest("utm_source=share&utm_medium=bundled&utm_campaign=timelapse&utm_content=$content")

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun opened(): CanvasRef? {
        var ref: CanvasRef? = null
        scenario.onActivity { ref = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session?.ref }
        return ref
    }

    private fun gallery(): GalleryState {
        var state = GalleryState()
        scenario.onActivity { state = ViewModelProvider(it)[GalleryViewModel::class.java].state.value }
        return state
    }

    private fun back() = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun aBundledPictureOpensOnceFreeWithItsToastThenTheGalleryIsTheNormalGame() {
        land(locked.id)
        launch()
        compose.waitUntil("the shared canvas", 10_000) { opened() == lockedRef }
        compose.waitUntil("its toast", 3_000) {
            compose.onAllNodes(hasTestTag(SHARED_TOAST_TAG) and hasText("Shared with you: ${locked.title}"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(app.todays.isFreeToday(lockedRef), "free to start today, like Today's picture")

        back()
        compose.waitUntil("the gallery", 10_000) { exists("gallery-grid") && gallery().loaded }
        // DR4: no first finish, but the player is in the normal game once they leave the shared picture.
        compose.waitUntil("the Today card", 10_000) {
            runCatching { compose.onNodeWithTag("gallery-grid").performScrollToIndex(0) }
            exists(TODAY_TAG)
        }
        val tile = gallery().pictures.first { it.ref == lockedRef }
        val s = gallery()
        assertFalse(tile.locked(app.monetization.unlocks.unlocked.value, app.monetization.entitlements.value, s.todayFree), "no lock label today")
        compose.onNodeWithTag("gallery-grid").performScrollToNode(hasTestTag(tileTag(lockedRef)))

        // Once: a relaunch of the activity in the same session doesn't open it again.
        scenario.close()
        launch()
        compose.waitUntil("the gallery", 10_000) { exists("gallery-grid") && gallery().loaded }
        Thread.sleep(1_000)
        assertEquals(null, opened(), "nothing opens by itself a second time")
    }

    @Test
    fun aHiddenDropOpensNothing() {
        catalog.add("harbor-lights", CanvasSize.QUICK, locked = true, availableFrom = LocalDate.fromEpochDays(app.dayClock.today().toInt()).plus(DatePeriod(days = 1)).toString())
        land("harbor-lights")
        launch()
        compose.waitUntil("the first catalog check", 15_000) { app.remote.checksEnded.value > 0 }
        compose.waitUntil("the gallery", 10_000) { exists("gallery-grid") && gallery().loaded }
        Thread.sleep(1_000)
        assertEquals(null, opened(), "a hidden drop never opens")
        assertFalse(exists(SHARED_OFFER_TAG), "and isn't offered")
        assertFalse(app.firstRun.landed)
    }

    @Test
    fun aRemotePictureIsOfferedAfterTheFirstCatalogCheckAndOpenStartsItFree() {
        catalog.add("harbor-lights", CanvasSize.QUICK, locked = true)
        // A real download takes a moment: the gallery is in view before the check ends (the fake answers at once, and a
        // picture ready at the first look opens directly instead).
        val fake = FakeSource(catalog)
        var first = true
        app.resetRemoteForTest(
            { path, ifNoneMatch, maxBytes ->
                if (first) {
                    first = false
                    Thread.sleep(2_000)
                }
                fake.fetch(path, ifNoneMatch, maxBytes)
            },
            listOf(TestCatalog.KEY),
        )
        land("harbor-lights")
        launch()
        // Not on the phone yet: the gallery draws, the check downloads it, and it's offered with no jump (DR2).
        compose.waitUntil("the offer", 20_000) { exists(SHARED_OFFER_TAG) || opened() != null }
        assertTrue(exists(SHARED_OFFER_TAG), "offered, not opened: it arrived after the gallery drew")
        assertEquals(null, opened(), "no jump")
        compose.onNodeWithTag("gallery-shared-open", useUnmergedTree = true).performClick()
        val ref = CanvasRef(CanvasKind.REMOTE, "harbor-lights")
        compose.waitUntil("its canvas", 10_000) { opened() == ref }
        compose.waitUntil("its toast", 3_000) { exists(SHARED_TOAST_TAG) }
        assertTrue(app.todays.isFreeToday(ref))
        assertTrue(app.firstRun.landed)
    }

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
}
