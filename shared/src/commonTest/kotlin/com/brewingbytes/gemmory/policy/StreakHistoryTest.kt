package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.Refusal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** #101 E4: the streak's day log, its "GMRS" file, the 400-day cap, the reconcile and the one-time backfill. */
class StreakHistoryTest {
    private val d = 20_000L

    @Test
    fun theFileRoundTripsAndRefusesDamage() {
        val state = StreakHistoryState(
            days = listOf(
                HistoryDay(d, DayKind.FINISHED, "BUNDLED/lighthouse"),
                HistoryDay(d + 1, DayKind.DAY_OFF),
                HistoryDay(d + 2, DayKind.FINISHED, "PHOTO/ph-é1"),
                HistoryDay(d + 3, DayKind.VIP_FREEZE),
                HistoryDay(d + 4, DayKind.FREEZE),
                HistoryDay(d + 5, DayKind.REPAIRED),
            ),
            backfilled = true,
        )
        val bytes = StreakHistoryCodec.encode(state)
        assertEquals(state, assertIs<Decoded.Ok<StreakHistoryState>>(StreakHistoryCodec.decode(bytes)).value)
        assertContentEquals(StreakHistoryCodec.MAGIC, bytes.copyOf(4))
        val flipped = bytes.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        assertEquals(Decoded.Refused(Refusal.BadCrc), StreakHistoryCodec.decode(flipped))
        assertEquals(Decoded.Refused(Refusal.TooShort), StreakHistoryCodec.decode(byteArrayOf(1, 2)))
        val body = (Envelope.open(bytes, StreakHistoryCodec.MAGIC) as Decoded.Ok<Envelope.Opened>).value.body
        assertEquals(Decoded.Refused(Refusal.NewerVersion(2)), StreakHistoryCodec.decode(Envelope.wrap(StreakHistoryCodec.MAGIC, 2, body)))
        // Days out of order are malformed, CRC or not.
        val unordered = StreakHistoryCodec.encode(state.copy(days = state.days.reversed()))
        assertEquals(Decoded.Refused(Refusal.Malformed), StreakHistoryCodec.decode(unordered))
        val trailing = Envelope.wrap(StreakHistoryCodec.MAGIC, 1, body + 0)
        assertEquals(Decoded.Refused(Refusal.Malformed), StreakHistoryCodec.decode(trailing))
    }

    @Test
    fun aDayKeepsItsFirstEntry() {
        val first = StreakHistory.record(StreakHistoryState(), listOf(HistoryDay(d, DayKind.FINISHED, "BUNDLED/a")))
        val again = StreakHistory.record(first, listOf(HistoryDay(d, DayKind.FINISHED, "BUNDLED/b")))
        assertSame(first, again, "the day's first finish stays; nothing to write")
        val next = StreakHistory.record(again, listOf(HistoryDay(d - 1, DayKind.DAY_OFF), HistoryDay(d + 1, DayKind.FINISHED, "BUNDLED/c")))
        assertEquals(listOf(d - 1, d, d + 1), next.days.map { it.day }, "kept in order")
    }

    @Test
    fun theLogKeepsTheLast400Days() {
        var log = StreakHistoryState()
        for (day in 0 until 401) log = StreakHistory.record(log, listOf(HistoryDay(d + day, DayKind.FINISHED, "BUNDLED/x")))
        assertEquals(StreakHistory.MAX_DAYS, log.days.size)
        assertEquals(d + 1, log.days.first().day, "the oldest is dropped")
        assertEquals(d + 400, log.days.last().day)
    }

    @Test
    fun daysPastTheStreaksLastDayAreIgnored() {
        val log = StreakHistoryState(listOf(HistoryDay(d, DayKind.FINISHED, "a"), HistoryDay(d + 1, DayKind.FINISHED, "b")))
        assertEquals(listOf(d), StreakHistory.reconciled(log, StreakState(length = 1, lastDay = d)).map { it.day })
        assertTrue(StreakHistory.reconciled(log, StreakState()).isEmpty(), "a restored log with no streak shows nothing")
    }

    @Test
    fun theBackfillRunsOnceUpToTheLastDay() {
        val state = StreakState(length = 3, lastDay = d + 2)
        val finishes = listOf(d + 2 to "BUNDLED/late", d to "BUNDLED/first", d + 9 to "BUNDLED/future", d + 2 to "BUNDLED/second")
        val filled = StreakHistory.backfill(StreakHistoryState(), finishes, state)
        assertTrue(filled.backfilled)
        assertEquals(listOf(HistoryDay(d, DayKind.FINISHED, "BUNDLED/first"), HistoryDay(d + 2, DayKind.FINISHED, "BUNDLED/late")), filled.days)
        assertSame(filled, StreakHistory.backfill(filled, listOf(d + 1 to "BUNDLED/x"), state), "only once")
        val empty = StreakHistory.backfill(StreakHistoryState(), finishes, StreakState())
        assertTrue(empty.backfilled)
        assertFalse(empty.days.isNotEmpty())
    }
}
