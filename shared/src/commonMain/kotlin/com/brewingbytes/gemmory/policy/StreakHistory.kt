package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.i64

/** One streak [day] (local epoch day) and how it counted; [canvas] is the first picture finished that day, if any. */
data class HistoryDay(val day: Long, val kind: DayKind, val canvas: String? = null)

/**
 * The streak's day log (#101, E4), for the calendar and the week row only: the streak preferences stay the source of
 * truth. [days] are in order, one per day, the last [StreakHistory.MAX_DAYS] of them. [backfilled] once the days
 * finished before the log existed were read from the play files.
 */
data class StreakHistoryState(val days: List<HistoryDay> = emptyList(), val backfilled: Boolean = false)

/** The day log's rules (#101, E4, O7). */
object StreakHistory {
    /** Days kept; older ones are dropped. */
    const val MAX_DAYS = 400

    /** [history] with [days] added; a day already logged keeps its first entry (the day's first finish). */
    fun record(history: StreakHistoryState, days: List<HistoryDay>): StreakHistoryState {
        val known = history.days.mapTo(HashSet()) { it.day }
        val added = days.filter { known.add(it.day) }
        if (added.isEmpty()) return history
        return history.copy(days = (history.days + added).sortedBy { it.day }.takeLast(MAX_DAYS))
    }

    /**
     * The days [state] can vouch for: none after its last day, so a restored or stale log can't add days the streak
     * never had (E4).
     */
    fun reconciled(history: StreakHistoryState, state: StreakState): List<HistoryDay> =
        if (state.lastDay == StreakState.NEVER) emptyList() else history.days.filter { it.day <= state.lastDay }

    /**
     * The once-only backfill (E4): each canvas's first finished day ([finishes], day to canvas) from the play files,
     * as finished days up to [state]'s last day. Earlier finishes come first on a shared day.
     */
    fun backfill(history: StreakHistoryState, finishes: List<Pair<Long, String>>, state: StreakState): StreakHistoryState {
        if (history.backfilled) return history
        val days = if (state.lastDay == StreakState.NEVER) {
            emptyList()
        } else {
            finishes.filter { it.first <= state.lastDay }.sortedBy { it.first }.map { HistoryDay(it.first, DayKind.FINISHED, it.second) }
        }
        return record(history, days).copy(backfilled = true)
    }
}

/**
 * The streak log file `filesDir/streak/history`, "GMRS" v1 (same envelope and CRC trailer as the save files). Body:
 * u8 flags (bit 0 backfilled), varint count, then per day in order: i64 day (local epoch days), u8 kind, u16 canvas
 * byte length (0 for none), the canvas in UTF-8. Canvas ids only, never photo data, so it may be backed up.
 */
object StreakHistoryCodec {
    val MAGIC = "GMRS".encodeToByteArray()
    const val VERSION = 1

    fun encode(state: StreakHistoryState): ByteArray {
        val w = ByteWriter()
        w.u8(if (state.backfilled) 1 else 0)
        w.varint(state.days.size)
        for (day in state.days) {
            w.i64(day.day)
            w.u8(day.kind.ordinal)
            val canvas = day.canvas?.encodeToByteArray() ?: ByteArray(0)
            w.u16(canvas.size)
            w.bytes(canvas)
        }
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<StreakHistoryState> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val state = try {
            val r = ByteReader(opened.body)
            val flags = r.u8()
            val count = r.varint()
            if (flags !in 0..1 || count !in 0..StreakHistory.MAX_DAYS) throw MalformedException()
            val days = List(count) {
                val day = r.i64()
                val kind = DayKind.entries.getOrNull(r.u8()) ?: throw MalformedException()
                val length = r.u16()
                if (length > r.remaining) throw MalformedException()
                val canvas = ByteArray(length) { r.u8().toByte() }.decodeToString().takeIf { it.isNotEmpty() }
                HistoryDay(day, kind, canvas)
            }
            val ordered = days.zipWithNext().all { (a, b) -> a.day < b.day }
            if (r.remaining != 0 || !ordered) throw MalformedException()
            StreakHistoryState(days, flags == 1)
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(state)
    }
}
