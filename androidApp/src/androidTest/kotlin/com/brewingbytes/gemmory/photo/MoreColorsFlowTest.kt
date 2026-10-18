package com.brewingbytes.gemmory.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.PlaySession
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
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * #29: a Big photo canvas made with More colors. A free player gets "Watch a video for more colors" and Start does
 * nothing until a video unlocks More; then the canvas starts with more than Standard's 36 colors, plays, saves and
 * resumes with the palette it was made with.
 */
@RunWith(AndroidJUnit4::class)
class MoreColorsFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as GemmoryApp
    private val photo = File(context.cacheDir, "more-colors.jpg")
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        // Earlier tests may have used today's free canvas: one more, so Start can make a canvas whatever ran before.
        runBlocking { app.monetization.grantReward() }
        // Every hue and lightness with noise, so Big at More keeps well over 36 distinct colors.
        val random = Random(29)
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1200) {
            for (x in 0 until 900) {
                val hsv = floatArrayOf(x * 360f / 900, 0.35f + 0.6f * ((y / 150) % 2), 0.25f + 0.7f * y / 1200 + random.nextFloat() * 0.05f)
                bitmap.setPixel(x, y, Color.HSVToColor(hsv.also { it[2] = it[2].coerceAtMost(1f) }))
            }
        }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        photo.delete()
    }

    private fun viewModel(): PhotoFlowViewModel {
        lateinit var vm: PhotoFlowViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[PhotoFlowViewModel::class.java] }
        return vm
    }

    private fun waitForText(text: String, timeout: Long = 20_000) =
        compose.waitUntil("text $text", timeout) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun moreColorsNeedsAVideoThenPlaysSavesAndResumes() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil("gallery", 10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        val vm = viewModel()
        scenario.onActivity { vm.onPhotoPicked(Uri.fromFile(photo)) }
        waitForText("Crop")
        compose.onNodeWithText("Next").performClick()
        waitForText("Choose a size")
        compose.waitUntil("face check", 20_000) { compose.onAllNodes(hasText("Checking for faces…")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Big").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.waitUntil("preview", 10_000) { compose.onAllNodes(hasTestTag("colors-more")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("colors-more").performClick()
        compose.waitUntil("More converted", 60_000) { vm.state.value.current is Conversion.Ready }
        val pattern = (vm.state.value.current as Conversion.Ready).pattern
        assertEquals(ColorChoice.MORE, vm.state.value.choice)
        assertTrue(pattern.palette.size in (CanvasSize.BIG.paletteCap + 1)..CanvasSize.BIG.moreCap, "${pattern.palette.size} colors")
        waitForText("Watch a video for more colors")
        waitForText("More colors: watch a video, or get VIP.")
        compose.onNodeWithTag("colors-more").assertExists()
        compose.waitUntil { compose.onAllNodes(hasContentDescription("More, 60 colors, needs a video or VIP")).fetchSemanticsNodes().isNotEmpty() }
        waitForText("Big · 15,052 gems · ${pattern.palette.size} colors")

        // Without the video, Start makes nothing (the button isn't offered, and the rule holds in the view model).
        scenario.onActivity { vm.start() }
        compose.waitForIdle()
        assertTrue(runBlocking { app.store.list(CanvasKind.PHOTO) }.isEmpty(), "More needs a video or VIP")

        // The video's reward (RewardedEvent.MoreColorsUnlocked) unlocks it for this photo.
        scenario.onActivity { vm.onMoreColorsUnlocked() }
        waitForText("Start")
        compose.waitUntil { compose.onAllNodes(hasContentDescription("More, 60 colors")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil("face check and conversion", 30_000) { vm.state.value.canStart }
        compose.onNodeWithTag("preview-primary").performClick()
        compose.waitUntil("canvas written", 20_000) { runBlocking { app.store.list(CanvasKind.PHOTO) }.isNotEmpty() }
        val ref = CanvasRef(CanvasKind.PHOTO, runBlocking { app.store.list(CanvasKind.PHOTO) }.single())
        // The canvas opened on top; leave it so the store is ours alone.
        compose.waitUntil("canvas open", 20_000) { compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isNotEmpty() }
        scenario.close()

        // Plays and saves with its own palette, then resumes as made.
        val placed = play(ref, gems = 25)
        val resumed = open(ref)
        assertEquals(pattern.palette, resumed.grid.pattern.palette, "the canvas keeps the palette it was made with")
        lateinit var session: PlaySession
        instrumentation.runOnMainSync { session = app.store.start(resumed) }
        assertTrue(placed.all { session.isPlaced(it) }, "every placed gem is still there")
        assertEquals(placed.size, (0 until session.cellCount).count { session.isPlaced(it) })
        instrumentation.runOnMainSync { session.close() }
    }

    private fun open(ref: CanvasRef): CanvasStore.Opened.Ready = runBlocking { assertIs<CanvasStore.Opened.Ready>(app.store.open(ref)) }

    /** Places [gems] of the first color and closes the session (the closing write); returns the cells placed. */
    private fun play(ref: CanvasRef, gems: Int): List<Int> {
        val opened = open(ref)
        val cells = opened.grid.pattern.cells
        lateinit var session: PlaySession
        instrumentation.runOnMainSync { session = app.store.start(opened) }
        val slot = session.selectedSlot
        val targets = cells.indices.filter { cells[it] == slot && !session.isPlaced(it) }.take(gems)
        instrumentation.runOnMainSync {
            for (cell in targets) assertIs<PlaceResult.Placed>(session.place(cell))
            session.close()
        }
        open(ref) // joins the closing write
        return targets
    }
}
