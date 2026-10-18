package com.brewingbytes.gemmory.converter

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Spike-phase converter tests (eng D19): determinism, palette cap and degenerate inputs only.
 * Exact-output goldens are frozen at spike GO, not before.
 */
class ConverterTest {

    /** A busy, many-colored image: hue gradients plus seeded noise, so k-means has real work to do. */
    private fun noisyImage(w: Int, h: Int, seed: Int = 7): IntArray {
        var state = seed
        return IntArray(w * h) { i ->
            state = state * 1103515245 + 12345
            val noise = (state ushr 16) and 0x3F
            val x = i % w
            val y = i / w
            val r = (x * 255 / w + noise) and 0xFF
            val g = (y * 255 / h + noise * 2) and 0xFF
            val b = ((x + y) * 255 / (w + h) + noise * 3) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun assertWellFormed(p: Pattern, spec: GridSpec) {
        assertEquals(spec.width, p.width)
        assertEquals(spec.height, p.height)
        assertEquals(spec.cellCount, p.cells.size)
        assertTrue(p.palette.isNotEmpty())
        assertTrue(p.cells.all { it in p.palette.indices }, "every cell points at a palette slot")
        val counts = p.countsPerSlot()
        assertTrue(counts.all { it > 0 }, "no unused palette slots")
        assertEquals(counts.sortedDescending(), counts.toList(), "slots numbered most common first")
        p.palette.forEachIndexed { i, c -> assertEquals(i + 1, c.number) }
        assertTrue(p.palette.all { (it.argb ushr 24) == 0xFF }, "palette colors are opaque")
    }

    @Test
    fun sameInputAndSeedGiveTheSameGrid() {
        val img = noisyImage(300, 400)
        val spec = GridSpec.of(CanvasSize.MEDIUM, AspectRatio.PORTRAIT)
        val first = convert(img, 300, 400, spec, seed = 42)
        val second = convert(img.copyOf(), 300, 400, spec, seed = 42)
        assertEquals(first, second)
    }

    @Test
    fun everyPickerSpecIsDeterministic() {
        val img = noisyImage(160, 160, seed = 3)
        for (spec in GridSpec.PICKER) {
            assertEquals(convert(img, 160, 160, spec, 9), convert(img, 160, 160, spec, 9), spec.id)
        }
    }

    @Test
    fun seedIsActuallyUsed() {
        val img = noisyImage(200, 200)
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.SQUARE)
        val outputs = (1L..5L).map { convert(img, 200, 200, spec, it) }.toSet()
        assertTrue(outputs.size > 1, "different seeds should be able to give different k-means inits")
    }

    @Test
    fun paletteNeverExceedsTheCap() {
        val img = noisyImage(256, 256)
        for (spec in GridSpec.PICKER + GridSpec.STARTER) {
            val p = convert(img, 256, 256, spec, seed = 1)
            assertTrue(p.palette.size <= spec.paletteCap, "${spec.id}: ${p.palette.size} > ${spec.paletteCap}")
            assertWellFormed(p, spec)
        }
    }

    @Test
    fun busyImageUsesMostOfTheCap() {
        val spec = GridSpec.of(CanvasSize.BIG, AspectRatio.SQUARE)
        val p = convert(noisyImage(244, 244), 244, 244, spec, seed = 1)
        assertTrue(p.palette.size >= spec.paletteCap - 4, "only ${p.palette.size} of ${spec.paletteCap} colors")
    }

    @Test
    fun capHoldsWithEveryTuningKnobAtItsPlainSetting() {
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.LANDSCAPE)
        val plain = ConverterOptions(saturation = 1.0, minRegionCells = 2, lightnessWeight = 1.0, sharpen = 0.0)
        val p = convert(noisyImage(128, 96), 128, 96, spec, 5, plain)
        assertTrue(p.palette.size <= spec.paletteCap)
        assertWellFormed(p, spec)
    }

    @Test
    fun singleColorImageGivesOneColor() {
        val color = 0xFF3366CC.toInt()
        val spec = GridSpec.of(CanvasSize.MEDIUM, AspectRatio.SQUARE)
        val p = convert(IntArray(100 * 100) { color }, 100, 100, spec, seed = 0, ConverterOptions(saturation = 1.0))
        assertEquals(1, p.palette.size)
        assertTrue(p.cells.all { it == 0 })
        assertColorClose(color, p.palette[0].argb)
    }

    @Test
    fun twoColorImageKeepsBothColors() {
        val img = IntArray(64 * 64) { if ((it % 64) < 32) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.SQUARE)
        val p = convert(img, 64, 64, spec, seed = 0)
        assertEquals(2, p.palette.size)
        assertWellFormed(p, spec)
    }

