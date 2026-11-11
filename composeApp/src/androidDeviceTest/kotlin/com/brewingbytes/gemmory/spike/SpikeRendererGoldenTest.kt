package com.brewingbytes.gemmory.spike

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * The renderer golden bar, Android side (T59 Stage 0, R3-1): the spike renderer against today's `CanvasRenderer`,
 * captured by `:androidApp`'s `RendererGoldenCapture` into `goldens/renderer/`. Both draw into software bitmaps.
 *
 * Geometry (number boxes masked): ≤ 0.5% of pixels differ by more than 8/255 in a channel, none by more than 32/255.
 * Text: SSIM ≥ 0.98 over the number boxes (numbers move from `Paint.drawText` to `TextMeasurer`). Every case's
 * figures are printed (logcat tag `GemmoryGolden`) for the eng review, and the spike's renders are saved to the test
 * app's external files (`goldens-actual/`).
 */
@OptIn(ExperimentalTextApi::class)
@RunWith(AndroidJUnit4::class)
class SpikeRendererGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun spikeMatchesTheGoldens() = runBlocking<Unit> {
        val cases = GoldenRender.cases(resource("renderer/cases.txt").decodeToString())
        assertTrue(cases.isNotEmpty(), "no goldens in goldens/renderer/")
        val pattern = (GridCodec.decode(Res.readBytes("files/pictures/deco-peacock.gmrg")) as Decoded.Ok).value.pattern
        val font = File(context.cacheDir, "mono.ttf").apply { writeBytes(Res.readBytes("font/atkinson_hyperlegible_mono.ttf")) }
        // Today's shipped canvas draws its numbers at the font's default weight, 200, with a synthetic bold: its
        // Typeface.create(font, 700) doesn't set a variable font's weight. The goldens are that look, so the test
        // draws the same to prove the rest of the pipeline matches. The app (and Stage 1a) draws the design's 700
        // (founder, 2026-11-06), and the goldens are recaptured at 700 then.
        val mono = FontFamily(Font(font, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))))
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(GoldenRender.DENSITY), LayoutDirection.Ltr)
        val out = File(context.getExternalFilesDir(null), "goldens-actual").apply { mkdirs() }
        val failures = mutableListOf<String>()
        for (case in cases) {
            val name = case.name
            val png = resource("renderer/$name.png")
            val golden = BitmapFactory.decodeByteArray(png, 0, png.size).copy(Bitmap.Config.ARGB_8888, false)
            val (image, numberBoxes) = GoldenRender.render(pattern, measurer, mono, case)
            val actual = image.asAndroidBitmap()
            File(out, "$name.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = compare(golden, actual, numberBoxes)
            val figures = "$name differing=${GoldenMath.pct(result.diff.differingShare)} maxDiff=${result.diff.maxDiff} textSsim=${GoldenMath.fmt(result.textSsim)} " +
                "geometry=${if (result.geometryPasses) "pass" else "FAIL"} text=${if (result.textPasses) "pass" else "FAIL"}"
            android.util.Log.i("GemmoryGolden", figures)
            println("GemmoryGolden $figures")
            if (!result.geometryPasses || !result.textPasses) failures += figures
        }
        // The design's weight (700, founder 2026-11-06), saved for the iOS check's cross-platform figures
        // (GoldenIosTest reads them from composeApp/build/android-w700/ once pulled; README → iOS spike).
        val mono700 = FontFamily(Font(font, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))))
        for (case in cases.filter { it.zoomDp >= 16f }) {
            val w700 = GoldenRender.render(pattern, measurer, mono700, case).first.asAndroidBitmap()
            File(out, "w700-${case.name}.png").outputStream().use { w700.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(failures.isEmpty(), "over the golden bar:\n" + failures.joinToString("\n"))
    }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing golden $path" }.use { it.readBytes() }

    private class Result(val diff: GoldenMath.Diff, val textSsim: Double) {
        val geometryPasses get() = diff.geometryPasses
        val textPasses get() = textSsim >= 0.98
    }

    private fun compare(golden: Bitmap, actual: Bitmap, boxes: List<IntArray>): Result {
        val w = golden.width
        val h = golden.height
        require(actual.width == w && actual.height == h) { "size ${actual.width}x${actual.height} vs ${w}x$h" }
        val a = IntArray(w * h).also { golden.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(w * h).also { actual.getPixels(it, 0, w, 0, 0, w, h) }
        val masked = GoldenMath.mask(w, h, boxes)
        return Result(GoldenMath.diff(a, b, masked), if (boxes.isEmpty()) 1.0 else GoldenMath.ssim(a, b, w, masked))
    }
}
