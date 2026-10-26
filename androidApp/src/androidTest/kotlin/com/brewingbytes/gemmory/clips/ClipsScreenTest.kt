package com.brewingbytes.gemmory.clips

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.settings.CLIPS_ROW_TAG
import com.brewingbytes.gemmory.settings.SettingsScreen
import com.brewingbytes.gemmory.ui.GemmoryTheme
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * The founder clip tool (T47, queue #152): the starter, picked from the dropdown, queued twice (English in every format,
 * then German at 9:16) renders five files in order; debug Settings has the Clips row.
 */
@RunWith(AndroidJUnit4::class)
class ClipsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "clips")
    private val id = BundledPictures.STARTER.id
    private val files = listOf(
        "$id-by-color-en.mp4", "$id-by-color-4x5-en.mp4", "$id-by-color-feed-en.mp4", "$id-before-after-en.png", "$id-finished.png",
        "$id-by-color-de.mp4",
    ).map { File(dir, it) }

    @Before
    @After
    fun clear() {
        files.forEach { it.delete() }
    }

    @Test
    fun aQueueRendersEachLanguageInTurn() {
        compose.setContent { GemmoryTheme { ClipsScreen(onBack = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(PICTURE_TAG)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(PICTURE_TAG).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("$ITEM_TAG$id")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("$ITEM_TAG$id").performClick()
        // English (the app's language on a test device) in every format.
        compose.onNodeWithTag("${LANGUAGE_TAG}en").performScrollTo().assertIsOn()
        compose.onNodeWithTag(ADD_TAG).performScrollTo().performClick()
        // Then German alone, 9:16 only.
        compose.onNodeWithTag("${LANGUAGE_TAG}en").performScrollTo().performClick()
        compose.onNodeWithTag("${LANGUAGE_TAG}de").performScrollTo().performClick()
        compose.onNode(hasText("4:5")).performScrollTo().performClick()
        compose.onNode(hasText("7 s feed")).performScrollTo().performClick()
        compose.onNode(hasText("Stills")).performScrollTo().performClick()
        compose.onNodeWithTag(ADD_TAG).performScrollTo().performClick()
        compose.waitUntil(480_000) {
            compose.onAllNodes(hasTestTag("${QUEUE_TAG}1") and hasText("Saved 1 file", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag("${ERROR_TAG}0") or hasTestTag("${ERROR_TAG}1")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodes(hasTestTag("${ERROR_TAG}0") or hasTestTag("${ERROR_TAG}1")).fetchSemanticsNodes().isEmpty(), "no error line")
        compose.onNodeWithTag("${QUEUE_TAG}0").assert(hasText("Saved 5 files", substring = true))
        for (file in files) assertTrue(file.length() > 0, "${file.name} saved")
        assertTrue(!File(dir, "$id-by-color-4x5-de.mp4").exists(), "German was queued at 9:16 only")
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".partial") }, "no partial files left")
    }

    @Test
    fun debugSettingsHaveTheClipsRow() {
        // The perf build compiles src/debug but hides the row (not debuggable, no Clips in its manifest).
        assumeTrue(BuildConfig.DEBUG)
        compose.setContent { GemmoryTheme { SettingsScreen(onBack = {}, onMembership = {}) } }
        compose.onNodeWithTag(CLIPS_ROW_TAG).performScrollTo().assertExists()
    }
}
