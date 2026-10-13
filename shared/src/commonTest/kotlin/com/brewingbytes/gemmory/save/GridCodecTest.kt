package com.brewingbytes.gemmory.save

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.convert
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GridCodecTest {
    private val pattern = Pattern(
        width = 3,
        height = 2,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(50.0, 0.0, 0.0)),
            PaletteColor(2, 0xFFFFEE01.toInt(), Lab(100.0, -0.5, 2.25)),
        ),
        cells = intArrayOf(0, 1, 1, 0, 0, 1),
    )

    // Golden v1 grid, built independently (Python struct + zlib.crc32). Changing it means a new grid version,
    // and every existing save bound to the old CRC would be refused.
    private val golden = "474d52470001000300020002102030404900000000000000000000000000000000000000000000ffee01405900" +
        "0000000000bfe000000000000040020000000000000001010000014610357d"
    private val goldenCrc = 0x4610357d

    private fun refusal(bytes: ByteArray): Refusal = assertIs<Decoded.Refused>(GridCodec.decode(bytes)).reason

    @Test
    fun encodesGoldenBytes() {
        assertEquals(golden, GridCodec.encode(pattern).toHex())
    }

    @Test
    fun decodesGoldenBytesWithItsCrc() {
        val grid = assertIs<Decoded.Ok<GridFile>>(GridCodec.decode(hex(golden))).value
        assertEquals(pattern, grid.pattern)
        assertEquals(goldenCrc, grid.crc32)
        assertEquals(GridBinding(goldenCrc, 6), grid.binding)
    }

    @Test
    fun roundTripsAConvertedPatternExactly() {
        val spec = GridSpec.of(CanvasSize.QUICK, AspectRatio.SQUARE)
        val pixels = IntArray(64 * 64) { i -> (0xFF shl 24) or ((i * 37) and 0xFFFFFF) }
        val converted = convert(pixels, 64, 64, spec, seed = 7)
        val decoded = assertIs<Decoded.Ok<GridFile>>(GridCodec.decode(GridCodec.encode(converted))).value
        assertEquals(converted, decoded.pattern)
    }

    @Test
    fun refusesCorruptTruncatedAndNewerGrids() {
        val bytes = hex(golden)
        assertEquals(Refusal.BadCrc, refusal(bytes.copyOf(bytes.size - 1)))
        assertEquals(Refusal.BadCrc, refusal(bytes.copyOf().also { it[20] = (it[20] + 1).toByte() }))
        assertEquals(Refusal.TooShort, refusal(bytes.copyOf(9)))
        assertEquals(Refusal.BadMagic, refusal(hex(SaveCodecGolden)))
        assertEquals(Refusal.NewerVersion(2), refusal(reseal(bytes.copyOf().also { it[5] = 2 })))
    }

    @Test
    fun refusesImpossibleBodiesEvenWithAValidCrc() {
        val bytes = hex(golden)
        val slotTooBig = bytes.copyOf().also { it[it.size - 5] = 2 }
        assertEquals(Refusal.Malformed, refusal(reseal(slotTooBig)))
        val zeroWidth = bytes.copyOf().also { it[7] = 0 }
        assertEquals(Refusal.Malformed, refusal(reseal(zeroWidth)))
        val oneCellShort = bytes.copyOfRange(0, bytes.size - 5) + bytes.copyOfRange(bytes.size - 4, bytes.size)
        assertEquals(Refusal.Malformed, refusal(reseal(oneCellShort)))
    }

    @Test
    fun rejectsPatternsItCannotRepresent() {
        val translucent = Pattern(1, 1, listOf(PaletteColor(1, 0x80102030.toInt(), Lab(1.0, 0.0, 0.0))), intArrayOf(0))
        assertFailsWith<IllegalArgumentException> { GridCodec.encode(translucent) }
        val misnumbered = Pattern(1, 1, listOf(PaletteColor(3, 0xFF102030.toInt(), Lab(1.0, 0.0, 0.0))), intArrayOf(0))
        assertFailsWith<IllegalArgumentException> { GridCodec.encode(misnumbered) }
    }

    private companion object {
        const val SaveCodecGolden = "474d5259000112345678040500ab0280013f7fcb36"
    }
}