    @Test
    fun onePixelImageFillsTheGrid() {
        val spec = GridSpec.of(CanvasSize.BIG, AspectRatio.LANDSCAPE)
        val p = convert(intArrayOf(0xFF808080.toInt()), 1, 1, spec, seed = 0)
        assertEquals(1, p.palette.size)
        assertWellFormed(p, spec)
    }

    @Test
    fun imageSmallerThanTheGridIsUpsampled() {
        val img = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(),
        )
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.PORTRAIT)
        val p = convert(img, 3, 2, spec, 0, ConverterOptions(sharpen = 0.0))
        assertEquals(6, p.palette.size)
        assertWellFormed(p, spec)
    }

    @Test
    fun extremeAspectInputStillFillsTheGrid() {
        val spec = GridSpec.of(CanvasSize.MEDIUM, AspectRatio.SQUARE)
        assertWellFormed(convert(noisyImage(1000, 2), 1000, 2, spec, seed = 0), spec)
        assertWellFormed(convert(noisyImage(1, 700), 1, 700, spec, seed = 0), spec)
    }

    @Test
    fun fullyTransparentImageReadsAsWhite() {
        val spec = GridSpec.STARTER
        val p = convert(IntArray(40 * 40) { 0x00123456 }, 40, 40, spec, seed = 0)
        assertEquals(1, p.palette.size)
        assertColorClose(0xFFFFFFFF.toInt(), p.palette[0].argb)
    }

    @Test
    fun halfTransparentBlackIsCompositedOverWhite() {
        val spec = GridSpec.STARTER
        val p = convert(IntArray(40 * 40) { 0x80000000.toInt() }, 40, 40, spec, 0, ConverterOptions(saturation = 1.0))
        val gray = p.palette.single().argb and 0xFF
        assertTrue(gray in 150..210, "expected a mid-light gray, got $gray")
    }

    @Test
    fun alphaOnlyDifferencesAreIgnoredWhenOpaque() {
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.SQUARE)
        val img = noisyImage(64, 64)
        assertEquals(convert(img, 64, 64, spec, 1), convert(img.map { it or (0xFF shl 24) }.toIntArray(), 64, 64, spec, 1))
    }

    @Test
    fun sharpeningNeverPushesLightnessOutOfRange() {
        val img = IntArray(64 * 64) { if ((it % 64) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.SQUARE)
        val p = convert(img, 64, 64, spec, 0, ConverterOptions(sharpen = 3.0, lightnessWeight = 4.0))
        assertTrue(p.palette.all { it.lab.l in 0.0..100.0 })
        assertWellFormed(p, spec)
    }

    @Test
    fun invalidOptionsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ConverterOptions(lightnessWeight = 0.0) }
        assertFailsWith<IllegalArgumentException> { ConverterOptions(sharpen = -1.0) }
        assertFailsWith<IllegalArgumentException> { ConverterOptions(minRegionCells = 0) }
    }

    @Test
    fun invalidInputIsRejected() {
        val spec = GridSpec.STARTER
        assertFailsWith<IllegalArgumentException> { convert(IntArray(0), 0, 0, spec, 0) }
        assertFailsWith<IllegalArgumentException> { convert(IntArray(10), 4, 4, spec, 0) }
        assertFailsWith<IllegalArgumentException> { convert(IntArray(4), -2, -2, spec, 0) }
    }

    @Test
    fun isolatedSpecksAreMergedIntoTheirSurroundings() {
        val w = 56
        val img = IntArray(w * w) { 0xFF2040A0.toInt() }
        img[10 * w + 10] = 0xFFFFFFFF.toInt()
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.SQUARE)
        // Sharpening off: it would add halo shades around the speck, which this test is not about.
        assertEquals(1, convert(img, w, w, spec, 0, ConverterOptions(minRegionCells = 2, sharpen = 0.0)).palette.size)
        assertEquals(2, convert(img, w, w, spec, 0, ConverterOptions(sharpen = 0.0)).palette.size, "merging is off by default")
    }

    /** Two shades a hair apart (ΔL 1) and a far one: More merges the twins, the plain converter keeps them. */
    @Test
    fun minColorDistanceMergesNearTwins() {
        val twinA = 0xFF808080.toInt()
        val twinB = 0xFF838383.toInt()
        val far = 0xFF2050C0.toInt()
        val img = IntArray(30 * 30) { i -> when ((i % 30) / 10) { 0 -> twinA; 1 -> twinB; else -> far } }
        val spec = GridSpec("twins", 30, 30, 8)
        val plain = convert(img, 30, 30, spec, seed = 1, ConverterOptions(sharpen = 0.0))
        val merged = convert(img, 30, 30, spec, seed = 1, ConverterOptions(sharpen = 0.0, minColorDistance = 5.0))
        assertEquals(3, plain.palette.size)
        assertEquals(2, merged.palette.size)
        assertWellFormed(merged, spec)
        // The twins become one color between them, weighted by their cells (equal here).
        val gray = merged.palette.first { it.argb != far }
        val expected = ColorSpace.argbToLab(twinA).l / 2 + ColorSpace.argbToLab(twinB).l / 2
        assertTrue(abs(gray.lab.l - expected) < 0.01, "merged L ${gray.lab.l}, expected $expected")
        assertEquals(merged, convert(img, 30, 30, spec, seed = 1, ConverterOptions(sharpen = 0.0, minColorDistance = 5.0)))
    }

    @Test
    fun moreStaysWithinItsCap() {
        val img = noisyImage(256, 256)
        for (size in CanvasSize.entries) {
            val spec = GridSpec.of(size, AspectRatio.SQUARE, ColorChoice.MORE)
            val p = convert(img, 256, 256, spec, seed = 1, ConverterOptions.of(ColorChoice.MORE))
            assertTrue(p.palette.size <= size.moreCap, "${spec.id}: ${p.palette.size} > ${size.moreCap}")
            assertWellFormed(p, spec)
        }
    }

    private fun assertColorClose(expected: Int, actual: Int, tolerance: Int = 2) {
        for (shift in intArrayOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(abs(e - a) <= tolerance, "channel ${shift / 8}: expected $e, got $a")
        }
    }
}

