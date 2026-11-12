package com.brewingbytes.gemmory.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.compose.res.Res
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * The renderer golden bar, Android side, full set (iOS Stage 1a, T62): the shared [CanvasRenderer] and [GemRenderer]
 * against today's renderers, captured by `:androidApp`'s `RendererGoldenCapture` into `goldens/renderer/`. Both draw
 * into software bitmaps on the pinned emulator image (workflow.md).
 *
 * Geometry (number boxes masked): ≤ 0.5% of pixels differ by more than 8/255 in a channel, none by more than 32/255.
 * Text: SSIM ≥ 0.98 over the number boxes (numbers move from `Paint.drawText` to `TextMeasurer`). Every case's figures
 * are printed (logcat tag `GemmoryGolden`), and the shared renders are saved to the test app's external files
 * (`goldens-actual/`) for a look.
 */
@OptIn(ExperimentalTextApi::class)
@RunWith(AndroidJUnit4::class)
class SharedRendererGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sharedRenderersMatchTheGoldens() = runBlocking<Unit> {
        val cases = GoldenRender.cases(resource("renderer/cases.txt").decodeToString())
        assertTrue(cases.size > 80, "goldens/renderer/cases.txt holds ${cases.size} cases: recapture them (README → iOS)")
        val font = File(context.cacheDir, "mono.ttf").apply { writeBytes(Res.readBytes("font/atkinson_hyperlegible_mono.ttf")) }
        val assets = object : RenderAssets {
            override val numbers = FontFamily(Font(font, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))
        }
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(GoldenRender.DENSITY), LayoutDirection.Ltr)
        val out = File(context.getExternalFilesDir(null), "goldens-actual").apply { mkdirs() }
        val patterns = HashMap<String, Pattern>()
        val failures = mutableListOf<String>()
        for (case in cases) {
            val pattern = patterns.getOrPut(case.picture) {
                (GridCodec.decode(resource("renderer/pictures/${case.picture}.gmrg")) as Decoded.Ok).value.pattern
            }
            val (image, boxes) = when (case) {
                is GoldenRender.CanvasCase -> GoldenRender.render(pattern, measurer, assets, case)
                is GoldenRender.GemsCase -> GoldenRender.gems(pattern, case) to emptyList()
            }
            val result = compare(case.name, image, boxes, out)
            val figures = "${case.name} differing=${GoldenMath.pct(result.diff.differingShare)} maxDiff=${result.diff.maxDiff} " +
                "textSsim=${GoldenMath.fmt(result.textSsim)} geometry=${pass(result.geometryPasses)} text=${pass(result.textPasses)}"
            android.util.Log.i("GemmoryGolden", figures)
            println("GemmoryGolden $figures")
            if (!result.geometryPasses || !result.textPasses) failures += figures
        }
        assertTrue(failures.isEmpty(), "over the golden bar:\n" + failures.joinToString("\n"))
    }

    private fun pass(ok: Boolean) = if (ok) "pass" else "FAIL"

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing golden $path" }.use { it.readBytes() }

    private class Result(val diff: GoldenMath.Diff, val textSsim: Double) {
        val geometryPasses get() = diff.geometryPasses
        val textPasses get() = textSsim >= 0.98
    }

    private fun compare(name: String, image: ImageBitmap, boxes: List<IntArray>, out: File): Result {
        val actual = image.asAndroidBitmap()
        File(out, "$name.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val png = resource("renderer/$name.png")
        val golden = BitmapFactory.decodeByteArray(png, 0, png.size).copy(Bitmap.Config.ARGB_8888, false)
        val w = golden.width
        val h = golden.height
        require(actual.width == w && actual.height == h) { "$name: size ${actual.width}x${actual.height} vs ${w}x$h" }
        val a = IntArray(w * h).also { golden.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(w * h).also { actual.getPixels(it, 0, w, 0, 0, w, h) }
        val masked = GoldenMath.mask(w, h, boxes)
        return Result(GoldenMath.diff(a, b, masked), if (boxes.isEmpty()) 1.0 else GoldenMath.ssim(a, b, w, masked))
    }
}
