package com.brewingbytes.gemmory.achievements

import android.app.Activity
import android.content.Intent
import com.brewingbytes.gemmory.policy.Achievement
import com.brewingbytes.gemmory.policy.AchievementLedger
import com.brewingbytes.gemmory.policy.AchievementState
import com.brewingbytes.gemmory.policy.GameEvent
import com.brewingbytes.gemmory.policy.StateStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How a tap on the Settings row ended. */
enum class OpenResult {
    /** Play Games' achievements screen is opening. */
    OPENED,

    /** The player declined the sign-in: nothing is shown. */
    DECLINED,

    /** "Couldn't reach Play Games. Try again." */
    FAILED,
}

/**
 * Play Games achievements (#49). Game events are counted at once in the [AchievementLedger] (a file, so they survive
 * process death and offline play); [sync] sends what Play Games hasn't confirmed, and is called only while the
 * Gallery or Settings shows, so Play Games' own "Achievement unlocked" pop-up never lands on the canvas or the
 * completion screen. Ledger work is serialized by [ledgerLock], held only on the phone (the one-time backfill runs
 * before any event is counted); Play Games calls run outside it, one round at a time under [sending], so a slow or
 * hung call never holds up counting.
 */
class Achievements(
    store: StateStore<AchievementState>,
    private val backend: GamesBackend,
    /** An achievement's Play Console id (res/values/games-ids.xml); blank until it's set up, and then it's kept. */
    private val ids: (Achievement) -> String,
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
) {
    private val ledger = AchievementLedger(store)
    private val ledgerLock = Mutex()
    private val sending = Mutex()
    private val _changes = MutableStateFlow(0)

    /** Ticks whenever something new was earned, so a Gallery that's showing sends it. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** The Settings row shows only when Play Games can run here. */
    val available: Boolean get() = backend.available

    /** Counts [event]. Never blocks the caller. */
    fun record(event: GameEvent): Job = scope.launch(worker) {
        val earned = ledgerLock.withLock { ledger.record(event) }
        if (earned) _changes.value++
    }

    /** Play Games just started (T45): a Gallery or Settings that's showing sends what waited in the ledger. */
    fun resend() {
        _changes.value++
    }

    /**
     * The one-time count from the phone (founder, 2026-10-18). Call it first, at app start: [read] runs only when it
     * never ran, and events wait for it.
     */
    fun backfill(read: suspend () -> Pair<List<GameEvent.Finished>, Int>): Job = scope.launch(worker) {
        val earned = ledgerLock.withLock {
            if (ledger.state.backfilled) return@withLock false
            val (finished, streak) = read()
            ledger.backfill(finished, streak)
        }
        if (earned) _changes.value++
    }

    /**
     * Sends everything earned but not confirmed, for the signed-in player; silent when nobody is signed in or Play
     * Games can't be reached. Cancelling it (the Gallery left) keeps the rest for next time.
     */
    suspend fun sync(activity: Activity) {
        if (backend.available) sync(backend.session(activity))
    }

    /** [sync] on an open session (tests use a fake one). */
    suspend fun sync(session: GamesSession) {
        withContext(worker) { send(session, session.player() ?: return@withContext) }
    }

    /**
     * The Settings row: signs in if needed (the only sign-in the player can ask for), sends what's pending, then
     * returns Play Games' achievements screen to start, or why not.
     */
    suspend fun open(activity: Activity): Pair<OpenResult, Intent?> = open(backend.session(activity))

    /** [open] on an open session (tests use a fake one). */
    suspend fun open(session: GamesSession): Pair<OpenResult, Intent?> {
        val player = session.player() ?: when (val signIn = session.signIn()) {
            is SignIn.SignedIn -> signIn.player
            SignIn.Declined -> return OpenResult.DECLINED to null
            SignIn.Failed -> return OpenResult.FAILED to null
        }
        withContext(worker) { send(session, player) }
        val intent = session.achievementsIntent() ?: return OpenResult.FAILED to null
        return OpenResult.OPENED to intent
    }

    /** Every finish counted on this phone, the one-time backfill included: the review prompt's thresholds (T39). */
    val finishes: Int get() = ledger.state.finishes

    /** The ledger as it stands (tests). */
    val state: AchievementState get() = ledger.state

    private suspend fun send(session: GamesSession, player: String) = sending.withLock {
        for (pending in ledgerLock.withLock { ledger.pendingFor(player) }) {
            val id = ids(pending.achievement).takeIf { it.isNotBlank() } ?: continue
            val sent = if (pending.achievement.incremental) {
                session.setSteps(id, pending.steps)
            } else {
                session.unlock(id)
            }
            // One failure (offline, say) ends this round: the rest would fail the same way.
            if (!sent) return@withLock
            ledgerLock.withLock { ledger.confirm(player, pending) }
        }
    }
}
