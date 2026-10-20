package com.brewingbytes.gemmory.policy

/**
 * The daily play streak's state (#33): [length] days in a row, the last one [lastDay] (local epoch days), and the
 * day a missed day was last forgiven ([restDay]). A fresh install has played no day.
 */
data class StreakState(val length: Int = 0, val lastDay: Long = NEVER, val restDay: Long = NEVER) {
    companion object {
        const val NEVER = Long.MIN_VALUE
    }
}

/**
 * Daily play streak (#33): a day counts once the player finishes a picture that day, any picture, bundled or their
 * own photo (founder, 2026-10-17), by the local date (the allowance's [DayClock]). One missed day a week is forgiven, so a single day off doesn't break it; a longer gap starts over at
 * the next day played. A clock moved back changes nothing. No rewards, and the gallery shows it from [SHOWN_FROM]
 * days; a broken streak just isn't shown.
 */
class DailyStreak(private val clock: DayClock, private val store: StateStore<StreakState>) {
    private var state: StreakState = store.load() ?: StreakState()

    /** A picture was finished. Returns the new length when this was the first finish of a new day, else null. */
    fun played(): Int? {
        val next = played(state, clock.today())
        if (next == state) return null
        state = next
        store.save(next)
        return next.length
    }

    /** The streak as it stands today: 0 once it's broken. */
    fun current(): Int = shown(state, clock.today())

    /** The stored state, for the evening reminder's "breaks at midnight" check (T35). */
    fun state(): StreakState = state

    companion object {
        /** A one-day streak isn't news. */
        const val SHOWN_FROM = 2

        /** Days between two forgiven days off. */
        const val REST_EVERY = 7

        /** [state] after a gem placed on [today]. */
        fun played(state: StreakState, today: Long): StreakState {
            if (state.lastDay == StreakState.NEVER) return StreakState(1, today, state.restDay)
            val gap = today - state.lastDay
            return when {
                gap <= 0 -> state
                gap == 1L -> state.copy(length = state.length + 1, lastDay = today)
                gap == 2L && restAllowed(state, today) -> StreakState(state.length + 1, today, restDay = today - 1)
                else -> StreakState(1, today, state.restDay)
            }
        }

        /** The length that still stands on [today]: alive if the next day played would extend it. */
        fun shown(state: StreakState, today: Long): Int {
            if (state.lastDay == StreakState.NEVER) return 0
            val gap = today - state.lastDay
            val alive = gap <= 1 || (gap == 2L && restAllowed(state, today))
            return if (alive) state.length else 0
        }

        /** Whether yesterday (the day off before [today]) may be forgiven: none forgiven in the [REST_EVERY] days before it. */
        private fun restAllowed(state: StreakState, today: Long): Boolean =
            state.restDay == StreakState.NEVER || (today - 1) - state.restDay >= REST_EVERY
    }
}
