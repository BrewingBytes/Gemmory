package com.brewingbytes.gemmory.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.billing.BillingBackends
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.photo.PhotoFlowViewModel
import com.brewingbytes.gemmory.policy.Products
import com.brewingbytes.gemmory.ui.GemRenderer
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Play Store screenshots (#8, for the store listing): phone 1080×1920, because Play rejects a long side more than twice
 * the short one, or tablet 2560×1440 with `-e storeDisplay tablet`; a clean demo-mode status bar. Saved to
 * /data/local/tmp/store-[t-]name.png. Fake billing prices.
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** `-e storeDisplay tablet`: a 16:9 landscape tablet (2560x1440 at 320dpi, 1280x720dp) for the tablet listing. */
    private val tablet = InstrumentationRegistry.getArguments().getString("storeDisplay") == "tablet"

    /** No real AdMob: an interstitial after Done would cover the screens being captured. */
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

    @Before
    fun setUp() {
        // A new streak day's celebration (#101) would sit between Done and the gallery; this class tests what follows.
        app.resetStreakForTest(celebrations = false)
        assumeDeviceChecks()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        BillingBackends.fake.reset()
        runBlocking { app.billing.refresh() }
        // Store shots never show "Watch to unlock" (founder, #32): every picture opens, even on a Free account.
        BundledPictures.ALL.forEach { app.monetization.unlocks.unlock(it.id) }
        // No Today's picture card (#34): a pick left on the device from earlier today would show one, so the
        // shots would change from run to run.
        context.getSharedPreferences(TodaysPictures.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        app.resetTodaysForTest(enabled = false)
        if (tablet) {
            A11yTestSupport.shell("wm size 2560x1440")
            A11yTestSupport.shell("wm density 320")
        } else {
            A11yTestSupport.shell("wm size 1080x1920")
        }
        // A fixed day, no remote catalog, no real ads and a demo-mode status bar (T48's R1 pixel diff).
        PixelPins.pin(app)
        app.resetMonetizationForTest(NoAds)
    }

    @After
    fun tearDown() {
        app.resetStreakForTest()
        if (::scenario.isInitialized) scenario.close()
        PixelPins.unpin(app)
        A11yTestSupport.shell("wm size reset")
        A11yTestSupport.shell("wm density reset")
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PictureUnlocks.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
    }

    private fun shot(name: String) {
        SystemClock.sleep(1_500)
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("locked-label"), useUnmergedTree = true).assertCountEquals(0)
        A11yTestSupport.shell("screencap -p /data/local/tmp/store-${if (tablet) "t-" else ""}$name.png")
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    @Test
    fun capture() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-bundled-starter").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val s = session()!!
        val colors = s.pattern.palette.size
        // About half the colors done, the way a player fills a picture.
        instrumentation.runOnMainSync {
            for (slot in 0 until colors / 2) {
                s.select(slot)
                while (s.remaining(slot) > 0) s.placeNext()
            }
            s.select(colors / 2)
            repeat(s.remaining(colors / 2) / 2) { s.placeNext() }
        }
        shot("2-canvas")
        instrumentation.runOnMainSync { s.viewport.set(24f, s.pattern.width * 0.55f, s.pattern.height * 0.45f) }
        shot("3-canvas-zoom")

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        shot("1-gallery")

        compose.onNodeWithTag("tile-bundled-starter").performClick()
        compose.waitUntil(10_000) { session()?.viewport?.isLaidOut == true }
        val again = session()!!
        instrumentation.runOnMainSync { while (!again.isFinished) again.placeNext() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("completion-fresh")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        shot("4-completion")

        compose.onNodeWithTag("completion-done").performClick()
        // #89: the finished starter has moved to the Finished section, out of view.
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("gallery-grid")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-membership").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("plan-yearly")).fetchSemanticsNodes().isNotEmpty() }
        shot("5-paywall")
    }

    /** A photo pushed to /data/local/tmp/store-photos (read through the shell, which can see it). */
    private fun photo(name: String): File {
        val out = File(context.cacheDir, "store-in/$name.jpg").apply { parentFile?.mkdirs() }
        A11yTestSupport.automation.executeShellCommand("cat /data/local/tmp/store-photos/$name.jpg").use { fd ->
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        check(out.length() > 0) { "push $name.jpg to /data/local/tmp/store-photos first" }
        return out
    }

    private fun waitText(text: String, timeout: Long = 30_000) =
        compose.waitUntil(text, timeout) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String, timeout: Long = 60_000) =
        compose.waitUntil("$text gone", timeout) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

    /** The real photo flow: crop (square), Big, Preview, Start. Returns the open canvas's session. */
    private fun makeCanvas(name: String, title: String, previewShot: String? = null): PlaySession {
        val file = photo(name)
        scenario.onActivity { ViewModelProvider(it)[PhotoFlowViewModel::class.java].onPhotoPicked(Uri.fromFile(file)) }
        waitText("Crop")
        scenario.onActivity { ViewModelProvider(it)[PhotoFlowViewModel::class.java].onRatio(AspectRatio.SQUARE) }
        compose.onNodeWithText("Next").performClick()
        waitText("Choose a size")
        waitGone("Checking for faces…")
        compose.onNodeWithText("Big").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("preview-primary")).fetchSemanticsNodes().isNotEmpty() }
        waitGone("Setting the gems…")
        previewShot?.let(::shot)
        compose.onNodeWithTag("preview-primary").performClick()
        compose.waitUntil(20_000) { session()?.let { it.viewport.isLaidOut && it.placedCount == 0 } == true }
        val ref = session()!!.ref
        runBlocking { app.store.rename(ref, title) }
        // The open canvas keeps the title it opened with: back out and reopen it.
        leaveCanvas()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tileTag(ref)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tileTag(ref), useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) { session()?.let { it.ref == ref && it.viewport.isLaidOut } == true }
        return session()!!
    }

    /** Places [fraction] of the gems color by color, the way a player fills a picture. */
    private fun fill(s: PlaySession, fraction: Float) {
        val target = (s.pattern.cells.size * fraction).toInt()
        instrumentation.runOnMainSync {
            for (slot in s.pattern.palette.indices) {
                if (s.placedCount >= target) break
                s.select(slot)
                while (s.remaining(slot) > 0 && s.placedCount < target) s.placeNext()
            }
        }
    }

    /** The real renderer's art for [s] as it stands, saved for the feature graphic and the before/after frames. */
    private fun render(s: PlaySession, name: String, cellPx: Int = 16) {
        var placed = BooleanArray(0)
        // The session is main-thread only; the renderer itself isn't.
        instrumentation.runOnMainSync { placed = BooleanArray(s.pattern.cells.size) { s.isPlaced(it) } }
        for ((suffix, bitmap) in listOf("" to GemRenderer.render(s.pattern, cellPx) { placed[it] }, "-full" to GemRenderer.render(s.pattern, cellPx))) {
            // Through the shell: the app's own files go away when the test run uninstalls it.
            val (out, input) = A11yTestSupport.automation.executeShellCommandRw("dd of=/data/local/tmp/store-r-$name$suffix.png")
            ParcelFileDescriptor.AutoCloseOutputStream(input).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
        }
    }

    private fun leaveCanvas() {
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("My Photos")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun finish(s: PlaySession, shotName: String? = null) {
        instrumentation.runOnMainSync { while (!s.isFinished) s.placeNext() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("completion-fresh")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Finished picture")).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(6_000)
        shotName?.let(::shot)
        compose.onNodeWithTag("completion-done").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("My Photos")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Six photo canvases through the real photo flow (VIP on the fake backend, so the daily allowance doesn't stop it). */
    @Test
    fun photoCanvases() {
        BillingBackends.fake.own(Products.VIP_YEARLY)
        runBlocking { app.billing.refresh() }
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }

            // Created oldest first, so the gallery (newest first) opens on the puppy and the finished couple.
            val cat = makeCanvas("cat", "Marmalade")
            fill(cat, 0.85f)
            instrumentation.runOnMainSync { cat.viewport.set(20f, cat.pattern.width * 0.64f, cat.pattern.height * 0.40f) }
            shot("p-zoom")
            render(cat, "cat")
            finish(cat, "p-completion")

            val sunset = makeCanvas("sunset", "Sail away")
            fill(sunset, 0.6f)
            render(sunset, "sunset")
            leaveCanvas()

            val cottage = makeCanvas("cottage", "The cottage")
            fill(cottage, 0.7f)
            render(cottage, "cottage")
            leaveCanvas()

            val sunflowers = makeCanvas("sunflowers", "Sunflowers")
            fill(sunflowers, 0.85f)
            render(sunflowers, "sunflowers")
            leaveCanvas()

            val couple = makeCanvas("couple", "Golden hour")
            render(couple, "couple")
            finish(couple)

            val puppy = makeCanvas("puppy", "Biscuit", previewShot = "p-preview")
            fill(puppy, 0.62f)
            shot("p-canvas")
            render(puppy, "puppy")
            leaveCanvas()

            compose.onNodeWithText("My Photos").performClick()
            shot("p-gallery")
        } finally {
            BillingBackends.fake.reset()
            runBlocking { app.billing.refresh() }
        }
    }
}
