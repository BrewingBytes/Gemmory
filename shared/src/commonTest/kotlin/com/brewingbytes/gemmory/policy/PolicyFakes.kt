package com.brewingbytes.gemmory.policy

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** A clock the test moves by hand, in a zone it can switch. Bucharest has DST (last Sundays of March and October). */
class FakeClock(start: String = "2026-10-14T10:00", var zone: TimeZone = BUCHAREST) {
    var now: Long = at(start)

    val day = DayClock({ now }) { zone }

    fun at(local: String): Long = LocalDateTime.parse(local).toInstant(zone).toEpochMilliseconds()

    fun set(local: String) {
        now = at(local)
    }

    fun advanceMinutes(minutes: Long) {
        now += minutes * 60_000L
    }

    companion object {
        val BUCHAREST = TimeZone.of("Europe/Bucharest")
    }
}

/** In-memory store that also round-trips every save through the real codec, so the file format is exercised. */
class CodecStore<T>(private val encode: (T) -> ByteArray, private val decode: (ByteArray) -> T?) : StateStore<T> {
    var bytes: ByteArray? = null
    var saves = 0

    override fun load(): T? = bytes?.let(decode)

    override fun save(value: T) {
        saves++
        bytes = encode(value)
    }
}
