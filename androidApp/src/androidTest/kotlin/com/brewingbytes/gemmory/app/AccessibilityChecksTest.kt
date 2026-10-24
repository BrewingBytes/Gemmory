package com.brewingbytes.gemmory.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.streak.CELEBRATION_CONTINUE_TAG
import com.brewingbytes.gemmory.streak.CELEBRATION_TAG
import com.brewingbytes.gemmory.streak.STREAK_SCREEN_TAG
import com.brewingbytes.gemmory.achievements.ACHIEVEMENTS_TAG
import com.brewingbytes.gemmory.achievements.FakeGames
import com.brewingbytes.gemmory.ads.RewardPhase
import com.brewingbytes.gemmory.ads.RewardTarget
import com.brewingbytes.gemmory.ads.RewardedSheet
import com.brewingbytes.gemmory.ads.RewardedUi
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.gallery.STREAK_NUDGE_TAG
import com.brewingbytes.gemmory.gallery.STREAK_TAG
import com.brewingbytes.gemmory.gallery.openFinishedTile
import com.brewingbytes.gemmory.game.CanvasZoom
import com.brewingbytes.gemmory.photo.PhotoFlowViewModel
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.Unavailable
import com.brewingbytes.gemmory.share.ShareSheet
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.timelapse.EncodeState
import com.brewingbytes.gemmory.ui.GemmoryTheme
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import java.io.File
import kotlinx.coroutines.runBlocking
import org.hamcrest.BaseMatcher
import org.hamcrest.CoreMatchers
import org.hamcrest.Description
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fails on every Accessibility Scanner finding, warnings included: touch targets, contrast, labels, duplicates. These
 * findings are suppressed on named nodes only:
 * - image contrast on the player's picture itself (previews, the finished picture, Before/After): it is the artwork,
 *   not an icon, and its colors are the photo's;
 * - "Color 7 selected" in the canvas description (design D19 wording): it names the selected color, not a state of
 *   the canvas node, which the check can't tell apart;
 * - text contrast on disabled controls (Start, "Loading video…" at 55%): WCAG exempts inactive components;
 * - text contrast on a row the scroll edge cuts to a sliver (under [SLIVER_PX] high): the check estimates the colors
 *   from a few antialiased pixels (Settings grew with T42's Music rows and a row's top edge sits at the screen's foot).
 */
internal fun scannerValidator(): AccessibilityValidator =
    AccessibilityValidator().setRunChecksFromRootView(true).setThrowExceptionFor(AccessibilityCheckResultType.WARNING)
        .setSuppressingResultMatcher(
            CoreMatchers.anyOf(
                Allowed("ImageContrastCheck") { it in ARTWORK || it.endsWith(" preview") },
                Allowed("RedundantDescriptionCheck") { it.startsWith("Canvas. ") },
                Disabled("TextContrastCheck"),
                Sliver("TextContrastCheck"),
            ),
        )

private val ARTWORK = setOf("Gem preview", "Finished picture", "Before", "After")

/** A finding of [check] on a disabled node, or inside one (a disabled button's label is its own node). */
private class Disabled(private val check: String) : BaseMatcher<AccessibilityViewCheckResult>() {
    override fun matches(item: Any?): Boolean {
        val result = item as? AccessibilityViewCheckResult ?: return false
        if (result.accessibilityHierarchyCheck?.simpleName != check) return false
        var element: ViewHierarchyElement? = result.element
        while (element != null) {
            if (!element.isEnabled) return true
            element = element.parentView
        }
        return false
    }

    override fun describeTo(text: Description) {
        text.appendText("disabled $check")
    }
}

private const val SLIVER_PX = 8

/** A finding of [check] on a node the scroll edge leaves under [SLIVER_PX] high on screen. */
private class Sliver(private val check: String) : BaseMatcher<AccessibilityViewCheckResult>() {
    override fun matches(item: Any?): Boolean {
        val result = item as? AccessibilityViewCheckResult ?: return false
        if (result.accessibilityHierarchyCheck?.simpleName != check) return false
        val bounds = result.element?.boundsInScreen ?: return false
        return bounds.height in 0 until SLIVER_PX
    }

    override fun describeTo(text: Description) {
        text.appendText("sliver $check")
    }
}

/** A finding of [check] on a node whose description passes [description]. */
private class Allowed(private val check: String, private val description: (String) -> Boolean) :
    BaseMatcher<AccessibilityViewCheckResult>() {
    override fun matches(item: Any?): Boolean {
        val result = item as? AccessibilityViewCheckResult ?: return false
        if (result.accessibilityHierarchyCheck?.simpleName != check) return false
        return result.element?.contentDescription?.toString()?.let(description) == true
    }

    override fun describeTo(text: Description) {
        text.appendText("allowed $check")
    }
}

/**
 * The "Accessibility Scanner clean" signal (#14): the Accessibility Test Framework, the Scanner's check engine, run on
 * every main screen and sheet of the app as a player reaches them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val photo = CanvasRef(CanvasKind.PHOTO, "a11y-scan")
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        compose.enableAccessibilityChecks(scannerValidator())
    }

    @After
    fun tearDown() {
        app.resetStreakForTest()
        if (::scenario.isInitialized) scenario.close()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    /** Every window on screen (a sheet is its own window). */
    private fun scan() {
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
    }

    private fun waitForTag(tag: String) =
        compose.waitUntil("tag $tag", 10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String) =
        compose.waitUntil("text $text", 20_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    @Test
    fun galleryAndSettings() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tile-bundled-starter")
        scan()
        compose.onNodeWithText("My Photos").performClick()
        waitForText("Your photos, in gems")
        scan()
        compose.onNodeWithText("Pictures").performClick()
        compose.onNodeWithTag("tile-bundled-starter").performCustomAccessibilityActionWithLabel("Start over")
        waitForText("Start this picture over?")
        scan()
        compose.onNodeWithText("Keep").performClick()

        compose.onNodeWithContentDescription("Settings").performClick()
        waitForTag("settings-delete-all")
        scan()
        // The Selected color sheet (#19).
        compose.onNodeWithTag("settings-highlight").performClick()
        waitForTag("highlight-dim")
        scan()
        compose.onNodeWithTag("highlight-tint").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("highlight-tint")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("settings-delete-all").performClick()
        waitForText("Delete all photo canvases?")
        scan()
    }

    /**
     * #101: the streak celebration (a missed one, shown on the gallery visit after a cold start, G9) and the streak
     * screen it opens, at 1x and 2x font.
     */
    @Test
    fun streakCelebrationAndScreen() {
        val prefs = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toEpochDay()
        try {
            for (scale in listOf("1.0", "2.0")) {
                A11yTestSupport.shell("settings put system font_scale $scale")
                prefs.edit().clear()
                    .putInt("length", 7).putLong("lastDay", today).putLong("restDay", today - 3)
                    .putLong("pendingDay", today).putInt("pendingLength", 7).putString("pendingCovered", "")
                    .putLong("pendingFinishedAt", System.currentTimeMillis()).putString("pendingCanvas", "BUNDLED/starter")
                    .commit()
                app.resetStreakForTest()
                scenario = ActivityScenario.launch(MainActivity::class.java)
                waitForTag(CELEBRATION_TAG)
                compose.mainClock.advanceTimeBy(4_000)
                scan()
                // A milestone's text button is Share (#104): the streak screen is reached from the gallery's chip.
                compose.onNodeWithTag(CELEBRATION_CONTINUE_TAG).performClick()
                waitForTag(STREAK_TAG)
                compose.onNodeWithTag(STREAK_TAG).performClick()
                waitForTag(STREAK_SCREEN_TAG)
                scan()
                scenario.close()
            }
        } finally {
            A11yTestSupport.shell("settings put system font_scale 1.0")
            prefs.edit().clear().commit()
            app.resetStreakForTest(celebrations = false)
        }
    }

    /** #102: the keep-it-alive nudge and its broken variant with the chip's "0 ›", at 1x and 2x font. */
    @Test
    fun streakNudge() {
        val prefs = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toEpochDay()
        try {
            for (scale in listOf("1.0", "2.0")) {
                A11yTestSupport.shell("settings put system font_scale $scale")
                for (lastDay in listOf(today - 1, today - 3)) {
                    // Alive through yesterday (the day off used), then broken two days ago with a repair open.
                    prefs.edit().clear().putInt("length", 6).putLong("lastDay", lastDay).putLong("restDay", lastDay - 2).commit()
                    app.resetStreakForTest(celebrations = false)
                    scenario = ActivityScenario.launch(MainActivity::class.java)
                    waitForTag(STREAK_NUDGE_TAG)
                    scan()
                    scenario.close()
                }
            }
        } finally {
            A11yTestSupport.shell("settings put system font_scale 1.0")
            prefs.edit().clear().commit()
            app.resetStreakForTest(celebrations = false)
        }
    }

    @Test
    fun settingsWithPlayGamesAndItsFailedLine() {
        // A fake Play Games (#49): the row shows, and its achievements screen can't open, so the failure line shows.
        val app = context.applicationContext as GemmoryApp
        app.resetAchievementsForTest(FakeGames()) { it.name.lowercase() }
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitForTag("tile-bundled-starter")
            compose.onNodeWithContentDescription("Settings").performClick()
            waitForTag(ACHIEVEMENTS_TAG)
            scan()
            compose.onNodeWithTag(ACHIEVEMENTS_TAG).performClick()
            waitForText(context.getString(R.string.achievements_failed))
            scan()
        } finally {
            app.resetAchievementsForTest()
        }
    }

    @Test
    fun paywallAndSettingsAsVipWithARestoreResult() {
        val app = context.applicationContext as GemmoryApp
        val fake = BillingBackends.fake
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitForTag("tile-bundled-starter")
            compose.onNodeWithContentDescription("Settings").performClick()
            waitForTag("settings-membership")
            compose.onNodeWithTag("settings-membership").performClick()
            waitForTag("plan-yearly")
            scan()
            compose.onNodeWithTag("paywall-start").performClick()
            waitForTag("settings-manage-subscription")
            compose.onNodeWithTag("settings-restore").performClick()
            waitForText("Purchases restored: VIP (yearly)")
            scan()
        } finally {
            fake.reset()
            runBlocking { app.billing.refresh() }
            app.billing.purchaseHandled()
            app.billing.notice.value?.let(app.billing::noticeShown)
        }
    }

    @Test
    fun photoTileAndRenameSheet() {
        runBlocking { (context.applicationContext as GemmoryApp).store.create(photo, A11yTestSupport.pattern(12, 16, 4)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        waitForTag("tile-photo-a11y-scan")
        scan()
        compose.onNodeWithTag("tile-photo-a11y-scan").performCustomAccessibilityActionWithLabel("Rename")
        waitForTag("sheet-title")
        scan()
    }

    @Test
    fun photoFlowCropSizeAndPreview() {
        val file = File(context.cacheDir, "a11y-photo.jpg")
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(
            0f, 0f, 900f, 1200f,
            Paint().apply { shader = LinearGradient(0f, 0f, 900f, 1200f, 0xFF3366AA.toInt(), 0xFFEECC88.toInt(), Shader.TileMode.CLAMP) },
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("tile-bundled-starter")
        scenario.onActivity { ViewModelProvider(it)[PhotoFlowViewModel::class.java].onPhotoPicked(Uri.fromFile(file)) }
        waitForText("Crop")
        scan()
        compose.onNodeWithText("Next").performClick()
        waitForText("Choose a size")
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Checking for faces…")).fetchSemanticsNodes().isEmpty() }
        scan()
        compose.onNodeWithText("Next").performClick()
        waitForTag("preview-primary")
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Setting the gems…")).fetchSemanticsNodes().isEmpty() }
        scan()
        // #29: the Colors row with More chosen (its play mark and the video primary for a free player).
        compose.onNodeWithTag("colors-more").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Gem preview")).fetchSemanticsNodes().isNotEmpty() }
        scan()
    }

    @Test
    fun canvasCompletionAndPlayAgain() {
        runBlocking { (context.applicationContext as GemmoryApp).store.create(photo, A11yTestSupport.pattern(20, 30, 3)) }
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        com.brewingbytes.gemmory.play.CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("My Photos").performClick()
        waitForTag("tile-photo-a11y-scan")
        compose.onNodeWithTag("tile-photo-a11y-scan").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        scan()
        // Zoomed in, with Find showing for a color that's nearly done.
        val s = session()!!
        instrumentation.runOnMainSync {
            repeat(185) { s.placeNext() }
            s.viewport.set(CanvasZoom.TAP_ZOOM_DP, 10f, 15f)
        }
        waitForTag("find")
        scan()
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        waitForTag("completion-fresh")
        // The picture loads in real time; the lights-down sequence starts only then.
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        scan()
        compose.onNodeWithTag("completion-done").performClick()
        runCatching { waitForText("My Photos") }.onFailure {
            A11yTestSupport.shell("screencap -p /data/local/tmp/a11y-done.png")
            throw it
        }
        compose.onNodeWithText("My Photos").performClick()
        // #89: the finished canvas sits in My Photos' Finished section.
        compose.openFinishedTile(photo)
        waitForTag("completion-still")
        scan()
        compose.onNodeWithText("Play again").performClick()
        waitForText("Start this picture over?")
        scan()
    }
}

/**
 * The sheets whose states can't be reached without ads or an encode, checked on their own, in an edge-to-edge
 * activity like the app's (otherwise the navigation bar covers the bottom of the sheet).
 */
@RunWith(AndroidJUnit4::class)
class SheetAccessibilityChecksTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var rewarded by mutableStateOf(RewardedUi(RewardTarget.Picture("starter"), RewardPhase.Ready))
    private var encode by mutableStateOf<EncodeState>(EncodeState.Running(0, 450))

    @Before
    fun setUp() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.enableAccessibilityChecks(scannerValidator())
    }

    /** After the sheet has settled to its new content's height. */
    private fun scan() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).tryPerformAccessibilityChecks()
    }

    @Test
    fun rewardedSheetStates() {
        compose.setContent { GemmoryTheme { RewardedSheet(rewarded, {}, {}, {}, {}, {}) } }
        // The sheet slides in on its own window; let it land before the first check.
        android.os.SystemClock.sleep(1_000)
        for (target in listOf(RewardTarget.Picture("starter"), RewardTarget.PhotoCanvas)) {
            // Ready first: the sheet lands before Loading's endless progress line (the test clock is driven by hand).
            for (phase in listOf(
                RewardPhase.Ready,
                RewardPhase.Loading,
                RewardPhase.NotAvailable(Unavailable.GRACE_OFFER),
                RewardPhase.NotAvailable(Unavailable.NO_VIDEO),
                RewardPhase.NotAvailable(Unavailable.OFFLINE),
                RewardPhase.Unlocked,
            )) {
                rewarded = RewardedUi(target, phase, closedEarly = phase == RewardPhase.Ready)
                runCatching { scan() }.onFailure {
                    A11yTestSupport.shell("screencap -p /data/local/tmp/a11y-sheet.png")
                    A11yTestSupport.shell("uiautomator dump /data/local/tmp/a11y-sheet.xml")
                    throw AssertionError("$target $phase", it)
                }
            }
        }
    }

    @Test
    fun shareSheetStates() {
        val thumb = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF557799.toInt()) }
        val image = thumb.asImageBitmap()
        compose.setContent { GemmoryTheme { ShareSheet(encode, image, image, 0.75f, {}, {}, {}, {}) } }
        android.os.SystemClock.sleep(1_000)
        for (state in listOf(
            EncodeState.Running(200, 450),
            EncodeState.Picture(File("before-after.png")),
            EncodeState.Failed,
            EncodeState.DiskFull,
        )) {
            encode = state
            runCatching { scan() }.onFailure { throw AssertionError("$state", it) }
        }
    }
}
