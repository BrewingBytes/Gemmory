package com.brewingbytes.gemmory.app

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.settings.LANGUAGE_TAG
import com.brewingbytes.gemmory.storage.StorageLayout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

/**
 * T49 (#136): Gemmory's own language picked while a canvas is open (Android 13+, what Settings → Language opens). The
 * activity is recreated in the new language, the canvas comes back with every gem, and its title, the gallery and
 * Settings' Language row read in that language; the row is the language's own name.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        assumeTrue("per-app languages need Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        app.resetStreakForTest(celebrations = false)
        app.resetTodaysForTest(enabled = false)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
    }

    @After
    fun tearDown() {
        TestLanguage.set(context, null)
        if (::scenario.isInitialized) scenario.close()
        app.resetStreakForTest()
        app.resetTodaysForTest()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private fun waitText(text: String) =
        compose.waitUntil(text, 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aLanguagePickedMidCanvasKeepsTheCanvasAndSwitchesItsWords() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-bundled-starter").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        waitText("The Starry Night")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { repeat(25) { session()!!.placeNext() } }
        val placed = session()!!.placedCount
        assertEquals(25, placed)

        TestLanguage.set(context, "de")
        waitText("Die Sternennacht")
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        assertEquals(placed, session()!!.placedCount, "the canvas comes back with every gem")

        compose.onNodeWithContentDescription("Zurück").performClick()
        waitText("Bilder")
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithTag(LANGUAGE_TAG).performScrollTo()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("settings-language-value") and hasText("Deutsch"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        TestLanguage.set(context, "ja")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("settings-language-value") and hasText("日本語"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("設定", TestLanguage.text(context, R.string.settings_title))
    }
}
