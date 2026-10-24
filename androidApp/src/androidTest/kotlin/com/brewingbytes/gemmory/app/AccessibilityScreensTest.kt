package com.brewingbytes.gemmory.app

import com.brewingbytes.gemmory.R
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.ui.SHEET_TITLE_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

/**
 * TalkBack outside the canvas (design D19): gallery tiles read their state and carry their ⋮ actions, icon buttons are
 * labelled, and sheets move TalkBack's focus to their title on open and back to the trigger on close.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val photo = CanvasRef(CanvasKind.PHOTO, "a11y-tile")
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var talkBack = false

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        runBlocking { (context.applicationContext as GemmoryApp).store.create(photo, A11yTestSupport.pattern(10, 10, 4)) }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        if (talkBack) A11yTestSupport.talkBackOff()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun actionLabels(tag: String): List<String> =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    @Test
    fun tilesReadTheirStateAndCarryTheirMenuAsActions() {
        launch()
        compose.onNodeWithTag("tile-bundled-starter").assertContentDescriptionEquals("The Starry Night, 0% done").assertHasClickAction()
        assertEquals(listOf("Start over"), actionLabels("tile-bundled-starter"))
        compose.onNodeWithTag("tile-bundled-starter").performCustomAccessibilityActionWithLabel("Start over")
        compose.onNodeWithTag(SHEET_TITLE_TAG).assertExists()
        compose.onNodeWithText("Start this picture over?").assertExists()
        compose.onNodeWithText("Keep").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("My Photos").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tile-photo-a11y-tile")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-photo-a11y-tile").assertContentDescriptionEquals("${context.getString(R.string.photo_default_title)}, 0% done")
        assertEquals(listOf("Rename", "Delete"), actionLabels("tile-photo-a11y-tile"))
        compose.onNodeWithTag("tile-photo-a11y-tile").performCustomAccessibilityActionWithLabel("Rename")
        compose.onNodeWithTag(SHEET_TITLE_TAG).assertExists()
        compose.onNode(hasText("Rename") and hasTestTag(SHEET_TITLE_TAG)).assertExists()
    }

    @Test
    fun everyIconButtonHasALabel() {
        launch()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun withTalkBackASheetTakesFocusOnItsTitleAndGivesItBackToTheTrigger() {
        assumeTrue("needs TalkBack on the device", A11yTestSupport.talkBackInstalled)
        talkBack = true
        A11yTestSupport.talkBackOn()
        launch()
        // A tile's ⋮ action opens its sheet; focus goes to the title, then back to that tile.
        compose.onNodeWithTag("tile-bundled-starter").performCustomAccessibilityActionWithLabel("Start over")
        A11yTestSupport.waitFor(5_000, "focus on the Start over title") {
            compose.waitForIdle()
            A11yTestSupport.accessibilityFocusText() == "Start this picture over?"
        }
        compose.onNodeWithText("Keep").performSemanticsAction(SemanticsActions.OnClick)
        A11yTestSupport.waitFor(5_000, "focus back on the tile") {
            compose.waitForIdle()
            A11yTestSupport.accessibilityFocusText() == "The Starry Night, 0% done"
        }

        // A Settings row: the same both ways. "Privacy choices" may appear once UMP answers, so let the list settle.
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("settings-delete-all")).fetchSemanticsNodes().isNotEmpty() }
        android.os.SystemClock.sleep(1_500)
        compose.onNodeWithTag("settings-delete-all").performSemanticsAction(SemanticsActions.OnClick)
        A11yTestSupport.waitFor(5_000, "focus on the sheet title") {
            compose.waitForIdle()
            A11yTestSupport.accessibilityFocusText() == "Delete all photo canvases?"
        }
        compose.onNodeWithText("Keep").performSemanticsAction(SemanticsActions.OnClick)
        A11yTestSupport.waitFor(5_000, "focus back on the row") {
            compose.waitForIdle()
            A11yTestSupport.accessibilityFocusText() == "Delete all photo canvases"
        }

        // The paywall (#8) is a screen, not a sheet: the same both ways, from the Membership row.
        compose.onNodeWithTag("settings-membership").performSemanticsAction(SemanticsActions.OnClick)
        // The screen's own coroutines run on the test clock (a sheet has its own window and real time).
        compose.mainClock.advanceTimeBy(500)
        A11yTestSupport.waitFor(5_000, "focus on the paywall title") {
            compose.waitForIdle()
            A11yTestSupport.accessibilityFocusText() == "Gemmory VIP"
        }
        compose.onNodeWithContentDescription("Close").performSemanticsAction(SemanticsActions.OnClick)
        A11yTestSupport.waitFor(5_000, "focus back on Membership") {
            compose.waitForIdle()
            A11yTestSupport.accessibilityFocusText()?.startsWith("Membership") == true
        }
    }
}
