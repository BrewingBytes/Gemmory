package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasZoom
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

/**
 * Canvas cell colors (design system, Canvas cell colors): a placed gem is the exact grid color; an unplaced cell is
 * that color mixed 78% toward bone with its number in on-bone; a remaining cell of the selected color is mixed only
 * 40% toward bone, its number in on-bone or bone, whichever has more contrast. Seams are each face slightly darker,
 * so the grid reads without dark gaps.
 */
class CellColors(pattern: Pattern) {
    val gem = IntArray(pattern.palette.size) { pattern.palette[it].argb or OPAQUE }
    val gemSeam = IntArray(gem.size) { shade(gem[it], 0.8f) }
    val tint = IntArray(gem.size) { mix(gem[it], BONE, UNPLACED_TINT) }
    val tintSeam = IntArray(gem.size) { shade(tint[it], 0.92f) }
    val selectedTint = IntArray(gem.size) { mix(gem[it], BONE, SELECTED_TINT) }
    val selectedSeam = IntArray(gem.size) { shade(selectedTint[it], 0.92f) }
    val selectedNumber = IntArray(gem.size) { readableOn(selectedTint[it]) }

    fun face(slot: Int, kind: SpriteKind): Int = when (kind) {
        SpriteKind.PLACED -> gem[slot]
        SpriteKind.UNPLACED -> tint[slot]
        SpriteKind.SELECTED -> selectedTint[slot]
    }

    fun seam(slot: Int, kind: SpriteKind): Int = when (kind) {
        SpriteKind.PLACED -> gemSeam[slot]
        SpriteKind.UNPLACED -> tintSeam[slot]
        SpriteKind.SELECTED -> selectedSeam[slot]
    }

    fun number(slot: Int, kind: SpriteKind): Int = if (kind == SpriteKind.SELECTED) selectedNumber[slot] else ON_BONE

    companion object {
        const val OPAQUE = 0xFF shl 24
        const val BONE = 0xFFE9E4D8.toInt()
        const val ON_BONE = 0xFF2B322D.toInt()
        const val UNPLACED_TINT = 0.78f
        const val SELECTED_TINT = 0.40f

        fun shade(argb: Int, factor: Float): Int = mix(argb, OPAQUE, 1f - factor)

        fun mix(from: Int, to: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = (from shr shift) and 0xFF
                val b = (to shr shift) and 0xFF
                return (a + (b - a) * amount + 0.5f).toInt()
            }
            return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        /** On-bone or bone, whichever has more contrast on [argb] (WCAG relative luminance). */
        fun readableOn(argb: Int): Int =
            if (contrast(ON_BONE, argb) >= contrast(BONE, argb)) ON_BONE else BONE

        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        private fun luminance(argb: Int): Double {
            fun linear(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
        }
    }
}

/** Which look of a color a sprite has: a placed gem, an unplaced tint, or the selected color's stronger tint. */
enum class SpriteKind { PLACED, UNPLACED, SELECTED }

/**
 * One zoom bucket: sprites are drawn [px] square and scaled down at most one bucket step to the on-screen cell,
 * with what that zoom shows (design system zoom table): raised edges from 8dp, numbers from 16dp, facets from 20dp.
 */
data class SpriteSpec(val px: Int, val raised: Boolean, val numbers: Boolean, val facets: Boolean) {
    companion object {
        /** Buckets are 2^(1/5) apart (~15%), so a sprite is never scaled down by more than that. */
        fun bucketPx(cellPx: Float): Int {
            if (cellPx <= 1f) return 1
            val step = ceil(log2(cellPx) * STEPS_PER_DOUBLING - 1e-4f)
            return ceil(2f.pow(step / STEPS_PER_DOUBLING) - 1e-3f).toInt().coerceAtLeast(ceil(cellPx).toInt())
        }

        fun of(zoomDp: Float, cellPx: Float) = SpriteSpec(
            px = bucketPx(cellPx),
            raised = zoomDp >= CanvasZoom.RAISED_MIN_DP,
            numbers = zoomDp >= CanvasZoom.PLACE_MIN_DP,
            facets = zoomDp >= CanvasZoom.FACET_MIN_DP,
        )

        private const val STEPS_PER_DOUBLING = 5f
    }
}

/**
 * One cached gem sprite per palette color per zoom bucket (eng finding 13), made on first use. Each bucket has a
 * payload [B] (on the device, the atlas bitmap its sprites are drawn into, so the GPU draws a whole frame from one
 * texture). Selection never invalidates a sprite: the selected look is its own [SpriteKind]. Moving to a new bucket
 * keeps the last one, so a pinch that wobbles across a bucket edge doesn't remake anything; the bucket before that
 * is dropped and its payload handed to the next new bucket for reuse.
 */
class SpriteCache<B : Any, T : Any>(
    private val slots: Int,
    private val newBucket: (spec: SpriteSpec, recycled: B?) -> B,
    private val make: (bucket: B, spec: SpriteSpec, slot: Int, kind: SpriteKind) -> T,
) {
    private inner class Bucket(val spec: SpriteSpec, val payload: B) {
        val sprites = arrayOfNulls<Any>(slots * KINDS)
    }

    private var current: Bucket? = null
    private var previous: Bucket? = null

    /** Sprites made so far, for tests. */
    var made = 0
        private set

    /** Buckets made so far, for tests. */
    var buckets = 0
        private set

    /** The current bucket's payload, after a [get] for its spec. */
    val payload: B? get() = current?.payload

    @Suppress("UNCHECKED_CAST")
    fun get(spec: SpriteSpec, slot: Int, kind: SpriteKind): T {
        val bucket = bucketFor(spec)
        val index = slot * KINDS + kind.ordinal
        bucket.sprites[index]?.let { return it as T }
        made++
        return make(bucket.payload, spec, slot, kind).also { bucket.sprites[index] = it }
    }

    /** Drops every sprite (the screen's density changed). */
    fun clear() {
        current = null
        previous = null
    }

    private fun bucketFor(spec: SpriteSpec): Bucket {
        current?.let { if (it.spec == spec) return it }
        val reused = previous?.takeIf { it.spec == spec }
        val evicted = if (reused == null) previous else null
        previous = current
        val bucket = reused ?: Bucket(spec, newBucket(spec, evicted?.payload)).also { buckets++ }
        current = bucket
        return bucket
    }

    companion object {
        val KINDS = SpriteKind.entries.size
    }
}

/** Where [CanvasBake] writes its pixels: a 1 px-per-cell bitmap on the device, a fake in tests. */
interface PixelSink {
    fun setAll(pixels: IntArray)

