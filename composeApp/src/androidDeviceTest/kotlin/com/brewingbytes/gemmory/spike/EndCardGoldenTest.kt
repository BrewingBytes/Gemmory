package com.brewingbytes.gemmory.spike

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.brewingbytes.gemmory.timelapse.ShareLayout
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * One time-lapse end card (T59 Stage 0): the common [EndCardSpike]'s band and card layer against androidApp's
 * `FrameArt` layers in `goldens/renderer/`. Band: the geometry bar. Card (Play badge masked: Android-only): SSIM ≥
 * 0.98 over the text and over the shadow, the bar for text and blur. The figures go to the eng review.
 */
@OptIn(ExperimentalTextApi::class)
@RunWith(AndroidJUnit4::class)
class EndCardGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun endCardMatchesTheGoldens() = runBlocking<Unit> {
        val pattern = (GridCodec.decode(Res.readBytes("files/pictures/deco-peacock.gmrg")) as Decoded.Ok).value.pattern
        suspend fun font(name: String) = File(context.cacheDir, name).apply { writeBytes(Res.readBytes("font/$name")) }
        // As androidApp's AppFonts.LATIN in the frame: Bricolage at 'wght' 600, Figtree at its default instance (300).
        val display = FontFamily(Font(font("bricolage_grotesque.ttf"), FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))))
        val body = FontFamily(Font(font("figtree.ttf"), FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))))
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val spike = EndCardSpike(WIDTH, HEIGHT, measurer, display, body)
        val out = File(context.getExternalFilesDir(null), "goldens-actual").apply { mkdirs() }
        val failures = mutableListOf<String>()

        val band = spike.band(pattern).asAndroidBitmap()
        band.compress(Bitmap.CompressFormat.PNG, 100, File(out, "endcard-band.png").outputStream())
        val bandDiff = GoldenMath.diff(pixels(golden("endcard-band.png")), pixels(band))
        report("endcard-band differing=${GoldenMath.pct(bandDiff.differingShare)} maxDiff=${bandDiff.maxDiff} geometry=${pass(bandDiff.geometryPasses)}")
        if (!bandDiff.geometryPasses) failures += "band"

        val layout = ShareLayout.of(WIDTH, HEIGHT, pattern.width, pattern.height, branded = true)
        val card = layout.card!!
        val icon = Res.readBytes("files/share/app_icon.png").let { BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap() }
        val actual = spike.card(card, icon, "Gemmory", "Your photos in gems").asAndroidBitmap()
        actual.compress(Bitmap.CompressFormat.PNG, 100, File(out, "endcard-card.png").outputStream())
        val goldenCard = golden("endcard-card.png")
        val w = goldenCard.width
        val h = goldenCard.height
        assertTrue(actual.width == w && actual.height == h, "card ${actual.width}x${actual.height} vs ${w}x$h")
        val top = spike.cardTop(card)
        fun box(r: com.brewingbytes.gemmory.timelapse.BoardRect, pad: Int = 0) = intArrayOf(r.left - pad, r.top - top - pad, r.right + pad, r.bottom - top + pad)
        val a = pixels(goldenCard)
        val b = pixels(actual)
        val badge = GoldenMath.mask(w, h, listOf(box(card.badge, 4)))
        val text = GoldenMath.mask(
            w, h,
            listOf(
                intArrayOf(0, card.nameBaseline - top - spike.px(104), w, card.nameBaseline - top + spike.px(30)),
                intArrayOf(0, card.taglineBaseline - top - spike.px(44), w, card.taglineBaseline - top + spike.px(14)),
            ),
        )
        // The shadow: around the picture (the picture itself is the canvas, drawn over it by the compositor).
        val p = card.picture
        val shadow = GoldenMath.mask(w, h, listOf(box(p, spike.px(EndCardSpike.CARD_SHADOW_BLUR) * 2)))
        for (i in shadow.indices) if (badge[i] || text[i]) shadow[i] = false
        val textSsim = GoldenMath.ssim(a, b, w, text)
        val shadowSsim = GoldenMath.ssim(a, b, w, shadow)
        val layerDiff = GoldenMath.diff(a, b, BooleanArray(w * h) { badge[it] || text[it] })
        report(
            "endcard-card textSsim=${GoldenMath.fmt(textSsim)} shadowSsim=${GoldenMath.fmt(shadowSsim)} " +
                "nonText differing=${GoldenMath.pct(layerDiff.differingShare)} maxDiff=${layerDiff.maxDiff}",
        )
        if (textSsim < 0.98) failures += "card text"
        if (shadowSsim < 0.98) failures += "card shadow"
        assertTrue(failures.isEmpty(), "over the golden bar: $failures")
    }

    private fun golden(name: String): Bitmap {
        val bytes = checkNotNull(javaClass.classLoader!!.getResourceAsStream("renderer/$name")) { "missing golden $name" }.use { it.readBytes() }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun pass(ok: Boolean) = if (ok) "pass" else "FAIL"

    private fun report(line: String) {
        android.util.Log.i("GemmoryGolden", line)
        println("GemmoryGolden $line")
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}
