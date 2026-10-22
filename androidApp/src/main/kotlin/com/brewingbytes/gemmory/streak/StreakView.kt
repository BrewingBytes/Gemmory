package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.play.PlayStreak
import com.brewingbytes.gemmory.policy.BrokenStreak
import com.brewingbytes.gemmory.policy.Cover
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DailyStreak
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.StreakState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

/**
 * The one reader of the streak (#101, O8): the stored state with what covers a missed day (the VIP freeze while VIP;
 * else the streak freezes held, #102), for the chip, the streak screen and the achievements backfill. It follows every
 * finish, VIP change and inventory change; [refresh] picks up a new day.
 */
class StreakView(
    private val streak: PlayStreak,
    private val clock: DayClock,
    private val entitlements: StateFlow<Entitlements>,
    /** The inventory as Monetization last read it, for the freezes held. */
    private val inventory: StateFlow<InventoryState>,
    scope: CoroutineScope,
) {
    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<StreakSnapshot> = _snapshot.asStateFlow()

    private val following: Job = scope.launch { combine(streak.changes, entitlements, inventory) { _, _, _ -> }.collect { refresh() } }

    /** What covers a missed day right now: VIP never spends held freezes (O2), so they count only without it. */
    fun cover(): Cover {
        val vip = entitlements.value.vip
        return Cover(vip = vip, freezes = if (vip) 0 else inventory.value.freezes)
    }

    /** Re-reads the streak as of today. */
    fun refresh(): StreakSnapshot = read().also { _snapshot.value = it }

    /** Stops following, for a test that replaces this instance. */
    fun close() = following.cancel()

    private fun read() = StreakSnapshot(streak.state(), clock.today(), cover())
}
