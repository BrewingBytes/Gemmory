package com.brewingbytes.gemmory.clips

import android.os.Environment
import androidx.activity.ComponentActivity
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

/** The founder clip tool (T47): one picture renders four files; debug Settings has the Clips row. */
@RunWith(AndroidJUnit4::class)
class ClipsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "clips")
    private val id = BundledPictures.STARTER.id
    private val files = listOf("$id-by-color.mp4", "$id-by-color-4x5.mp4", "$id-before-after.png", "$id-finished.png").map { File(dir, it) }

    @Before
    @After
    fun clear() {
        files.forEach { it.delete() }
    }

    @Test
    fun onePictureRendersFourFiles() {
        compose.setContent { GemmoryTheme { ClipsScreen(onBack = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("$RENDER_TAG$id")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("$RENDER_TAG$id").performClick()
        compose.waitUntil(240_000) {
            compose.onAllNodes(hasTestTag("$STATUS_TAG$id") and hasText("Saved 4 files")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag("$ERROR_TAG$id")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodes(hasTestTag("$ERROR_TAG$id")).fetchSemanticsNodes().isEmpty(), "no error line")
        for (file in files) assertTrue(file.length() > 0, "${file.name} saved")
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
