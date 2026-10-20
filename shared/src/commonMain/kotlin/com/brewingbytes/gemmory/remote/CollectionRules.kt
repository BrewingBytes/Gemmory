package com.brewingbytes.gemmory.remote

/** Where one collection member stands on this phone (T37). */
enum class MemberState {
    /** Finished, whatever its date: one finished before T35 has no `play` date and still counts. */
    FINISHED,

    /** It shows in the gallery (listed, bundled, or kept by the withdraw rule) and isn't finished. */
    OPEN,

    /** In the catalog but not on the gallery yet (its date hasn't come, or its grid isn't stored): "more coming". */
    COMING,

    /** Neither in the catalog nor started: withdrawn, or for a newer app. It leaves the collection and its size. */
    GONE,
}

/**
 * One collection's progress (T37). [shown] are the members on the phone, in the collection's order; [coming] the ones
 * still to come. The size counts both, never the gone ones.
 */
data class CollectionProgress(val collection: RemoteCollection, val shown: List<String>, val finished: Int, val coming: Int) {
    val id: String get() = collection.id
    val size: Int get() = shown.size + coming

    /** Every member left is finished and none is still to come. An empty collection never completes. */
    val complete: Boolean get() = size > 0 && coming == 0 && finished == size
}

/** The gallery's collection rows (T37): [rows] above the grid, and whether the "All collections" link shows. */
data class CollectionRowPick(val rows: List<String>, val showAll: Boolean) {
    companion object {
        val NONE = CollectionRowPick(emptyList(), false)
    }
}

/**
 * The collection rules (T37, eng D7; T38 adds the event's window here), pure so they're unit-tested. The app finds each
 * member's [MemberState]; this decides progress, completion, the keepsake and which rows show.
 */
object CollectionRules {
    /** A completed collection keeps its row this many days after its completion, then leaves (design system). */
    const val COMPLETE_STAYS_DAYS = 7

    /** At most this many rows above the grid; one above 1.5× font scale (plan-design-review D4). */
    const val MAX_ROWS = 2

    /**
     * A member's state. [shows]: listed with its grid stored and its date come, or bundled. [started]: a gem placed, so
     * a withdrawn one stays (the withdraw rule). [inCatalog]: listed in the catalog at all, hidden or not.
     */
    fun memberState(finished: Boolean, started: Boolean, shows: Boolean, inCatalog: Boolean): MemberState = when {
        finished -> MemberState.FINISHED
        shows || started -> MemberState.OPEN
        inCatalog -> MemberState.COMING
        else -> MemberState.GONE
    }

    fun progress(collection: RemoteCollection, stateOf: (String) -> MemberState): CollectionProgress {
        val states = collection.members.map { it to stateOf(it) }
        val shown = states.filter { it.second == MemberState.FINISHED || it.second == MemberState.OPEN }.map { it.first }
        return CollectionProgress(
            collection,
            shown,
            finished = states.count { it.second == MemberState.FINISHED },
            coming = states.count { it.second == MemberState.COMING },
        )
    }

    /**
     * The collections T37 plays: an event (T38) is parsed but held back until T38 gives it its window, so this build
     * never reads one as an ordinary collection.
     */
    fun playable(catalog: RemoteCatalog): List<RemoteCollection> = catalog.collections.filterNot { it.event }

    /** [collectionId]'s keepsake: the catalog picture whose `rewardOf` names it. Null when the catalog has none. */
    fun rewardFor(collectionId: String, catalog: RemoteCatalog): RemotePicture? = catalog.pictures.firstOrNull { it.rewardOf == collectionId }

    /** Every keepsake id in [catalog]: hidden from the gallery until earned. */
    fun rewardIds(catalog: RemoteCatalog): Set<String> = catalog.pictures.filter { it.rewardOf != null }.map { it.id }.toSet()

    /** The keepsake [id] shows: it isn't one, or it was earned (its id is in the unlocks). */
    fun rewardShows(picture: RemotePicture, earned: Set<String>): Boolean = picture.rewardOf == null || picture.id in earned

    /**
     * Which rows show (design system, "How many"). [progress] is in catalog order (the last is the newest).
     * [completedDay] is each completed collection's local epoch day: one recorded complete stays complete after a Play
     * again. [glintPending] are the completed ones whose glint hasn't played. No rows before the player's first finished picture ([anyFinished], D5). The newest one with a
     * glint waiting is pinned first for this visit, at any font size (D7); then the newest incomplete one; then those
     * completed in the last 7 days, newest first. Two rows, or one when [largeFont] (D4) beside a pinned one.
     */
    fun rows(
        progress: List<CollectionProgress>,
        completedDay: Map<String, Long>,
        glintPending: Set<String>,
        today: Long,
        anyFinished: Boolean,
        largeFont: Boolean,
    ): CollectionRowPick {
        val live = progress.filter { it.size > 0 }
        if (!anyFinished || live.isEmpty()) return CollectionRowPick.NONE
        val newestFirst = live.asReversed()
        val complete = { p: CollectionProgress -> p.id in completedDay }
        val pinned = newestFirst.firstOrNull { complete(it) && it.id in glintPending }
        val incomplete = newestFirst.firstOrNull { !complete(it) }
        val recent = newestFirst.filter { p -> completedDay[p.id]?.let { today - it in 0 until COMPLETE_STAYS_DAYS } == true }
        val ordered = (listOfNotNull(pinned, incomplete) + recent).distinctBy { it.id }
        val rows = ordered.take(if (largeFont) 1 else MAX_ROWS).map { it.id }
        val showAll = live.any { it.id !in rows || complete(it) }
        return CollectionRowPick(rows, showAll)
    }

    /** The All collections sheet: every collection with members, newest first, complete ones ([completed]) last. */
    fun sheet(progress: List<CollectionProgress>, completed: Set<String>): List<String> =
        progress.filter { it.size > 0 }.asReversed().sortedBy { it.id in completed }.map { it.id }

    /** The collection a picture's canvas events name (T37 `collection_id`): the newest one listing it, or null. */
    fun collectionOf(pictureId: String, collections: List<RemoteCollection>): String? =
        collections.lastOrNull { pictureId in it.members }?.id
}
