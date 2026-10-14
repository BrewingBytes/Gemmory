package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RenderCachesTest {
    // 4×2, slots: 0 1 0 2 / 1 0 2 0.
    private val pattern = Pattern(
        width = 4,
        height = 2,
        palette = listOf(0xFF102030, 0xFFF4EEE6, 0xFFE0B89A).mapIndexed { i, argb ->
            PaletteColor(i + 1, argb.toInt(), Lab(i * 10.0, 0.0, 0.0))
        },
        cells = intArrayOf(0, 1, 0, 2, 1, 0, 2, 0),
    )
    private val colors = CellColors(pattern)

    private class FakeSink : PixelSink {
        var fullWrites = 0
        val writes = mutableListOf<Pair<Int, Int>>()
        val pixels = IntArray(8)

        override fun setAll(pixels: IntArray) {
            fullWrites++
            pixels.copyInto(this.pixels)
        }

        override fun set(cell: Int, argb: Int) {
            writes += cell to argb
            pixels[cell] = argb
        }
    }

    @Test
    fun bucketsNeverScaleASpriteDownByMoreThanOneStep() {
        var cellPx = 8f
        while (cellPx <= 170f) {
            val px = SpriteSpec.bucketPx(cellPx)
            assertTrue(px >= cellPx, "bucket $px under cell $cellPx")
            assertTrue(px <= cellPx * 1.16f + 1f, "bucket $px too big for cell $cellPx")
            cellPx += 0.37f
        }
        // Small pinch wobbles stay in one bucket.
        assertEquals(SpriteSpec.bucketPx(60.1f), SpriteSpec.bucketPx(61.5f))
    }

    @Test
    fun specFeaturesFollowTheZoomTable() {
        val d = 2.625f
        assertEquals(SpriteSpec(SpriteSpec.bucketPx(8 * d), raised = true, numbers = false, facets = false), SpriteSpec.of(8f, 8 * d))
        assertTrue(SpriteSpec.of(16f, 16 * d).numbers)
        assertFalse(SpriteSpec.of(19.9f, 19.9f * d).facets)
        assertTrue(SpriteSpec.of(20f, 20 * d).facets)
    }

    @Test
    fun spritesAreMadeOncePerColorKindAndBucket() {
        val cache = SpriteCache<Unit, Triple<SpriteSpec, Int, SpriteKind>>(3, { _, _ -> }) { _, spec, slot, kind -> Triple(spec, slot, kind) }
        val a = SpriteSpec.of(20f, 52.5f)
        val first = cache.get(a, 1, SpriteKind.PLACED)
        assertSame(first, cache.get(a, 1, SpriteKind.PLACED))
        cache.get(a, 1, SpriteKind.UNPLACED)
        cache.get(a, 1, SpriteKind.SELECTED)
        cache.get(a, 2, SpriteKind.PLACED)
        assertEquals(4, cache.made)
    }

    @Test
    fun aNewBucketRemakesAndTheLastBucketIsKept() {
        val cache = SpriteCache<Unit, Triple<SpriteSpec, Int, SpriteKind>>(3, { _, _ -> }) { _, spec, slot, kind -> Triple(spec, slot, kind) }
        val small = SpriteSpec.of(20f, 52.5f)
        val big = SpriteSpec.of(30f, 78.75f)
        val smallSprite = cache.get(small, 0, SpriteKind.PLACED)
        cache.get(big, 0, SpriteKind.PLACED)
        assertEquals(2, cache.made, "a new bucket makes its own sprite")
        assertSame(smallSprite, cache.get(small, 0, SpriteKind.PLACED), "wobbling back reuses the last bucket")
        assertEquals(2, cache.made)
        val third = SpriteSpec.of(45f, 118f)
        cache.get(third, 0, SpriteKind.PLACED) // keeps small (the last one), drops big
        cache.get(big, 0, SpriteKind.PLACED)
        assertEquals(4, cache.made, "only one earlier bucket is kept")
        assertEquals(4, cache.buckets)
        cache.clear()
        cache.get(big, 0, SpriteKind.PLACED)
        assertEquals(5, cache.made, "clear drops everything (density change)")
    }

    @Test
    fun anEvictedBucketsPayloadIsOfferedForReuse() {
        val offered = mutableListOf<String?>()
        var n = 0
        val cache = SpriteCache<String, Int>(1, { _, recycled -> offered += recycled; "atlas${n++}" }) { _, _, _, _ -> 0 }
        cache.get(SpriteSpec.of(20f, 50f), 0, SpriteKind.PLACED)
        cache.get(SpriteSpec.of(30f, 75f), 0, SpriteKind.PLACED)
        cache.get(SpriteSpec.of(40f, 100f), 0, SpriteKind.PLACED)
        assertEquals(listOf(null, null, "atlas0"), offered)
        assertEquals("atlas2", cache.payload)
    }

    @Test
    fun theSameSizeWithOtherFeaturesIsAnotherBucket() {
        val cache = SpriteCache<Unit, Triple<SpriteSpec, Int, SpriteKind>>(3, { _, _ -> }) { _, spec, slot, kind -> Triple(spec, slot, kind) }
        cache.get(SpriteSpec(42, raised = true, numbers = false, facets = false), 0, SpriteKind.UNPLACED)
        cache.get(SpriteSpec(42, raised = true, numbers = true, facets = false), 0, SpriteKind.UNPLACED)
        assertEquals(2, cache.made)
    }

    @Test
    fun theBakeIsWholeOnceThenOnePixelPerPlacement() {
        val sink = FakeSink()
        val bake = CanvasBake(pattern, colors, sink)
        val state = CanvasState.empty(pattern)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0)
        assertEquals(1, sink.fullWrites)
        assertEquals(colors.selectedTint[0], sink.pixels[0])
        assertEquals(colors.tint[1], sink.pixels[1])

        state.place(2, 0)
        state.place(5, 0)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0)
        assertEquals(1, sink.fullWrites, "a placement never rebakes the whole canvas")
        assertEquals(listOf(2 to colors.gem[0], 5 to colors.gem[0]), sink.writes)

        sink.writes.clear()
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0)
        assertTrue(sink.writes.isEmpty(), "nothing new, nothing written")
        assertEquals(1, bake.fullBakes)
    }

    @Test
    fun aSelectionChangeRewritesOnlyTheTwoColorsUnplacedCells() {
        val sink = FakeSink()
        val bake = CanvasBake(pattern, colors, sink)
        val state = CanvasState.empty(pattern)
        state.place(0, 0)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 2)
        // Slot 0's unplaced cells (2, 5, 7) lose the strong tint; slot 2's (3, 6) gain it. Placed cell 0 is untouched.
        assertEquals(setOf(2, 5, 7, 3, 6), sink.writes.map { it.first }.toSet())
        assertEquals(colors.tint[0], sink.pixels[2])
        assertEquals(colors.selectedTint[2], sink.pixels[3])
        assertEquals(colors.gem[0], sink.pixels[0])
        assertEquals(1, sink.fullWrites)
    }

    @Test
    fun selectedNumbersPickTheMoreReadableInk() {
        // A dark navy's 40% tint is still dark enough for bone; cream and skin tones take on-bone.
        assertEquals(CellColors.BONE, colors.selectedNumber[0])
        assertEquals(CellColors.ON_BONE, colors.selectedNumber[1])
        assertEquals(CellColors.ON_BONE, colors.selectedNumber[2])
        assertEquals(CellColors.ON_BONE, colors.number(0, SpriteKind.UNPLACED))
    }

    @Test
    fun tintsMixTowardBone() {
        assertEquals(0xFFE9E4D8.toInt(), CellColors.mix(0xFF000000.toInt(), CellColors.BONE, 1f))
        assertEquals(CellColors.mix(colors.gem[0], CellColors.BONE, 0.40f), colors.selectedTint[0])
        assertEquals(CellColors.mix(colors.gem[0], CellColors.BONE, 0.78f), colors.tint[0])
    }
}
