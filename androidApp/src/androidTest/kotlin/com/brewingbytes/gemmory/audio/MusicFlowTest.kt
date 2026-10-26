package com.brewingbytes.gemmory.audio

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.gallery.MUSIC_CHIP_TAG
import com.brewingbytes.gemmory.gallery.MUSIC_OFF_TAG
import com.brewingbytes.gemmory.gallery.touchExplorationForTest
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.settings.MUSIC_TAG
import com.brewingbytes.gemmory.settings.MUSIC_VOLUME_TAG
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Ambient music (T42; design system, Settings → Music): silent on the cold-opened gallery, 30% from the first canvas
 * (D24), the one-time chip (8s, none under TalkBack, D20), silent and vibrate, audio focus, ads, the time-lapse export
 * and the background. The emulator runs with `-no-audio`; the player still plays (to a null sink), and the test reads
 * the controller, not the speaker.
 */
@RunWith(AndroidJUnit4::class)
class MusicFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as GemmoryApp
    private val audio = context.getSystemService(AudioManager::class.java)
    private val ads = FakeAds()
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** A full-screen ad the test opens and closes. */
    private class FakeAds : AdGateway {
        val showing = MutableStateFlow(false)
        override val fullScreen: StateFlow<Boolean> = showing
        override val sdkReady = true

        override fun online() = true

        override suspend fun ensureConsent(activity: Activity) = true

        override suspend fun start() = Unit

        override fun preloadRewarded() = Unit

        override suspend fun loadRewarded(): RewardedLoad = RewardedLoad.Failed(LoadFailure.OTHER)

        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false

        override fun preloadInterstitial() = Unit

        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }

    @Before
    fun freshInstall() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            app.settings.resetMusicForTest()
            app.music.ringerModeForTest = AudioManager.RINGER_MODE_NORMAL
        }
        touchExplorationForTest = false
        app.resetMonetizationForTest(ads)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(STARTER)).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        scenario.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.music.ringerModeForTest = null }
        touchExplorationForTest = null
        app.resetMonetizationForTest()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    private fun playing() = app.music.playing.value

    private fun awaitPlaying(on: Boolean, ms: Long = 5_000) = compose.waitUntil(ms) { playing() == on }

    private fun openStarter() {
        compose.onNodeWithTag(STARTER).performClick()
        awaitCanvas()
        awaitPlaying(true)
    }

    /** The canvas is up and handles back itself; a back before that would leave the app. */
    private fun awaitCanvas() = compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isNotEmpty() }

    private fun back() = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun chipShown() = compose.onAllNodes(hasTestTag(MUSIC_CHIP_TAG)).fetchSemanticsNodes().isNotEmpty()

    private fun focusRequest(gain: Int) = AudioFocusRequest.Builder(gain)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
        .setOnAudioFocusChangeListener {}
        .build()

    @Test
    fun theColdOpenIsSilentThenThirtyPercentFromTheFirstCanvas() {
        Thread.sleep(1_500)
        assertFalse(playing(), "never on the cold-opened gallery (D24)")
        openStarter()
        assertEquals(0.3f, app.music.volumeForTest)
        // From then on it plays in the gallery too.
        back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(STARTER)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(playing())
    }

    @Test
    fun theChipShowsOnceOnTheNextGalleryVisitAndGoesAfterEightSeconds() {
        openStarter()
        back()
        compose.waitUntil(5_000) { chipShown() }
        // The chip's 8s runs on the test clock.
        compose.mainClock.advanceTimeBy(7_000)
        assertTrue(chipShown(), "still up before 8s")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitUntil(3_000) { !chipShown() }
        // Never again.
        openStarter()
        back()
        Thread.sleep(1_000)
        assertFalse(chipShown())
    }

    @Test
    fun underTalkBackTheChipStaysUntilTurnOffWhichSwitchesMusicOff() {
        touchExplorationForTest = true
        openStarter()
        back()
        compose.waitUntil(5_000) { chipShown() }
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertTrue(chipShown(), "no 8s timeout under TalkBack (D20)")
        compose.onNodeWithTag("gallery-music-off-button").performClick()
        awaitPlaying(false)
        assertFalse(app.settings.music.value)
        compose.onNodeWithTag(MUSIC_OFF_TAG).assertExists()
        assertFalse(chipShown())
    }

    @Test
    fun silentAndVibrateTurnItOffAndNormalBringsItBack() {
        openStarter()
        for (mode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { app.music.ringerModeForTest = mode }
            awaitPlaying(false)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { app.music.ringerModeForTest = AudioManager.RINGER_MODE_NORMAL }
            awaitPlaying(true)
        }
    }

    @Test
    fun aFirstCanvasInSilentModeStartsNothingAndShowsNoChip() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.music.ringerModeForTest = AudioManager.RINGER_MODE_SILENT }
        compose.onNodeWithTag(STARTER).performClick()
        awaitCanvas()
        Thread.sleep(1_500)
        assertFalse(playing())
        back()
        Thread.sleep(1_000)
        assertFalse(chipShown(), "not shown when music didn't start")
    }

    @Test
    fun focusLossPausesAndTheGainResumes() {
        openStarter()
        val transient = focusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(transient))
        awaitPlaying(false)
        audio.abandonAudioFocusRequest(transient)
        awaitPlaying(true)
        // Lost for good (another app's media): it waits for the next foreground.
        val other = focusRequest(AudioManager.AUDIOFOCUS_GAIN)
        audio.requestAudioFocus(other)
        awaitPlaying(false)
        audio.abandonAudioFocusRequest(other)
        Thread.sleep(1_000)
        assertFalse(playing())
        scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(1_500)
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitPlaying(true)
    }

    @Test
    fun aFullScreenAdAndTheExportPauseIt() {
        openStarter()
        ads.showing.value = true
        awaitPlaying(false)
        ads.showing.value = false
        awaitPlaying(true)

        val ref = CanvasRef(CanvasKind.BUNDLED, "starter")
        val bytes = context.assets.open(BundledPictures.STARTER.asset).use { it.readBytes() }
        val pattern = (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        app.encodes.share(EncodeRequest(ref, pattern, pattern.cells.indices.toList().toIntArray(), "The Starry Night", source = null, watermark = true))
        awaitPlaying(false)
        app.encodes.cancel(ref)
        awaitPlaying(true, 10_000)
    }

    @Test
    fun theBackgroundStopsIt() {
        openStarter()
        scenario.moveToState(Lifecycle.State.CREATED)
        // The process counts as in the background 700ms after its last activity stops.
        awaitPlaying(false)
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitPlaying(true)
    }

    /** R1: the Music switch is its own: off stops it and hides the volume, and the volume moves in 10% steps. */
    @Test
    fun settingsSwitchAndVolume() {
        openStarter()
        back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(STARTER)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(MUSIC_VOLUME_TAG)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(MUSIC_VOLUME_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        compose.waitUntil(2_000) { app.settings.musicVolume.value == 50 }
        assertEquals(0.5f, app.music.volumeForTest)
        compose.onNodeWithTag(MUSIC_TAG).performClick()
        awaitPlaying(false)
        assertTrue(compose.onAllNodes(hasTestTag(MUSIC_VOLUME_TAG)).fetchSemanticsNodes().isEmpty(), "off hides the volume")
        assertTrue(app.settings.sound.value, "Sound stays as it was")
        compose.onNodeWithTag(MUSIC_TAG).performClick()
        awaitPlaying(true)
    }

    private companion object {
        const val STARTER = "tile-bundled-starter"
    }
}
