package com.brewingbytes.gemmory.policy

/**
 * The daily play streak's state (#33): [length] days in a row, the last one [lastDay] (local epoch days), the day a
 * missed day was last covered by the weekly day off ([restDay]) and by the VIP weekly freeze ([vipFreezeDay], #101
 * E6). When a streak breaks, its length and last day are kept as [brokenLength] and [brokenLastDay], so a repair (#102,
 * O1) can still bring it back after the player finished a picture on the day they came back. A fresh install has
 * played no day.
 */
data class StreakState(
    val length: Int = 0,
    val lastDay: Long = NEVER,
    val restDay: Long = NEVER,
    val vipFreezeDay: Long = NEVER,
    val brokenLength: Int = 0,
    val brokenLastDay: Long = NEVER,
) {
    companion object {
        const val NEVER = Long.MIN_VALUE
    }
}

/**
 * What can cover a missed day besides the weekly day off, which every player has (#101): the VIP weekly freeze while
 * [vip] (E6), then [freezes] streak freezes held (E5; they arrive in #102).
 */
data class Cover(val vip: Boolean = false, val freezes: Int = 0) {
    companion object {
        val NONE = Cover()
    }
}

/** How a day of the streak counts (#101): a picture finished, or a missed day covered or repaired. */
enum class DayKind { FINISHED, DAY_OFF, VIP_FREEZE, FREEZE, REPAIRED }

/** A missed [day] and what covered it. */
data class CoveredDay(val day: Long, val kind: DayKind)

/**
 * A finish on a new streak day (#101): the [state] to save, the missed days [covered] on the way (oldest first; empty
 * when the streak simply went on, started or started over), and how many streak freezes that spent.
 */
data class DayResult(val state: StreakState, val covered: List<CoveredDay> = emptyList()) {
    val length: Int get() = state.length
    val freezesUsed: Int get() = covered.count { it.kind == DayKind.FREEZE }
}

/** A streak that ended (O1): [length] days, ending on [endedOn], the first missed day nothing covered. */
data class BrokenStreak(val length: Int, val endedOn: Long)

/**
 * Daily play streak (#33): a day counts once the player finishes a picture that day, any picture, bundled or their
 * own photo (founder, 2026-10-17), by the local date (the allowance's [DayClock]). A missed day is covered by the
 * weekly day off (one in any [REST_EVERY] days), then for VIP by the VIP weekly freeze, then by a streak freeze held
 * (#101); a gap with a day nothing covers starts over at the next day played. A clock moved back changes nothing.
 */
class DailyStreak(private val clock: DayClock, private val store: StateStore<StreakState>) {
    private var state: StreakState = store.load() ?: StreakState()

    /** A picture was finished. Returns the new length when this was the first finish of a new day, else null. */
    fun played(): Int? = play(Cover.NONE)?.length

    /** A picture was finished with [cover] at hand: the day's result when this was the first finish of a new day. */
    fun play(cover: Cover): DayResult? {
        val result = played(state, clock.today(), cover) ?: return null
        state = result.state
        store.save(result.state)
        return result
    }

    /** The streak as it stands today: 0 once it's broken. */
    fun current(): Int = shown(state, clock.today())

    /** The stored state, for the evening reminder's "breaks at midnight" check (T35). */
    fun state(): StreakState = state

