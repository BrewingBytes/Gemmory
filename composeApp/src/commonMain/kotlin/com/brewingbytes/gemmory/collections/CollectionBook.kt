package com.brewingbytes.gemmory.collections

import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.remote.CollectionProgress
import com.brewingbytes.gemmory.remote.RemoteCollection
import com.brewingbytes.gemmory.remote.RemotePicture

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

/** A [CollectionBook.settle]: [fetched] false on a phone that never had a verified catalog (no rows at all). */
data class Settled(val fetched: Boolean, val statuses: List<CollectionStatus>) {
    companion object {
        val NONE = Settled(false, emptyList())
    }
}

/**
 * The collections as the gallery reads them (T37, iOS Stage 1c T64): Android's `Collections` until it moves to common
 * code with its prefs.
 */
interface CollectionBook {
    /** [pictures] are the gallery's picture summaries: grants what a completion earned, then says where each stands. */
    suspend fun settle(pictures: List<CanvasSummary>): Settled

    /** A completed collection's glint played: never again. */
    fun glintShown(id: String)
}
