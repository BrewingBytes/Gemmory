package com.brewingbytes.gemmory.play

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
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.gallery.STREAK_TAG
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
import java.time.LocalDate
import kotlin.test.assertFalse

/**
 * The daily play streak (#33): the first gem of today extends yesterday's streak, and the gallery shows it from two
 * days on; a streak that broke shows nothing.
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
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        prefs.edit().clear().commit()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** The app reads the streak once at start; this runs before the activity, in the same process. */
    private fun seed(length: Int, daysAgo: Long) {
        prefs.edit().clear()
            .putInt("length", length)
            .putLong("lastDay", LocalDate.now().toEpochDay() - daysAgo)
            .commit()
        app.resetStreakForTest()
    }

    @Test
    fun theFirstGemOfTodayExtendsYesterdaysStreak() {
        seed(length = 2, daysAgo = 1)
        launchOnMyPhotos()
        compose.waitUntil(5_000) { chipText() == "2 days in a row" }
        compose.onNodeWithTag(TILE).performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
        instrumentation.runOnMainSync { session()!!.placeNext() }
        compose.waitForIdle()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { chipText() == "3 days in a row" }
    }

    @Test
    fun aBrokenStreakShowsNothing() {
        seed(length = 5, daysAgo = 3)
        launchOnMyPhotos()
        compose.waitForIdle()
        assertFalse(exists(STREAK_TAG), "two days missed: no chip, and no message about it")
    }

    private fun launchOnMyPhotos() {
        runBlocking { app.store.create(ref, small) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNode(hasText("My Photos")).performClick()
        compose.waitUntil(5_000) { exists(TILE) }
    }

    private fun chipText(): String? = compose.onAllNodes(hasTestTag(STREAK_TAG)).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TILE = "tile-photo-streak"
    }
}
