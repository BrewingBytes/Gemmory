package com.brewingbytes.gemmory.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.policy.ConsumeResult
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Eng E4a (#9): Start on Preview is single-flight and keyed by the draft, so a double tap makes one canvas and uses
 * one allowance.
 */
@RunWith(AndroidJUnit4::class)
class StartSingleFlightTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as GemmoryApp
    private val photo = File(context.cacheDir, "single-flight.jpg")
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        // Earlier tests may have used today's free canvas: one more, so Start is enabled whatever ran before.
        runBlocking { app.monetization.grantReward() }
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(
            0f, 0f, 900f, 1200f,
            Paint().apply { shader = LinearGradient(0f, 0f, 900f, 1200f, 0xFF3366AA.toInt(), 0xFFEECC88.toInt(), Shader.TileMode.CLAMP) },
        )
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
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

    @Test
    fun aDoubleTapOnStartMakesOneCanvasAndUsesOneAllowance() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil("gallery", 10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        val vm = viewModel()
        scenario.onActivity { vm.onPhotoPicked(Uri.fromFile(photo)) }
        compose.waitUntil("decoded", 20_000) { vm.state.value.source != null }
        scenario.onActivity { vm.onCropNext(Rect(0, 0, 900, 1200)) }
        compose.waitUntil("face check and conversion", 60_000) { vm.state.value.canStart }

        // Two taps in the same frame, before the first Start's coroutine has run.
        scenario.onActivity {
            vm.start()
            vm.start()
        }
        compose.waitUntil("canvas written", 20_000) { runBlocking { app.store.list(CanvasKind.PHOTO) }.isNotEmpty() }
        compose.waitForIdle()

        val canvases = runBlocking { app.store.list(CanvasKind.PHOTO) }
        assertEquals(1, canvases.size, "one canvas: $canvases")
        val repeat = runBlocking { app.monetization.consume(canvases.single()) }
        assertIs<ConsumeResult.Ok>(repeat)
        assertTrue(repeat.repeat, "the draft's allowance was spent once and is remembered")
    }
}
