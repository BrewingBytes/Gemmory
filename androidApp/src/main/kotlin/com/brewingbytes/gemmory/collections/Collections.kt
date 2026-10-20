package com.brewingbytes.gemmory.collections

import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.play.KeepResult
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.remote.CollectionProgress
import com.brewingbytes.gemmory.remote.CollectionRules
import com.brewingbytes.gemmory.remote.MemberState
import com.brewingbytes.gemmory.remote.RemoteCatalog
import com.brewingbytes.gemmory.remote.RemoteCollection
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a completed collection's keepsake stands (design system, row states). */
enum class Keepsake {
    /** Not complete, or the catalog names no keepsake for it. */
    NONE,

    /** In its canvas: it shows in the row's strip and the grid. */
    ADDED,

    /** Earned, but its grid isn't on the phone yet: it comes with the next successful check. */
    PENDING_OFFLINE,

    /** Earned, but the copy failed for lack of space: retried on each gallery visit. */
    PENDING_SPACE,
}

/**
 * One collection as the gallery shows it (T37). [completedDay] is set once it's complete (it stays complete after a
 * Play again); [keepsake] is its catalog keepsake (an event's only once earned), shown once [keepsakeState] is
 * [Keepsake.ADDED].
 * [event]: a weekly event with a window (T38), [inWindow] today, [earned] its exclusive with 3 finishes in the window.
 */
data class CollectionStatus(
    val collection: RemoteCollection,
    val progress: CollectionProgress,
    val completedDay: Long?,
    val vipAtCompletion: Boolean,
    val keepsake: RemotePicture?,
    val keepsakeState: Keepsake,
    val glintPending: Boolean,
    val event: Boolean = false,
    val inWindow: Boolean = false,
    val earned: Boolean = false,
) {
    val id: String get() = collection.id
    val complete: Boolean get() = completedDay != null
}

/** A [Collections.settle]: [fetched] false on a phone that never had a verified catalog (no rows at all). */
data class Settled(val fetched: Boolean, val statuses: List<CollectionStatus>) {
    companion object {
        val NONE = Settled(false, emptyList())
    }
}

/**
 * Collections on this phone (T37, eng D7, CEO C6): works out each one's progress from the gallery's canvases and the
 * verified catalog (the cached one when offline), and grants a completed one's keepsake and token. A weekly event
 * (T38) grants its keepsake for 3 finishes in its window instead, and only the token for all 7. Every step is
 * idempotent and runs again on each [settle], so a crash or a failed copy resumes on the next visit and nothing is
 * given twice: the completion is written first, the keepsake's unlock is a set, the token is a one-time earn kept in
 * the inventory's own file, and the copy skips a canvas already there.
 */
