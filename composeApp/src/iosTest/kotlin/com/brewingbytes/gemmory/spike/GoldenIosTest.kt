package com.brewingbytes.gemmory.spike

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.render.GoldenMath
import com.brewingbytes.gemmory.render.GoldenRender
import com.brewingbytes.gemmory.render.RenderAssets
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.timelapse.ShareLayout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The renderer golden bar's cross-platform check (T59 Stage 0, the full T62 set; Stage 2 makes it a gate): the shared
 * renderers drawn on iOS against the Android goldens in `goldens/renderer/`, SSIM ≥ 0.97 per image (glyph rasterization differs
 * between platforms). Asserted where it holds today (cells without numbers, the end card's band and card); the
 * numbered zooms are printed for the eng review, with the geometry figures.
 */
@OptIn(ExperimentalTextApi::class)
class GoldenIosTest {
    private val root = NSProcessInfo.processInfo.environment["GEMMORY_ROOT"] as? String
        ?: error("GEMMORY_ROOT isn't set (composeApp/build.gradle.kts sets it for the simulator tests)")

    @Test
    fun spikeMatchesTheAndroidGoldens() {
        val pattern = (GridCodec.decode(file("goldens/renderer/pictures/deco-peacock.gmrg")) as Decoded.Ok).value.pattern
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val failures = mutableListOf<String>()
        val assets = object : RenderAssets {
            override val numbers = FontFamily(font("atkinson_hyperlegible_mono.ttf", FontWeight.Bold, 700))
        }
        val patterns = HashMap<String, Pattern>()
        for (case in GoldenRender.cases(file("goldens/renderer/cases.txt").decodeToString())) {
            val picture = patterns.getOrPut(case.picture) {
                (GridCodec.decode(file("goldens/renderer/pictures/${case.picture}.gmrg")) as Decoded.Ok).value.pattern
            }
            val (image, boxes) = when (case) {
                is GoldenRender.CanvasCase -> GoldenRender.render(picture, measurer, assets, case)
                is GoldenRender.GemsCase -> GoldenRender.gems(picture, case) to emptyList()
            }
            save(image, "${case.name}.png")
            val a = pixels(png("goldens/renderer/${case.name}.png"))
            val b = pixels(image)
            val ssim = GoldenMath.ssim(a, b, image.width)
            val diff = GoldenMath.diff(a, b, GoldenMath.mask(image.width, image.height, boxes))
            report("ios ${case.name} ssim=${GoldenMath.fmt(ssim)} geometry differing=${GoldenMath.pct(diff.differingShare)} maxDiff=${diff.maxDiff}")
            // Numbers (from 16dp) rasterize differently per platform (T59 figures: SSIM 0.90-0.96 at 700): reported for
            // the eng review, gated from Stage 2. Below 16dp, and for the gem tiles, the bar holds today.
            val numbered = case is GoldenRender.CanvasCase && case.zoomDp >= 16f
            if (!numbered && ssim < CROSS_PLATFORM) failures += case.name
        }

        val display = FontFamily(font("bricolage_grotesque.ttf", FontWeight.SemiBold, 600))
        val body = FontFamily(font("figtree.ttf", FontWeight.Light, 300))
        val spike = EndCardSpike(1080, 1920, measurer, display, body)
        val band = spike.band(pattern)
        val bandSsim = GoldenMath.ssim(pixels(png("goldens/renderer/endcard-band.png")), pixels(band), band.width)
        val bandDiff = GoldenMath.diff(pixels(png("goldens/renderer/endcard-band.png")), pixels(band))
        report("ios endcard-band ssim=${GoldenMath.fmt(bandSsim)} differing=${GoldenMath.pct(bandDiff.differingShare)} maxDiff=${bandDiff.maxDiff}")
        if (bandSsim < CROSS_PLATFORM) failures += "endcard-band"
        val layout = ShareLayout.of(1080, 1920, pattern.width, pattern.height, branded = true)
        val card = layout.card!!
        val icon = png("composeApp/src/commonMain/composeResources/files/share/app_icon.png")
        val actual = spike.card(card, icon, "Gemmory", "Your photos in gems")
        val golden = pixels(png("goldens/renderer/endcard-card.png"))
        val top = spike.cardTop(card)
        val badge = GoldenMath.mask(actual.width, actual.height, listOf(intArrayOf(card.badge.left - 4, card.badge.top - top - 4, card.badge.right + 4, card.badge.bottom - top + 4)))
        val mine = pixels(actual)
        // The Play badge is Android's alone (CEO D13): compare with it painted out of both.
        for (i in badge.indices) if (badge[i]) {
            golden[i] = 0
            mine[i] = 0
        }
        val cardSsim = GoldenMath.ssim(golden, mine, actual.width)
        report("ios endcard-card ssim=${GoldenMath.fmt(cardSsim)}")
        if (cardSsim < CROSS_PLATFORM) failures += "endcard-card"
        assertTrue(failures.isEmpty(), "under the cross-platform bar: $failures")
    }

    private fun font(name: String, weight: FontWeight, wght: Int) = Font(
        identity = name,
        data = file("composeApp/src/commonMain/composeResources/font/$name"),
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(wght)),
    )

    private fun file(path: String): ByteArray = fileOrNull(path) ?: error("can't read $root/$path")

    @OptIn(ExperimentalForeignApi::class)
    private fun fileOrNull(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile("$root/$path") ?: return null
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }

    /** The iOS render, for a look: `composeApp/build/goldens-ios/`. */
    @OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    private fun save(image: ImageBitmap, name: String) {
        val dir = "$root/composeApp/build/goldens-ios"
        platform.Foundation.NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val bytes = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
        bytes.usePinned { platform.Foundation.NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            .writeToFile("$dir/$name", atomically = true)
    }

    private fun png(path: String): ImageBitmap = Image.makeFromEncoded(file(path)).toComposeImageBitmap()

    private fun pixels(image: ImageBitmap) = IntArray(image.width * image.height).also { image.readPixels(it) }

    private fun report(line: String) = println("GemmoryGolden $line")

    private companion object {
        const val CROSS_PLATFORM = 0.97
    }
}