    fun set(cell: Int, argb: Int)
}

/**
 * The baked full canvas used below 8dp cells, where cells are flat (eng finding 13): one pixel per cell in the cell's
 * current face color, scaled up with nearest-neighbour sampling so every cell stays a crisp flat square. Baked whole
 * once per open; after that a placement writes one pixel and a selection change rewrites only the two colors'
 * unplaced cells, never the whole canvas.
 */
class CanvasBake(private val pattern: Pattern, private val colors: CellColors, private val sink: PixelSink) {
    private val pixels = IntArray(pattern.cells.size)
    private var selected = -1
    private var bakedCount = -1

    /** Full bakes so far: one per open. */
    var fullBakes = 0
        private set

    /**
     * Brings the bake up to date with the game: the first call bakes everything; later calls apply only the
     * placements after the last one seen ([placedAt] in log order) and a changed selection.
     */
    fun sync(placedCount: Int, placedAt: (Int) -> Int, isPlaced: (Int) -> Boolean, selectedSlot: Int) {
        if (bakedCount < 0) {
            bakeAll(isPlaced, selectedSlot)
            bakedCount = placedCount
            return
        }
        while (bakedCount < placedCount) {
            val cell = placedAt(bakedCount++)
            write(cell, colors.gem[pattern.cells[cell]])
        }
        if (selectedSlot != selected) reselect(selectedSlot, isPlaced)
    }

    private fun bakeAll(isPlaced: (Int) -> Boolean, selectedSlot: Int) {
        fullBakes++
        selected = selectedSlot
        for (cell in pixels.indices) pixels[cell] = colorOf(cell, isPlaced(cell))
        sink.setAll(pixels)
    }

    private fun reselect(slot: Int, isPlaced: (Int) -> Boolean) {
        val old = selected
        selected = slot
        val cells = pattern.cells
        for (cell in cells.indices) {
            val s = cells[cell]
            if ((s == old || s == slot) && !isPlaced(cell)) write(cell, colorOf(cell, false))
        }
    }

    private fun write(cell: Int, argb: Int) {
        if (pixels[cell] == argb) return
        pixels[cell] = argb
        sink.set(cell, argb)
    }

    private fun colorOf(cell: Int, placed: Boolean): Int {
        val slot = pattern.cells[cell]
        return when {
            placed -> colors.gem[slot]
            slot == selected -> colors.selectedTint[slot]
            else -> colors.tint[slot]
        }
    }
}
