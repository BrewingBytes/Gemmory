package com.brewingbytes.gemmory.save

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SaveCodecTest {
    private val grid = GridBinding(crc32 = 0x12345678, cellCount = 300)
    private val log = intArrayOf(5, 0, 299, 128)

    // Golden v1 file, built independently (Python struct + zlib.crc32): "GMRY", v1, grid CRC, count 4,
    // varints 5, 0, 299 (ab 02), 128 (80 01), CRC32 trailer. Changing it means a new schema version.
    private val goldenV1 = "474d5259000112345678040500ab0280013f7fcb36"

    // Synthetic v0 (never shipped): same frame, body = varint count + indices, no grid CRC.
    private val goldenV0 = "474d52590000040500ab028001757df91f"

    private val v0ToV1 = MigrationRegistry(
        mapOf(
            0 to SaveMigration { body, binding ->
                val w = ByteWriter()
                w.u32(binding.crc32)
                w.bytes(body)
                w.toByteArray()
            },
        ),
    )

    private fun refusal(bytes: ByteArray, codec: SaveCodec = SaveCodec()): Refusal =
        assertIs<Decoded.Refused>(codec.decode(bytes, grid)).reason

    @Test
    fun crc32MatchesTheStandardCheckValue() {
        assertEquals(0xCBF43926.toInt(), Crc32.of("123456789".encodeToByteArray()))
    }

    @Test
    fun encodesGoldenBytes() {
        assertEquals(goldenV1, SaveCodec().encode(grid, log).toHex())
    }

    @Test
    fun decodesGoldenBytesInPlacementOrder() {
        val ok = assertIs<Decoded.Ok<IntArray>>(SaveCodec().decode(hex(goldenV1), grid))
        assertContentEquals(log, ok.value)
    }

    @Test
    fun roundTripsEmptyAndFullLogs() {
        val codec = SaveCodec()
        val full = IntArray(grid.cellCount) { grid.cellCount - 1 - it }
        for (l in listOf(IntArray(0), full)) {
            assertContentEquals(l, assertIs<Decoded.Ok<IntArray>>(codec.decode(codec.encode(grid, l), grid)).value)
        }
    }

    @Test
    fun migratesV0ToV1() {
        val ok = assertIs<Decoded.Ok<IntArray>>(SaveCodec(v0ToV1).decode(hex(goldenV0), grid))
        assertContentEquals(log, ok.value)
    }

    @Test
    fun refusesAnOldVersionWithNoMigration() {
        assertEquals(Refusal.NoMigration(0), refusal(hex(goldenV0)))
    }

    @Test
    fun refusesAFailedMigration() {
        val broken = SaveCodec(MigrationRegistry(mapOf(0 to SaveMigration { _, _ -> null })))
        assertEquals(Refusal.Malformed, refusal(hex(goldenV0), broken))
    }

    @Test
    fun refusesANewerVersion() {
        val v2 = hex(goldenV1).also { it[5] = 2 }
        assertEquals(Refusal.NewerVersion(2), refusal(reseal(v2)))
    }

    @Test
    fun refusesAFlippedBit() {
        val bytes = hex(goldenV1).also { it[10] = (it[10].toInt() xor 0x01).toByte() }
        assertEquals(Refusal.BadCrc, refusal(bytes))
    }

    @Test
    fun refusesEveryTruncation() {
        val bytes = hex(goldenV1)
        for (n in 0 until bytes.size) {
            val r = refusal(bytes.copyOf(n))
            assertEquals(if (n < 10) Refusal.TooShort else Refusal.BadCrc, r, "truncated to $n bytes")
        }
    }

    @Test
    fun refusesBadMagic() {
        assertEquals(Refusal.BadMagic, refusal(hex(goldenV1).also { it[0] = 'X'.code.toByte() }))
    }

    @Test
    fun refusesAGridMismatch() {
        val other = GridBinding(crc32 = 0x0BADF00D, cellCount = 300)
        val bytes = SaveCodec().encode(other, log)
        assertEquals(Refusal.GridMismatch(expectedCrc32 = grid.crc32, actualCrc32 = other.crc32), refusal(bytes))
    }

    @Test
    fun refusesADuplicateIndex() {
        // A larger grid can legally encode the log; the 300-cell grid's check then sees the duplicate.
        val bytes = sealedV1(intArrayOf(5, 7, 5))
        assertEquals(Refusal.DuplicateIndex(5), refusal(bytes))
    }

    @Test
    fun refusesAnOutOfRangeIndex() {
        assertEquals(Refusal.IndexOutOfRange(300), refusal(sealedV1(intArrayOf(1, 300))))
    }

    @Test
    fun refusesACountLargerThanTheGridOrTheBody() {
        assertEquals(Refusal.Malformed, refusal(sealedV1(IntArray(0), count = 301)))
        assertEquals(Refusal.Malformed, refusal(sealedV1(intArrayOf(1, 2), count = 3)))
    }

    @Test
    fun refusesTrailingBytes() {
        assertEquals(Refusal.Malformed, refusal(sealedV1(intArrayOf(1, 2, 3), count = 2)))
    }

    @Test
    fun refusesAnOverlongVarint() {
        val w = ByteWriter()
        w.u32(grid.crc32)
        w.varint(1)
        w.bytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F))
        assertEquals(Refusal.Malformed, refusal(Envelope.wrap(SaveCodec.MAGIC, 1, w.toByteArray())))
    }

    @Test
    fun neverEncodesALogALoadWouldRefuse() {
        assertFailsWith<IllegalArgumentException> { SaveCodec().encode(grid, intArrayOf(1, 1)) }
        assertFailsWith<IllegalArgumentException> { SaveCodec().encode(grid, intArrayOf(-1)) }
        assertFailsWith<IllegalArgumentException> { SaveCodec().encode(grid, intArrayOf(300)) }
    }

    /** A v1 file with a valid CRC whose body the encoder would never produce. */
    private fun sealedV1(indices: IntArray, count: Int = indices.size): ByteArray {
        val w = ByteWriter()
        w.u32(grid.crc32)
        w.varint(count)
        for (i in indices) w.varint(i)
        return Envelope.wrap(SaveCodec.MAGIC, SaveCodec.VERSION, w.toByteArray())
    }
}
