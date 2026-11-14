package com.brewingbytes.gemmory.render

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.ui.AppRenderAssets
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The play canvas on the GPU (T62, eng D13, R10-6): the shared `render.CanvasRenderer` drawn the way `PlayScreen`
 * draws it, inside a Compose `Canvas` on the window's hardware-accelerated surface, and read back through the window
 * (`captureToImage`, PixelCopy). Each case is the goldens' own setup ([GoldenRender.draw]: 720×1280 px at density 2)
 * for one picture at two zoom buckets, empty, half and full, against the software goldens in `goldens/renderer/` at
 * SSIM ≥ 0.97. Runs on the Test Lab phones with the encoder smoke (`testlab.yml`, D24); figures go to logcat
 * (`GemmoryHardware`) and the captures to the app's external files (`goldens-hardware/`), which Test Lab pulls.
 */
@RunWith(AndroidJUnit4::class)
class HardwareCanvasGoldenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theCanvasOnTheGpuMatchesTheGoldens() {
        val cases = GoldenRender.cases(resource("renderer/cases.txt").decodeToString())
            .filterIsInstance<GoldenRender.CanvasCase>()
            .filter { it.name in CASES }
        assertEquals(CASES.toSet(), cases.map { it.name }.toSet(), "goldens/renderer/cases.txt lacks a hardware case")
        val pattern = (GridCodec.decode(resource("renderer/pictures/$PICTURE.gmrg")) as Decoded.Ok).value.pattern
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(GoldenRender.DENSITY), LayoutDirection.Ltr)
        var case by mutableStateOf(cases.first())
        compose.setContent {
            Canvas(
                Modifier
                    .testTag(TAG)
                    // Exactly the goldens' pixels, whatever this phone's density.
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(GoldenRender.WIDTH, GoldenRender.HEIGHT))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
            ) {
                val drawn = case
                drawIntoCanvas { GoldenRender.draw(it, pattern, measurer, AppRenderAssets, drawn) }
            }
        }
        val out = File(context.getExternalFilesDir(null), "goldens-hardware").apply { mkdirs() }
        val failures = mutableListOf<String>()
        for (c in cases) {
            case = c
            compose.waitForIdle()
            val actual = compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
            File(out, "${c.name}.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val w = GoldenRender.WIDTH
            val h = GoldenRender.HEIGHT
            assertEquals(w to h, actual.width to actual.height, "${c.name} captured at ${actual.width}x${actual.height}")
            val bytes = resource("renderer/${c.name}.png")
            val golden = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val a = IntArray(w * h).also { golden.getPixels(it, 0, w, 0, 0, w, h) }
            val b = IntArray(w * h).also { actual.getPixels(it, 0, w, 0, 0, w, h) }
            val ssim = GoldenMath.ssim(a, b, w)
            val diff = GoldenMath.diff(a, b)
            val figures = "${c.name} ssim=${GoldenMath.fmt(ssim)} differing=${GoldenMath.pct(diff.differingShare)} maxDiff=${diff.maxDiff}"
            Log.i("GemmoryHardware", "$figures ${if (ssim >= HARDWARE_SSIM) "pass" else "FAIL"}")
            if (ssim < HARDWARE_SSIM) failures += figures
        }
        assertTrue(failures.isEmpty(), "the GPU canvas is under the bar:\n" + failures.joinToString("\n"))
    }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing golden $path" }.use { it.readBytes() }

    private companion object {
        const val TAG = "hardware-canvas"
        const val PICTURE = "deco-peacock"

        /** Two zoom buckets (cells too small for numbers, and numbered), each on the empty, half and full saves. */
        val CASES = listOf(10, 28).flatMap { z -> listOf("$PICTURE-z$z-dim-empty", "$PICTURE-z$z-dim-half", "$PICTURE-z$z-full") }

        /** Hardware against software (eng D13): anti-aliasing and filtering differ a little across GPUs. */
        const val HARDWARE_SSIM = 0.97
    }
}