class Collections(
    private val prefs: CollectionPrefs,
    private val catalog: () -> RemoteCatalog?,
    private val vip: () -> Boolean,
    /** Adds a keepsake's id to the picture unlocks (backed up and transferred). */
    private val unlock: (String) -> Unit,
    /** Adds 1 unlock token once for the id; true when it was added now. */
    private val earnToken: suspend (String) -> Boolean,
    /** Copies a keepsake's verified grid into its canvas. */
    private val keep: suspend (String) -> KeepResult,
    /** A keepsake was copied in now: New from today (plan-design-review D25). */
    private val keptToday: (String) -> Unit,
    private val today: () -> Long,
    private val analytics: Analytics = Analytics.None,
) {
    private val mutex = Mutex()

    /** Collections completed in this process whose return to the gallery hasn't been read yet (D7). */
    private val rewardReturns = mutableSetOf<String>()

    /** [pictures] are the gallery's picture summaries (CanvasStore.summaries): what's on the phone and how far. */
    suspend fun settle(pictures: List<CanvasSummary>): Settled = mutex.withLock {
        val catalog = catalog() ?: return Settled.NONE
        val byId = pictures.associateBy { it.ref.id }
        val inCatalog = catalog.pictures.map { it.id }.toSet()
        val statuses = CollectionRules.playable(catalog).map { c ->
            val progress = CollectionRules.progress(c) { id -> stateOf(byId[id], id in inCatalog) }
            var completion = prefs.completion(c.id)
            if (completion == null && progress.complete) {
                val atVip = vip()
                prefs.markComplete(c.id, today(), atVip)
                completion = CollectionPrefs.Completion(today(), atVip)
                rewardReturns += c.id
                analytics.log(AnalyticsEvent.collectionCompleted(c.id))
            }
            // An event (T38) earns its keepsake with 3 finishes in its window, never by completing all 7.
            val window = CollectionRules.eventWindow(c, catalog)
            var earned = window != null && prefs.earned(c.id) != null
            if (window != null && !earned) {
                val count = CollectionRules.finishedInWindow(c, window) { id -> (byId[id] as? CanvasSummary.Playable)?.firstFinishedDay }
                if (CollectionRules.eventEarned(count)) {
                    prefs.markEarned(c.id, today())
                    earned = true
                    rewardReturns += c.id
                    analytics.log(AnalyticsEvent.eventRewardUnlocked(c.id, count))
                }
            }
            if (completion != null) {
                // VIP earns the picture only (CEO C6); one who had VIP at the completion never gets the token later.
                if (!completion.vip && earnToken(TOKEN_PREFIX + c.id)) {
                    analytics.log(AnalyticsEvent.inventoryEarned(Item.UNLOCK_TOKEN, EarnSource.COLLECTION, 1))
                }
            }
            // A collection's row names its keepsake from the start; an event's exclusive stays unnamed until earned (C3).
            val reward = CollectionRules.rewardFor(c.id, catalog)?.takeIf { window == null || earned }
            val granted = if (window != null) earned else completion != null
            var state = Keepsake.NONE
            if (reward != null && granted) {
                unlock(reward.id)
                state = when (keep(reward.id)) {
                    KeepResult.CREATED -> Keepsake.ADDED.also { keptToday(reward.id) }
                    KeepResult.PRESENT -> Keepsake.ADDED
                    KeepResult.NO_SPACE -> Keepsake.PENDING_SPACE
                    KeepResult.MISSING, KeepResult.FAILED -> Keepsake.PENDING_OFFLINE
                }
            }
            // The glint waits for the keepsake picture (design system, row states); an event's plays at its 3rd.
            val glint = (completion != null || earned) && !prefs.glintShown(c.id) && (reward == null || !granted || state == Keepsake.ADDED)
            CollectionStatus(
                c,
                progress,
                completion?.day,
                completion?.vip == true,
                reward,
                state,
                glint,
                event = window != null,
                inWindow = window != null && today() in window,
                earned = earned,
            )
        }
        Settled(true, statuses)
    }

    /**
     * Back on the gallery from [pictureId]'s fresh completion (D7): true when that finish completed a collection or
     * earned an event's keepsake (T38), so this return shows no interstitial, reminder ask or review. Read once.
     */
    suspend fun takeRewardReturn(pictureId: String): Boolean = mutex.withLock {
        val ids = catalog()?.collections.orEmpty().filter { it.id in rewardReturns && pictureId in it.members }.map { it.id }
        rewardReturns.removeAll(ids.toSet())
        ids.isNotEmpty()
    }

    /** The glint played (or reduced motion showed the line at once): it never plays again for [id]. */
    fun glintShown(id: String) = prefs.setGlintShown(id)

    private fun stateOf(summary: CanvasSummary?, inCatalog: Boolean): MemberState {
        val started = when (summary) {
            is CanvasSummary.Playable -> summary.state.placedCount > 0
            is CanvasSummary.Damaged -> true
            else -> false
        }
        val finished = (summary as? CanvasSummary.Playable)?.state?.isFinished == true
        return CollectionRules.memberState(finished, started, shows = summary != null, inCatalog = inCatalog)
    }

    companion object {
        const val TOKEN_PREFIX = "collection-"
    }
}
