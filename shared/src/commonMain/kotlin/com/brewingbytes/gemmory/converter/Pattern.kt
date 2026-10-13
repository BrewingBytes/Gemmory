package com.brewingbytes.gemmory.converter

/** One numbered gem color. [number] is what the player sees (1-based, slot + 1). */
data class PaletteColor(val number: Int, val argb: Int, val lab: Lab)

/**
 * A converted canvas: [cells] holds one palette slot (0-based index into [palette]) per cell,
 * row-major, `width * height` long. Slots are numbered by cell count, most common first.
 */
class Pattern(
    val width: Int,
    val height: Int,
    val palette: List<PaletteColor>,
    val cells: IntArray,
) {
    init {
        require(cells.size == width * height) { "cells must be width*height" }
    }

    fun slotAt(x: Int, y: Int): Int = cells[y * width + x]

    fun countsPerSlot(): IntArray = IntArray(palette.size).also { counts -> cells.forEach { counts[it]++ } }

    override fun equals(other: Any?): Boolean =
        other is Pattern && width == other.width && height == other.height &&
            palette == other.palette && cells.contentEquals(other.cells)

    override fun hashCode(): Int = (31 * (31 * width + height) + palette.hashCode()) * 31 + cells.contentHashCode()
}