class GridSpecTest {
    @Test
    fun pickerMatchesTheDesignTable() {
        val expected = mapOf(
            "quick-1:1" to Triple(56, 56, 16), "quick-3:4" to Triple(48, 64, 16), "quick-4:3" to Triple(64, 48, 16),
            "medium-1:1" to Triple(90, 90, 24), "medium-3:4" to Triple(78, 104, 24), "medium-4:3" to Triple(104, 78, 24),
            "big-1:1" to Triple(122, 122, 36), "big-3:4" to Triple(106, 142, 36), "big-4:3" to Triple(142, 106, 36),
        )
        assertEquals(expected, GridSpec.PICKER.associate { it.id to Triple(it.width, it.height, it.paletteCap) })
    }

    @Test
    fun colorChoicesPerSize() {
        val caps = CanvasSize.entries.associateWith { size -> ColorChoice.entries.map(size::cap) }
        assertEquals(
            mapOf(CanvasSize.QUICK to listOf(12, 16, 24), CanvasSize.MEDIUM to listOf(16, 24, 40), CanvasSize.BIG to listOf(24, 36, 60)),
            caps,
        )
        for (size in CanvasSize.entries) {
            assertEquals(size.paletteCap, size.cap(ColorChoice.STANDARD), "Standard is the cap from before #29")
            assertTrue(size.moreCap <= 255, "GridCodec stores one u8 slot per cell")
            for (ratio in AspectRatio.entries) {
                val more = GridSpec.of(size, ratio, ColorChoice.MORE)
                assertEquals(GridSpec.of(size, ratio).copy(paletteCap = size.moreCap), more, "same grid and id, only the cap moves")
            }
        }
    }

    @Test
    fun onlyMoreMergesNearTwinColors() {
        assertEquals(ConverterOptions(), ConverterOptions.of(ColorChoice.FEWER))
        assertEquals(ConverterOptions(), ConverterOptions.of(ColorChoice.STANDARD))
        assertEquals(ConverterOptions.MORE_MIN_COLOR_DISTANCE, ConverterOptions.of(ColorChoice.MORE).minColorDistance)
    }

    @Test
    fun starterIsBuildTimeOnly() {
        assertEquals(Triple(24, 32, 8), GridSpec.STARTER.let { Triple(it.width, it.height, it.paletteCap) })
        assertFalse(GridSpec.STARTER in GridSpec.PICKER)
        assertTrue(GridSpec.PICKER.none { it.width == 24 && it.height == 32 })
    }
}

class ColorSpaceTest {
    @Test
    fun srgbRoundTripsThroughLab() {
        var state = 1
        repeat(2000) {
            state = state * 1103515245 + 12345
            val argb = state or (0xFF shl 24)
            val back = ColorSpace.labToArgb(ColorSpace.argbToLab(argb))
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue(abs(((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
    }

    @Test
    fun whiteAndBlackHaveTheExpectedLightness() {
        assertTrue(abs(ColorSpace.argbToLab(0xFFFFFFFF.toInt()).l - 100.0) < 0.01)
        assertTrue(abs(ColorSpace.argbToLab(0xFF000000.toInt()).l) < 0.01)
        assertNotEquals(ColorSpace.argbToLab(0xFFFF0000.toInt()), ColorSpace.argbToLab(0xFF00FF00.toInt()))
    }
}
