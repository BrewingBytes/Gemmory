package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.policy.BrokenStreak
import com.brewingbytes.gemmory.policy.Cover
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DailyStreak
import com.brewingbytes.gemmory.policy.StreakState
import kotlinx.coroutines.flow.StateFlow

/** The streak as it stands on [today] with [cover] (#101, O8). */
data class StreakSnapshot(val state: StreakState, val today: Long, val cover: Cover) {
    /** Days in a row; 0 once broken. */
    val shown: Int get() = DailyStreak.shown(state, today, cover)

    /** A picture was finished today. */
    val doneToday: Boolean get() = state.lastDay == today

    /** The missed days a finish today would cover (G13). */
    val awaiting: List<CoveredDay> get() = DailyStreak.awaitingCover(state, today, cover)

    /** The broken streak a repair can still bring back (#102): the chip's "0 ›" and the nudge's broken variant. */
    val repairOffer: BrokenStreak? get() = DailyStreak.repairOffer(state, today, cover)
}

/** The streak as shared screens read it (#101, iOS Stage 1c T64): Android's `StreakView` until it moves with `PlayStreak`. */
interface StreakSource {
    /** The streak now; it follows every finish, VIP change and inventory change. */
    val snapshot: StateFlow<StreakSnapshot>

    /** Re-reads the streak as of today. */
    fun refresh(): StreakSnapshot

    /** The keep-it-alive nudge showed on [day] (#102): true the first time that day, for `streak_nudge_shown`. */
    fun nudgeShown(day: Long): Boolean
}
