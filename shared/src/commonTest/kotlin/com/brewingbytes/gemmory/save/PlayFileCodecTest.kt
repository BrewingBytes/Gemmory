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
    fun layoutIsMagicVersionAndTwoLongs() {
        val bytes = PlayFileCodec.encode(PlayTimes(lastGemMillis = 1, firstFinishedDay = null))
        assertEquals("474d5254" + "0001" + "0000000000000001" + "8000000000000000", bytes.toHex().dropLast(8))
    }

    @Test
    fun refusesCorruptionNewerVersionsAndMalformedBodies() {
        val bytes = PlayFileCodec.encode(PlayTimes(5, 6))
        assertEquals(Refusal.BadCrc, refusal(bytes.copyOf().also { it[8] = (it[8] + 1).toByte() }))
        assertEquals(Refusal.TooShort, refusal(bytes.copyOf(5)))
        assertEquals(Refusal.BadMagic, refusal(CanvasRecordCodec.encode(CanvasRecord("", 0, 0))))
        assertEquals(Refusal.NewerVersion(2), refusal(reseal(bytes.copyOf().also { it[5] = 2 })))
        assertEquals(Refusal.NoMigration(0), refusal(reseal(bytes.copyOf().also { it[5] = 0 })))
        // A body one byte short or long.
        assertEquals(Refusal.Malformed, refusal(reseal(bytes.copyOf(bytes.size - 1))))
        assertEquals(Refusal.Malformed, refusal(reseal(bytes.copyOf(bytes.size + 1))))
    }
}