    companion object {
        /** A one-day streak isn't news. */
        const val SHOWN_FROM = 2

        /** Days between two forgiven days off, and between two VIP freezes. */
        const val REST_EVERY = 7

        /** A repair is offered this many days after the streak ended (#102, O1). */
        const val REPAIR_WINDOW = 2

        /** Days between two free VIP repairs (#102, founder 2026-10-22); a VIP repairs with a video in between. */
        const val VIP_REPAIR_EVERY = 7

        /** Days between two grace repairs (online no-fill, #102, founder 2026-10-22). Video repairs have no limit. */
        const val GRACE_REPAIR_EVERY = 30

        /** [state] after a gem placed on [today], with only the day off to cover a missed day. */
        fun played(state: StreakState, today: Long): StreakState = played(state, today, Cover.NONE)?.state ?: state

        /** A finish on [today]: null when it isn't a new day, else the streak extended, started or started over. */
        fun played(state: StreakState, today: Long, cover: Cover): DayResult? {
            if (state.lastDay == StreakState.NEVER) return DayResult(state.copy(length = 1, lastDay = today))
            val gap = today - state.lastDay
            if (gap <= 0) return null
            if (gap == 1L) return DayResult(state.copy(length = state.length + 1, lastDay = today))
            val covered = coverMissed(state, today, cover)
            if (covered != null) {
                return DayResult(
                    state.copy(
                        length = state.length + 1,
                        lastDay = today,
                        restDay = covered.lastOrNull { it.kind == DayKind.DAY_OFF }?.day ?: state.restDay,
                        vipFreezeDay = covered.lastOrNull { it.kind == DayKind.VIP_FREEZE }?.day ?: state.vipFreezeDay,
                    ),
                    covered,
                )
            }
            return DayResult(state.copy(length = 1, lastDay = today, brokenLength = state.length, brokenLastDay = state.lastDay))
        }

        /** The length that still stands on [day]: alive if a finish that day would extend it. */
        fun shown(state: StreakState, day: Long, cover: Cover = Cover.NONE): Int {
            if (state.lastDay == StreakState.NEVER) return 0
            val gap = day - state.lastDay
            val alive = gap <= 1 || coverMissed(state, day, cover) != null
            return if (alive) state.length else 0
        }

        /** The missed days a finish on [today] would cover (G13), oldest first; empty when none is missed or it broke. */
        fun awaitingCover(state: StreakState, today: Long, cover: Cover = Cover.NONE): List<CoveredDay> {
            if (state.lastDay == StreakState.NEVER || today - state.lastDay < 2) return emptyList()
            return coverMissed(state, today, cover).orEmpty()
        }

        /**
         * The streak that ended, if [state] on [today] is a broken one or a new one started after a break (O1): its
         * length, and the day it ended, the first missed day that nothing covers (derived now, never stored).
         */
        fun broken(state: StreakState, today: Long, cover: Cover = Cover.NONE): BrokenStreak? {
            if (state.lastDay == StreakState.NEVER) return null
            if (shown(state, today, cover) == 0) {
                return BrokenStreak(state.length, endedOn(state, state.lastDay, today, cover))
            }
            if (state.brokenLength == 0 || state.brokenLastDay == StreakState.NEVER) return null
            val startedOn = state.lastDay - state.length + 1
            return BrokenStreak(state.brokenLength, endedOn(state, state.brokenLastDay, startedOn, cover))
        }

        /**
         * A repair is offered (#102, O1) for a broken streak of at least [SHOWN_FROM] days, within [REPAIR_WINDOW] days
         * of the day it ended. A video repair may be done any number of times (founder 2026-10-22: ad revenue).
         */
        fun repairOffer(state: StreakState, today: Long, cover: Cover): BrokenStreak? {
            val broken = broken(state, today, cover) ?: return null
            return broken.takeIf { it.length >= SHOWN_FROM && today - it.endedOn <= REPAIR_WINDOW }
        }

        /** The last day [broken] can still be repaired ("You can do this until Thursday."). */
        fun repairUntil(broken: BrokenStreak): Long = broken.endedOn + REPAIR_WINDOW

        /** VIP's free repair is ready: none in the [VIP_REPAIR_EVERY] days before [today]. */
        fun vipRepairReady(lastVipRepairDay: Long, today: Long): Boolean =
            lastVipRepairDay == StreakState.NEVER || today - lastVipRepairDay >= VIP_REPAIR_EVERY

        /** A grace repair may be offered on an online no-fill: none in the [GRACE_REPAIR_EVERY] days before [today]. */
        fun graceRepairAllowed(lastGraceRepairDay: Long, today: Long): Boolean =
            lastGraceRepairDay == StreakState.NEVER || today - lastGraceRepairDay >= GRACE_REPAIR_EVERY

        /**
         * [state] repaired on [today] (O1): the ended streak is back, and every missed day since counts as repaired. A
         * picture already finished since the break adds on (a repair after finishing today keeps today); otherwise the
         * streak stands through yesterday, so finishing today extends it.
         */
        fun repair(state: StreakState, today: Long, cover: Cover): DayResult? {
            val broken = broken(state, today, cover) ?: return null
            val restarted = shown(state, today, cover) > 0
            val (length, lastDay, gapEnd) = if (restarted) {
                Triple(broken.length + state.length, state.lastDay, state.lastDay - state.length)
            } else {
                Triple(broken.length, today - 1, today - 1)
            }
            val brokenLastDay = if (restarted) state.brokenLastDay else state.lastDay
            val repaired = (brokenLastDay + 1..gapEnd).map { CoveredDay(it, DayKind.REPAIRED) }
            return DayResult(state.copy(length = length, lastDay = lastDay, brokenLength = 0, brokenLastDay = StreakState.NEVER), repaired)
        }

        /**
         * Covers each day missed between [state]'s last day and [today], oldest first: the day off, then the VIP
         * freeze, then the streak freezes. Null when a day is left that nothing covers.
         */
        private fun coverMissed(state: StreakState, today: Long, cover: Cover): List<CoveredDay>? {
            val missed = state.lastDay + 1 until today
            // At most one day off and one VIP freeze fit in a gap this short; a long absence stops here.
            if (missed.last - missed.first + 1 > 1 + (if (cover.vip) 1 else 0) + cover.freezes) return null
            var rest = state.restDay
            var vip = state.vipFreezeDay
            var freezes = cover.freezes
            return missed.map { day ->
                when {
                    allowed(rest, day) -> DayKind.DAY_OFF.also { rest = day }
                    cover.vip && allowed(vip, day) -> DayKind.VIP_FREEZE.also { vip = day }
                    freezes > 0 -> DayKind.FREEZE.also { freezes-- }
                    else -> return null
                }.let { CoveredDay(day, it) }
            }
        }

        /** The first day after [lastDay] (and before [until]) that nothing covers. */
        private fun endedOn(state: StreakState, lastDay: Long, until: Long, cover: Cover): Long {
            var rest = state.restDay
            var vip = state.vipFreezeDay
            var freezes = cover.freezes
            for (day in lastDay + 1 until until) {
                when {
                    allowed(rest, day) -> rest = day
                    cover.vip && allowed(vip, day) -> vip = day
                    freezes > 0 -> freezes--
                    else -> return day
                }
            }
            return lastDay + 1
        }

        /** Whether [day] may be covered by a weekly cover last used on [last]: none in the [REST_EVERY] days before it. */
        private fun allowed(last: Long, day: Long): Boolean = last == StreakState.NEVER || day - last >= REST_EVERY
    }
}
