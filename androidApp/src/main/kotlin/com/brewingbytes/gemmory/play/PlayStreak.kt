package com.brewingbytes.gemmory.play

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.brewingbytes.gemmory.policy.Cover
import com.brewingbytes.gemmory.policy.BrokenStreak
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DailyStreak
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.DayResult
import com.brewingbytes.gemmory.policy.HistoryDay
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.StreakHistory
import com.brewingbytes.gemmory.policy.StreakHistoryState
import com.brewingbytes.gemmory.policy.StreakState
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A new streak day to celebrate (#101, E2): the [day] it counted on, the new [length], the missed days [covered] on the
 * way, when the picture was [finishedAt] (G9: a cold start celebrates only within 30 minutes) and which [canvas].
 */
data class PendingCelebration(
    val day: Long,
    val length: Int,
    val covered: List<CoveredDay>,
    val finishedAt: Long,
    val canvas: String,
)

/** How a broken streak is brought back (#102): a rewarded video, the online no-fill grace, or VIP's weekly repair. */
enum class RepairHow { VIDEO, GRACE, VIP }

/**
 * The daily play streak (#33, #101) on this phone. Kept in the "streak" preferences, which may be backed up: it's no
 * photo data, and a restore can't add days. Each new streak day is resolved on the monetization policy thread with VIP
 * and the freezes held ([resolve], O2), committed with the day's pending celebration before anything is spent (O3),
 * then logged in the day log [history] (E4). Every 5th day earns a gift spin (#38) inside [resolve]; [onDay] tells
 * achievements. Safe from any thread.
 */
class PlayStreak(
    context: Context,
    private val clock: DayClock,
    private val analytics: Analytics,
    private val history: StateStore<StreakHistoryState>,
    private val scope: CoroutineScope,
    /** Runs [block] on the policy thread with VIP and the streak freezes held (`Monetization.streakDay`). */
    private val resolve: suspend (block: (vip: Boolean, freezes: Int) -> DayResult?) -> DayResult?,
    /** Runs [block] on the policy thread with what covers a missed day now, spending nothing (`Monetization.repairStreak`). */
    private val resolveRepair: suspend (block: (cover: Cover) -> DayResult?) -> DayResult?,
    /** A new streak day of this length (#49 achievements). */
    private val onDay: (length: Int) -> Unit = {},
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private var state: StreakState = load(prefs)
    private var pending: PendingCelebration? = loadPending(prefs)
    private var log: StreakHistoryState? = null
    private var lastVipRepair: Long = prefs.getLong(LAST_VIP_REPAIR, StreakState.NEVER)
    private var lastGraceRepair: Long = prefs.getLong(LAST_GRACE_REPAIR, StreakState.NEVER)

    private val _changes = MutableStateFlow(0)

    /** Ticks on every change to the streak, for [com.brewingbytes.gemmory.streak.StreakView]. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    @Volatile
    private var lastPlay: Deferred<DayResult?>? = null

    /**
     * A picture, [canvas] (`CanvasStore.reminderKey`), was finished: the first one of a new day extends the streak, and
     * says so to Analytics. Runs on its own, so a screen closing right after can't drop it.
     */
    fun played(canvas: String): Deferred<DayResult?> = scope.async {
        val result = resolve { vip, freezes -> resolveLocked(Cover(vip, freezes), canvas) }
        if (result != null) {
            analytics.log(AnalyticsEvent.streakDay(result.length))
            if (result.freezesUsed > 0) analytics.log(AnalyticsEvent.streakFreezeUsed(result.freezesUsed))
            onDay(result.length)
            _changes.update { it + 1 }
        }
        result
    }.also { lastPlay = it }

    /** The last finish's result, once it's resolved (the completion exit waits for it, #101 E3). */
    suspend fun settled(): DayResult? = lastPlay?.await()

    @Synchronized
    private fun resolveLocked(cover: Cover, canvas: String): DayResult? {
        val today = clock.today()
        val result = DailyStreak.played(state, today, cover) ?: return null
        val celebration = PendingCelebration(today, result.length, result.covered, clock.nowMillis(), canvas)
        // Committed before the caller spends a freeze (O3): a crash in between keeps the freeze, never loses the day.
        prefs.edit(commit = true) {
            putState(result.state)
            putPending(celebration)
        }
        state = result.state
        pending = celebration
        val days = result.covered.map { HistoryDay(it.day, it.kind) } + HistoryDay(today, DayKind.FINISHED, canvas)
        saveLog(StreakHistory.record(logLocked(), days))
        return result
    }

    /**
     * Brings the broken streak back (#102, O1) [how] it was paid for, and logs `streak_repair_completed`. A watched video
     * always pays while the streak is broken, even if its window closed during the video; VIP's free repair needs VIP
     * and a ready week. Null when there's nothing to repair.
     */
    suspend fun repair(how: RepairHow): DayResult? {
        val result = resolveRepair { cover -> repairLocked(cover, how) } ?: return null
        analytics.log(AnalyticsEvent.streakRepairCompleted(vip = how == RepairHow.VIP))
        _changes.update { it + 1 }
        return result
    }

    @Synchronized
    private fun repairLocked(cover: Cover, how: RepairHow): DayResult? {
        val today = clock.today()
        if (how == RepairHow.VIP && !(cover.vip && DailyStreak.vipRepairReady(lastVipRepair, today))) return null
        val result = DailyStreak.repair(state, today, cover) ?: return null
        prefs.edit(commit = true) {
            putState(result.state)
            when (how) {
                RepairHow.VIP -> putLong(LAST_VIP_REPAIR, today)
                RepairHow.GRACE -> putLong(LAST_GRACE_REPAIR, today)
                RepairHow.VIDEO -> Unit
            }
        }
        state = result.state
        when (how) {
            RepairHow.VIP -> lastVipRepair = today
            RepairHow.GRACE -> lastGraceRepair = today
            RepairHow.VIDEO -> Unit
        }
        saveLog(StreakHistory.record(logLocked(), result.covered.map { HistoryDay(it.day, it.kind) }))
        return result
    }

    /** The streak that can be repaired today with [cover] (#102), if any. */
    fun repairOffer(cover: Cover): BrokenStreak? = DailyStreak.repairOffer(state(), clock.today(), cover)

    /** The day VIP's free repair was last used (#102), or [StreakState.NEVER]. */
    @Synchronized
    fun lastVipRepairDay(): Long = lastVipRepair

    /** VIP's free repair is ready this week (#102). */
    fun vipRepairReady(): Boolean = DailyStreak.vipRepairReady(lastVipRepairDay(), clock.today())

    /** A grace repair may be offered on an online no-fill: none in the last 30 days (#102). */
    @Synchronized
    fun graceRepairAllowed(): Boolean = DailyStreak.graceRepairAllowed(lastGraceRepair, clock.today())

    /** The stored state, for the evening reminder (T35) and the streak view. */
    @Synchronized
    fun state(): StreakState = state

    /** Days in a row as of today with [cover]; 0 once broken. */
    fun current(cover: Cover = Cover.NONE): Int = DailyStreak.shown(state(), clock.today(), cover)

    /** Today's celebration not shown yet (E2), if any. */
    @Synchronized
    fun pendingCelebration(): PendingCelebration? = pending?.takeIf { it.day == clock.today() }

    /** Today's celebration, now being shown, or dropped (G9); null if there is none. Either way it's gone after. */
    @Synchronized
    fun takeCelebration(): PendingCelebration? {
        val taken = pendingCelebration()
        if (pending != null) {
            pending = null
            prefs.edit(commit = true) { clearPending() }
        }
        return taken
    }

    /** The day log as far as the streak vouches for it (E4). */
    @Synchronized
    fun history(): List<HistoryDay> = StreakHistory.reconciled(logLocked(), state)

    /**
     * Once per install (E4): logs the days canvases were first finished on, read from the play files by [finishes]
     * (day to canvas). A log already backfilled reads nothing.
     */
    suspend fun backfill(finishes: suspend () -> List<Pair<Long, String>>) {
        if (synchronized(this) { logLocked().backfilled }) return
        val found = finishes()
        synchronized(this) { saveLog(StreakHistory.backfill(logLocked(), found, state)) }
        _changes.update { it + 1 }
    }

    private fun logLocked(): StreakHistoryState = log ?: (history.load() ?: StreakHistoryState()).also { log = it }

    private fun saveLog(next: StreakHistoryState) {
        if (next == log) return
        log = next
        history.save(next)
    }

    private fun SharedPreferences.Editor.putState(value: StreakState) {
        putInt(LENGTH, value.length)
        putLong(LAST_DAY, value.lastDay)
        putLong(REST_DAY, value.restDay)
        putLong(VIP_FREEZE_DAY, value.vipFreezeDay)
        putInt(BROKEN_LENGTH, value.brokenLength)
        putLong(BROKEN_LAST_DAY, value.brokenLastDay)
    }

    private fun SharedPreferences.Editor.putPending(value: PendingCelebration) {
        putLong(P_DAY, value.day)
        putInt(P_LENGTH, value.length)
        putString(P_COVERED, value.covered.joinToString(",") { "${it.day}:${it.kind.name}" })
        putLong(P_FINISHED_AT, value.finishedAt)
        putString(P_CANVAS, value.canvas)
    }

    private fun SharedPreferences.Editor.clearPending() {
        for (key in listOf(P_DAY, P_LENGTH, P_COVERED, P_FINISHED_AT, P_CANVAS)) remove(key)
    }

    private companion object {
        const val FILE = "streak"
        const val LENGTH = "length"
        const val LAST_DAY = "lastDay"
        const val REST_DAY = "restDay"
        const val VIP_FREEZE_DAY = "vipFreezeDay"
        const val BROKEN_LENGTH = "brokenLength"
        const val BROKEN_LAST_DAY = "brokenLastDay"
        const val LAST_VIP_REPAIR = "lastVipRepairDay"
        const val LAST_GRACE_REPAIR = "lastGraceRepairDay"
        const val P_DAY = "pendingDay"
        const val P_LENGTH = "pendingLength"
        const val P_COVERED = "pendingCovered"
        const val P_FINISHED_AT = "pendingFinishedAt"
        const val P_CANVAS = "pendingCanvas"

        fun load(prefs: SharedPreferences): StreakState {
            if (!prefs.contains(LAST_DAY)) return StreakState()
            return StreakState(
                length = prefs.getInt(LENGTH, 0),
                lastDay = prefs.getLong(LAST_DAY, StreakState.NEVER),
                restDay = prefs.getLong(REST_DAY, StreakState.NEVER),
                vipFreezeDay = prefs.getLong(VIP_FREEZE_DAY, StreakState.NEVER),
                brokenLength = prefs.getInt(BROKEN_LENGTH, 0),
                brokenLastDay = prefs.getLong(BROKEN_LAST_DAY, StreakState.NEVER),
            )
        }

        /** The saved celebration; an unreadable one (a newer app's kind, say) is dropped. */
        fun loadPending(prefs: SharedPreferences): PendingCelebration? {
            if (!prefs.contains(P_DAY)) return null
            val covered = prefs.getString(P_COVERED, "").orEmpty().split(',').filter { it.isNotEmpty() }.map { entry ->
                val (day, kind) = entry.split(':').takeIf { it.size == 2 } ?: return null
                CoveredDay(day.toLongOrNull() ?: return null, DayKind.entries.firstOrNull { it.name == kind } ?: return null)
            }
            return PendingCelebration(
                day = prefs.getLong(P_DAY, StreakState.NEVER),
                length = prefs.getInt(P_LENGTH, 0),
                covered = covered,
                finishedAt = prefs.getLong(P_FINISHED_AT, 0),
                canvas = prefs.getString(P_CANVAS, null) ?: return null,
            )
        }
    }
}
