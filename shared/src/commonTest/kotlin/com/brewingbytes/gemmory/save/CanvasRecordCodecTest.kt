package com.brewingbytes.gemmory.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CanvasRecordCodecTest {
    private val record = CanvasRecord("My photo · 13 Oct", createdAtMillis = 1_791_900_000_000, playMillis = 312_000)

    private fun refusal(bytes: ByteArray): Refusal = assertIs<Decoded.Refused>(CanvasRecordCodec.decode(bytes)).reason

    @Test
    fun roundTripsTitleWithNonAsciiCharacters() {
        val decoded = assertIs<Decoded.Ok<CanvasRecord>>(CanvasRecordCodec.decode(CanvasRecordCodec.encode(record)))
        assertEquals(record, decoded.value)
    }

    @Test
    fun emptyTitleForBundledPictures() {
        val bundled = CanvasRecord("", 0, 0)
        assertEquals(bundled, assertIs<Decoded.Ok<CanvasRecord>>(CanvasRecordCodec.decode(CanvasRecordCodec.encode(bundled))).value)
    }

    @Test
    fun layoutIsMagicVersionLengthTitleAndTwoLongs() {
        val bytes = CanvasRecordCodec.encode(CanvasRecord("Ab", createdAtMillis = 1, playMillis = 2))
        assertEquals("474d5243" + "0001" + "0002" + "4162" + "0000000000000001" + "0000000000000002", bytes.toHex().dropLast(8))
    }

    @Test
    fun refusesCorruptionNewerVersionsAndMalformedBodies() {
        val bytes = CanvasRecordCodec.encode(record)
        assertEquals(Refusal.BadCrc, refusal(bytes.copyOf().also { it[8] = (it[8] + 1).toByte() }))
        assertEquals(Refusal.TooShort, refusal(bytes.copyOf(5)))
        assertEquals(Refusal.NewerVersion(2), refusal(reseal(bytes.copyOf().also { it[5] = 2 })))
        // Title length longer than the body.
        assertEquals(Refusal.Malformed, refusal(reseal(bytes.copyOf().also { it[6] = 0x7F })))
        // Invalid UTF-8 in the title.
        assertEquals(Refusal.Malformed, refusal(reseal(bytes.copyOf().also { it[8] = 0xFF.toByte() })))
    }

    @Test
    fun rejectsTitlesOverTheLimit() {
        assertFailsWith<IllegalArgumentException> { CanvasRecord("x".repeat(81), 0, 0) }
    }
}
