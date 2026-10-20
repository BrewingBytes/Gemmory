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
        assertEquals(CellColors.ON_BONE, colors.number(0, CellLook.TINT))
    }

    @Test
    fun eachStyleGivesTheSelectedAndOtherCellsTheirLook() {
        fun looks(style: HighlightStyle, ringed: Boolean) = listOf(SpriteKind.PLACED, SpriteKind.SELECTED, SpriteKind.UNPLACED)
            .map { CellColors.look(it, style, ringed) }
        // Stronger tint: at every zoom.
        assertEquals(listOf(CellLook.GEM, CellLook.STRONG, CellLook.TINT), looks(HighlightStyle.TINT, ringed = false))
        assertEquals(listOf(CellLook.GEM, CellLook.STRONG, CellLook.TINT), looks(HighlightStyle.TINT, ringed = true))
        // Outline: the ring on a pale tint from 8dp, the stronger tint below it.
        assertEquals(listOf(CellLook.GEM, CellLook.TINT, CellLook.TINT), looks(HighlightStyle.OUTLINE, ringed = true))
        assertEquals(listOf(CellLook.GEM, CellLook.STRONG, CellLook.TINT), looks(HighlightStyle.OUTLINE, ringed = false))
        // Dim the rest: the selected color keeps the pale tint, every other unplaced cell dims.
        assertEquals(listOf(CellLook.GEM, CellLook.TINT, CellLook.DIMMED), looks(HighlightStyle.DIM, ringed = false))
        assertEquals(listOf(CellLook.GEM, CellLook.TINT, CellLook.DIMMED), looks(HighlightStyle.DIM, ringed = true))
    }

    @Test
    fun theRingIsDrawnOnlyFromEightDpOnTheSelectedColorUnderOutline() {
        val d = 2.625f
        val below = SpriteSpec.of(7.9f, 7.9f * d)
        val at8 = SpriteSpec.of(8f, 8 * d)
        val at24 = SpriteSpec.of(24f, 24 * d)
        assertFalse(CellColors.ringed(SpriteKind.SELECTED, HighlightStyle.OUTLINE, below))
        assertTrue(CellColors.ringed(SpriteKind.SELECTED, HighlightStyle.OUTLINE, at8))
        assertTrue(CellColors.ringed(SpriteKind.SELECTED, HighlightStyle.OUTLINE, at24))
        for (style in listOf(HighlightStyle.TINT, HighlightStyle.DIM)) {
            assertFalse(CellColors.ringed(SpriteKind.SELECTED, style, at24), "no ring under $style")
        }
        assertFalse(CellColors.ringed(SpriteKind.UNPLACED, HighlightStyle.OUTLINE, at24))
        assertFalse(CellColors.ringed(SpriteKind.PLACED, HighlightStyle.OUTLINE, at24))
    }

    @Test
    fun dimmedCellsMixThePaleTintTowardFeltDeepAndKeepReadableNumbers() {
        assertEquals(CellColors.mix(colors.tint[1], 0xFF3E4741.toInt(), 0.35f), colors.dimmed[1])
        for (slot in 0..2) {
            val face = colors.dimmed[slot]
            val ink = colors.number(slot, CellLook.DIMMED)
            assertEquals(CellColors.readableOn(face), ink)
            val other = if (ink == CellColors.ON_BONE) CellColors.BONE else CellColors.ON_BONE
            assertTrue(CellColors.contrast(ink, face) >= CellColors.contrast(other, face))
        }
        // Cream and skin tone stay well readable; the worst case anywhere (pale tints of near-black) is about 3.9:1.
        assertTrue(CellColors.contrast(colors.number(1, CellLook.DIMMED), colors.dimmed[1]) >= 5.5)
        assertTrue(CellColors.contrast(colors.number(2, CellLook.DIMMED), colors.dimmed[2]) >= 5.0)
        val black = CellColors(
            Pattern(1, 1, listOf(PaletteColor(1, 0xFF000000.toInt(), Lab(0.0, 0.0, 0.0))), intArrayOf(0)),
        )
        assertTrue(CellColors.contrast(black.number(0, CellLook.DIMMED), black.dimmed[0]) >= 3.9)
        // Dimmed cells stand apart from the selected pale tint even for cream.
        assertTrue(CellColors.contrast(colors.tint[1], colors.dimmed[1]) >= 1.7)
    }

    @Test
    fun aStyleSwitchRecolorsOnlyTheCellsItChanges() {
        val sink = FakeSink()
        val bake = CanvasBake(pattern, colors, sink)
        val state = CanvasState.empty(pattern)
        state.place(0, 0)
        state.place(3, 2)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0, HighlightStyle.TINT)

        // Tint → Outline: the bake (below 8dp) shows the stronger tint for both, so nothing is written.
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0, HighlightStyle.OUTLINE)
        assertTrue(sink.writes.isEmpty(), "Outline bakes like Tint below 8dp")

        // Outline → Dim: every unplaced cell changes (selected back to the pale tint, the rest dim); placed don't.
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0, HighlightStyle.DIM)
        assertEquals(setOf(1, 2, 4, 5, 6, 7), sink.writes.map { it.first }.toSet())
        assertEquals(colors.tint[0], sink.pixels[2])
        assertEquals(colors.dimmed[1], sink.pixels[1])
        assertEquals(colors.dimmed[2], sink.pixels[6])
        assertEquals(colors.gem[0], sink.pixels[0])
        assertEquals(colors.gem[2], sink.pixels[3])

        // Under Dim, a selection change still touches only the two colors' unplaced cells.
        sink.writes.clear()
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 1, HighlightStyle.DIM)
        assertEquals(setOf(2, 5, 7, 1, 4), sink.writes.map { it.first }.toSet())
        assertEquals(colors.dimmed[0], sink.pixels[2])
        assertEquals(colors.tint[1], sink.pixels[1])

        // A placement under Dim writes its one pixel.
        sink.writes.clear()
        state.place(1, 1)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 1, HighlightStyle.DIM)
        assertEquals(listOf(1 to colors.gem[1]), sink.writes)
        assertEquals(1, sink.fullWrites, "a style switch never rebakes the whole canvas")
        assertEquals(1, bake.fullBakes)
    }

    @Test
    fun theFirstBakeUsesTheCurrentStyle() {
        val sink = FakeSink()
        val bake = CanvasBake(pattern, colors, sink)
        val state = CanvasState.empty(pattern)
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0, HighlightStyle.DIM)
        assertEquals(colors.tint[0], sink.pixels[0])
        assertEquals(colors.dimmed[1], sink.pixels[1])
        assertTrue(sink.writes.isEmpty())
    }

    @Test
    fun tintsMixTowardBone() {
        assertEquals(0xFFE9E4D8.toInt(), CellColors.mix(0xFF000000.toInt(), CellColors.BONE, 1f))
        assertEquals(CellColors.mix(colors.gem[0], CellColors.BONE, 0.40f), colors.selectedTint[0])
        assertEquals(CellColors.mix(colors.gem[0], CellColors.BONE, 0.78f), colors.tint[0])
    }

    @Test
    fun aPlacedGemHasNoSeamAndUnplacedCellsKeepTheirs() {
        // T40 R1: the table cut's bevels carry a placed gem's edge, so its face runs to the cell edge in the exact
        // color; an unplaced cell keeps its 8% darker right and bottom seam in every look.
        for (slot in 0 until pattern.palette.size) {
            assertEquals(colors.gem[slot], colors.seam(slot, CellLook.GEM))
            assertEquals(CellColors.shade(colors.tint[slot], 0.92f), colors.seam(slot, CellLook.TINT))
            assertEquals(CellColors.shade(colors.selectedTint[slot], 0.92f), colors.seam(slot, CellLook.STRONG))
            assertEquals(CellColors.shade(colors.dimmed[slot], 0.92f), colors.seam(slot, CellLook.DIMMED))
        }
    }

    @Test
    fun theFitZoomBakeStaysFlatInTheExactColors() {
        // T40 R1: below 8dp a placed gem is a flat square in its exact grid color (no bevel shade baked in).
        val sink = FakeSink()
        val bake = CanvasBake(pattern, colors, sink)
        val state = CanvasState.empty(pattern)
        for (cell in pattern.cells.indices) state.place(cell, pattern.cells[cell])
        bake.sync(state.placedCount, state::placedAt, state::isPlaced, 0)
        for (cell in pattern.cells.indices) assertEquals(pattern.palette[pattern.cells[cell]].argb or CellColors.OPAQUE, sink.pixels[cell])
    }
}
