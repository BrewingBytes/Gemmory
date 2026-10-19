package com.brewingbytes.gemmory.policy

/**
 * `day_played` (T36, eng D13): true for the first gem placed on a local day, on any canvas, and false for every
 * other gem that day, so the event is logged once a day. [store] keeps the last day it fired (local epoch days). Not thread-safe.
 */
class DayPlayed(private val clock: DayClock, private val store: StateStore<Long>) {
    private var last: Long? = store.load()

    /** A gem was placed: whether it's the day's first. */
    fun gemPlaced(): Boolean {
        val today = clock.today()
        if (last == today) return false
        last = today
        store.save(today)
        return true
    }
}
