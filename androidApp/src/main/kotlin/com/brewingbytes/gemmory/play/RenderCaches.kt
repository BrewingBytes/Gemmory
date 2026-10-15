package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasZoom
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

/**
 * How the selected color's remaining cells stand out (design system, Play canvas → Selected color, #19). [key] is
 * what the settings store.
 */
enum class HighlightStyle(val key: String) {
    /** Selected cells in the stronger tint (the default). */
    TINT("tint"),

    /** The D17 two-tone ring from 8dp; the stronger tint below 8dp, where no ring is drawn. */
    OUTLINE("outline"),

    /** Selected cells keep the pale tint; every other unplaced cell is dimmed toward felt-deep. */
    DIM("dim"),
    ;

    companion object {
        val DEFAULT = TINT

        fun fromKey(key: String?): HighlightStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** What a cell's face looks like: a placed gem, the pale tint, the selected color's stronger tint, or a dimmed tint. */
enum class CellLook { GEM, TINT, STRONG, DIMMED }

/**
 * Canvas cell colors (design system, Canvas cell colors): a placed gem is the exact grid color; an unplaced cell is
 * that color mixed 78% toward bone with its number in on-bone; the stronger tint of a selected color is mixed only
 * 40% toward bone, and a dimmed cell (Dim the rest) is the pale tint mixed 35% toward felt-deep, each with its number
 * in on-bone or bone, whichever has more contrast. Seams are each face slightly darker, so the grid reads without
 * dark gaps.
 */
class CellColors(pattern: Pattern) {
    val gem = IntArray(pattern.palette.size) { pattern.palette[it].argb or OPAQUE }
    val gemSeam = IntArray(gem.size) { shade(gem[it], 0.8f) }
    val tint = IntArray(gem.size) { mix(gem[it], BONE, UNPLACED_TINT) }
    val tintSeam = IntArray(gem.size) { shade(tint[it], 0.92f) }
    val selectedTint = IntArray(gem.size) { mix(gem[it], BONE, SELECTED_TINT) }
    val selectedSeam = IntArray(gem.size) { shade(selectedTint[it], 0.92f) }
    val selectedNumber = IntArray(gem.size) { readableOn(selectedTint[it]) }
    val dimmed = IntArray(gem.size) { mix(tint[it], FELT_DEEP, DIM) }
    val dimmedSeam = IntArray(gem.size) { shade(dimmed[it], 0.92f) }
    val dimmedNumber = IntArray(gem.size) { readableOn(dimmed[it]) }

    fun face(slot: Int, look: CellLook): Int = when (look) {
        CellLook.GEM -> gem[slot]
        CellLook.TINT -> tint[slot]
        CellLook.STRONG -> selectedTint[slot]
        CellLook.DIMMED -> dimmed[slot]
    }

    fun seam(slot: Int, look: CellLook): Int = when (look) {
        CellLook.GEM -> gemSeam[slot]
        CellLook.TINT -> tintSeam[slot]
        CellLook.STRONG -> selectedSeam[slot]
        CellLook.DIMMED -> dimmedSeam[slot]
    }

    fun number(slot: Int, look: CellLook): Int = when (look) {
        CellLook.STRONG -> selectedNumber[slot]
        CellLook.DIMMED -> dimmedNumber[slot]
        else -> ON_BONE
    }

    companion object {
        const val OPAQUE = 0xFF shl 24
        const val BONE = 0xFFE9E4D8.toInt()
        const val ON_BONE = 0xFF2B322D.toInt()
        const val UNPLACED_TINT = 0.78f
        const val SELECTED_TINT = 0.40f
        const val FELT_DEEP = 0xFF3E4741.toInt()
        const val DIM = 0.35f

        /**
         * The look of a cell of [kind] under [style]. [ringed]: the Outline ring is drawn (sprites from 8dp); the
         * bake below 8dp has no ring, so Outline shows the stronger tint there.
         */
        fun look(kind: SpriteKind, style: HighlightStyle, ringed: Boolean): CellLook = when (kind) {
            SpriteKind.PLACED -> CellLook.GEM
            SpriteKind.SELECTED -> when (style) {
                HighlightStyle.TINT -> CellLook.STRONG
                HighlightStyle.OUTLINE -> if (ringed) CellLook.TINT else CellLook.STRONG
                HighlightStyle.DIM -> CellLook.TINT
            }
            SpriteKind.UNPLACED -> if (style == HighlightStyle.DIM) CellLook.DIMMED else CellLook.TINT
        }

        /** Whether a [kind] sprite at [spec] under [style] gets the D17 ring: Outline, the selected color, from 8dp. */
        fun ringed(kind: SpriteKind, style: HighlightStyle, spec: SpriteSpec): Boolean =
            kind == SpriteKind.SELECTED && style == HighlightStyle.OUTLINE && spec.raised

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

/**
 * Which role a cell has: placed, unplaced, or a remaining cell of the selected color. Its look also depends on the
 * [HighlightStyle] ([CellColors.look]).
 */
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
 * texture). Selection never invalidates a sprite: the selected look is its own [SpriteKind]. A style change does
 * ([clear]), since a style switch is rare and keeping every style's sprites would grow each atlas. Moving to a new bucket
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

    /** Drops every sprite (the screen's density or the highlight style changed). */
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
 * once per open; after that a placement writes one pixel, a selection change rewrites only the two colors'
 * unplaced cells, and a style change writes only the cells whose color it changes, never the whole canvas.
 */
class CanvasBake(private val pattern: Pattern, private val colors: CellColors, private val sink: PixelSink) {
    private val pixels = IntArray(pattern.cells.size)
    private var selected = -1
    private var style = HighlightStyle.DEFAULT
    private var bakedCount = -1

    /** Full bakes so far: one per open. */
    var fullBakes = 0
        private set

    /**
     * Brings the bake up to date with the game: the first call bakes everything; later calls apply only the
     * placements after the last one seen ([placedAt] in log order), a changed selection and a changed [style].
     */
    fun sync(
        placedCount: Int,
        placedAt: (Int) -> Int,
        isPlaced: (Int) -> Boolean,
        selectedSlot: Int,
        style: HighlightStyle = HighlightStyle.DEFAULT,
    ) {
        if (bakedCount < 0) {
            this.style = style
            bakeAll(isPlaced, selectedSlot)
            bakedCount = placedCount
            return
        }
        while (bakedCount < placedCount) {
            val cell = placedAt(bakedCount++)
            write(cell, colors.gem[pattern.cells[cell]])
        }
        if (style != this.style) restyle(style, selectedSlot, isPlaced)
        if (selectedSlot != selected) reselect(selectedSlot, isPlaced)
    }

    /** Every unplaced cell may change with the style; [write] skips the ones whose color stays (Tint ↔ Outline: none). */
    private fun restyle(style: HighlightStyle, selectedSlot: Int, isPlaced: (Int) -> Boolean) {
        this.style = style
        selected = selectedSlot
        for (cell in pixels.indices) if (!isPlaced(cell)) write(cell, colorOf(cell, false))
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
        val kind = when {
            placed -> SpriteKind.PLACED
            slot == selected -> SpriteKind.SELECTED
            else -> SpriteKind.UNPLACED
        }
        return colors.face(slot, CellColors.look(kind, style, ringed = false))
    }
}
