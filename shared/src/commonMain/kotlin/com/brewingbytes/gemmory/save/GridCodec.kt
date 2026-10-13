package com.brewingbytes.gemmory.save

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern

/** A decoded grid plus its CRC32 trailer, which every save made on it carries (eng D11). */
class GridFile(val pattern: Pattern, val crc32: Int) {
    val binding: GridBinding get() = GridBinding(crc32, pattern.width * pattern.height)
}

/** What a save is checked against: the grid's CRC32 and its cell count. */
data class GridBinding(val crc32: Int, val cellCount: Int)

/**
 * Versioned grid file (eng D11, R-O4). Photo grids are written once when the canvas starts; bundled pictures ship
 * as frozen grid files and are never re-converted at runtime, so a converter change can't move a saved cell index.
 *
 * v1 body, big-endian: u16 width, u16 height, u16 palette size, then per color u8 r, u8 g, u8 b, f64 L, f64 a,
 * f64 b (raw IEEE bits, so the Lab values round-trip exactly), then one u8 palette slot per cell, row-major.
 * Palette numbers are not stored: color `number` is always slot + 1.
 */
object GridCodec {
    val MAGIC = "GMRG".encodeToByteArray()
    const val VERSION = 1
    const val MAX_PALETTE = 256

    /** Well above Big (~15k gems); bounds the allocation a corrupt header can ask for. */
    const val MAX_CELLS = 1 shl 20

    fun encode(pattern: Pattern): ByteArray {
        val palette = pattern.palette
        require(pattern.width in 1..0xFFFF && pattern.height in 1..0xFFFF) { "grid side out of range" }
        require(pattern.width.toLong() * pattern.height <= MAX_CELLS) { "grid too large" }
        require(palette.size in 1..MAX_PALETTE) { "palette size out of range: ${palette.size}" }
        val w = ByteWriter(6 + palette.size * 27 + pattern.cells.size)
        w.u16(pattern.width)
        w.u16(pattern.height)
        w.u16(palette.size)
        palette.forEachIndexed { slot, color ->
            require(color.number == slot + 1) { "color number must be slot + 1" }
            require(color.argb ushr 24 == 0xFF) { "palette colors must be opaque" }
            w.u8(color.argb ushr 16)
            w.u8(color.argb ushr 8)
            w.u8(color.argb)
            w.f64(color.lab.l)
            w.f64(color.lab.a)
            w.f64(color.lab.b)
        }
        for (slot in pattern.cells) {
            require(slot in palette.indices) { "cell slot out of range: $slot" }
            w.u8(slot)
        }
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<GridFile> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val pattern = try {
            readV1(ByteReader(opened.body))
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(GridFile(pattern, opened.crc32))
    }

    private fun readV1(r: ByteReader): Pattern? {
        val width = r.u16()
        val height = r.u16()
        val paletteSize = r.u16()
        if (width == 0 || height == 0 || width.toLong() * height > MAX_CELLS) return null
        if (paletteSize !in 1..MAX_PALETTE) return null
        val palette = List(paletteSize) { slot ->
            val argb = (0xFF shl 24) or (r.u8() shl 16) or (r.u8() shl 8) or r.u8()
            val lab = Lab(r.f64(), r.f64(), r.f64())
            if (!lab.l.isFinite() || !lab.a.isFinite() || !lab.b.isFinite()) return null
            PaletteColor(number = slot + 1, argb = argb, lab = lab)
        }
        if (r.remaining != width * height) return null
        val cells = IntArray(width * height) { r.u8() }
        if (cells.any { it >= paletteSize }) return null
        return Pattern(width, height, palette, cells)
    }
}
