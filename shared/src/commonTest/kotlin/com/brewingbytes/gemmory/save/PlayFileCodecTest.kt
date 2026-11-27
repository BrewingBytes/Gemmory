package com.brewingbytes.gemmory.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayFileCodecTest {
    private fun refusal(bytes: ByteArray): Refusal = assertIs<Decoded.Refused>(PlayFileCodec.decode(bytes)).reason

    private fun roundTrip(times: PlayTimes): PlayTimes =
        assertIs<Decoded.Ok<PlayTimes>>(PlayFileCodec.decode(PlayFileCodec.encode(times))).value

    @Test
    fun roundTripsBothFieldsAndUnknowns() {
        assertEquals(PlayTimes(1_791_900_000_000, 20_380), roundTrip(PlayTimes(1_791_900_000_000, 20_380)))
        assertEquals(PlayTimes(), roundTrip(PlayTimes()))
        assertEquals(PlayTimes(lastGemMillis = null, firstFinishedDay = 0), roundTrip(PlayTimes(null, 0)))
    }

    @Test
    fun layoutIsMagicVersionTwoLongsAndSittings() {
        val bytes = PlayFileCodec.encode(PlayTimes(lastGemMillis = 1, firstFinishedDay = null, sittings = 3))
        assertEquals("474d5254" + "0002" + "0000000000000001" + "8000000000000000" + "00000003", bytes.toHex().dropLast(8))
        assertEquals(PlayTimes(1, null, 3), roundTrip(PlayTimes(1, null, 3)))
    }

    /** A file written before 1.0.7 (v1, two longs) still reads, with 0 sittings: nothing migrates. */
    @Test
    fun aVersionOneFileReadsZeroSittings() {
        val v1 = reseal(PlayFileCodec.encode(PlayTimes(5, 6)).copyOf().also { it[5] = 1 }.let { it.copyOf(it.size - 4) })
        assertEquals(Decoded.Ok(PlayTimes(5, 6, 0)), PlayFileCodec.decode(v1))
    }

    @Test
    fun refusesCorruptionNewerVersionsAndMalformedBodies() {
        val bytes = PlayFileCodec.encode(PlayTimes(5, 6))
        assertEquals(Refusal.BadCrc, refusal(bytes.copyOf().also { it[8] = (it[8] + 1).toByte() }))
        assertEquals(Refusal.TooShort, refusal(bytes.copyOf(5)))
        assertEquals(Refusal.BadMagic, refusal(CanvasRecordCodec.encode(CanvasRecord("", 0, 0))))
        assertEquals(Refusal.NewerVersion(3), refusal(reseal(bytes.copyOf().also { it[5] = 3 })))
        assertEquals(Refusal.NoMigration(0), refusal(reseal(bytes.copyOf().also { it[5] = 0 })))
        // A body one byte short or long.
        assertEquals(Refusal.Malformed, refusal(reseal(bytes.copyOf(bytes.size - 1))))
        assertEquals(Refusal.Malformed, refusal(reseal(bytes.copyOf(bytes.size + 1))))
    }
}
