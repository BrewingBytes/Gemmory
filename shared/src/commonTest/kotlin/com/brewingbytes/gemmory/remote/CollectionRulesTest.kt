package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.remote.MemberState.COMING
import com.brewingbytes.gemmory.remote.MemberState.FINISHED
import com.brewingbytes.gemmory.remote.MemberState.GONE
import com.brewingbytes.gemmory.remote.MemberState.OPEN
import com.brewingbytes.gemmory.remote.RemoteFixtures.grid
import com.brewingbytes.gemmory.remote.RemoteFixtures.picture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionRulesTest {
    private val seaside = RemoteCollection("seaside", "Seaside", listOf("a", "b", "c"))

    private fun progress(vararg states: MemberState, c: RemoteCollection = seaside) =
        CollectionRules.progress(c) { states[c.members.indexOf(it)] }

    @Test
    fun aMembersStateFollowsFinishedThenShownThenTheCatalog() {
        assertEquals(FINISHED, CollectionRules.memberState(finished = true, started = true, shows = false, inCatalog = false))
        assertEquals(OPEN, CollectionRules.memberState(finished = false, started = false, shows = true, inCatalog = true))
        // Withdrawn but started: it stays (the withdraw rule).
        assertEquals(OPEN, CollectionRules.memberState(finished = false, started = true, shows = false, inCatalog = false))
        assertEquals(COMING, CollectionRules.memberState(finished = false, started = false, shows = false, inCatalog = true))
        assertEquals(GONE, CollectionRules.memberState(finished = false, started = false, shows = false, inCatalog = false))
    }

    @Test
    fun everyMemberFinishedCompletesIt() {
        val p = progress(FINISHED, FINISHED, FINISHED)
        assertTrue(p.complete)
        assertEquals(3, p.size)
        assertEquals(listOf("a", "b", "c"), p.shown)
    }

    @Test
    fun aComingMemberCountsAndBlocksCompletion() {
        val p = progress(FINISHED, FINISHED, COMING)
        assertFalse(p.complete)
        assertEquals(3, p.size)
        assertEquals(1, p.coming)
        assertEquals(listOf("a", "b"), p.shown)
    }

    @Test
    fun aWithdrawnMemberNeverStartedLeavesTheCollectionAndItsSize() {
        val p = progress(FINISHED, GONE, FINISHED)
        assertEquals(2, p.size)
        assertTrue(p.complete)
    }

    @Test
    fun aWithdrawnMemberStartedStays() {
        // The app reads a started withdrawn member as OPEN: it still counts and still has to be finished.
        val p = progress(FINISHED, OPEN, FINISHED)
        assertEquals(3, p.size)
        assertFalse(p.complete)
    }

    @Test
    fun aCollectionWithEveryMemberGoneNeverCompletes() {
        assertFalse(progress(GONE, GONE, GONE).complete)
    }

    /** A finish from before T35 has no `play` date; the finished state alone counts. */
    @Test
    fun aFinishWithoutADateCounts() {
        val finishedBeforeT35 = CollectionRules.memberState(finished = true, started = true, shows = true, inCatalog = true)
        assertTrue(progress(finishedBeforeT35, FINISHED, FINISHED).complete)
    }

    @Test
    fun theKeepsakeIsTheEntryNamingTheCollectionAndHiddenUntilEarned() {
        val keepsake = picture("harbour-lights", grid(), minAppVersion = RemoteCatalogs.REWARDS_MIN_APP, rewardOf = "seaside")
        val plain = picture("harbor", grid())
        val catalog = RemoteCatalog(1, listOf(plain, keepsake), listOf(seaside))
        assertEquals(keepsake, CollectionRules.rewardFor("seaside", catalog))
        assertNull(CollectionRules.rewardFor("other", catalog))
        assertEquals(setOf("harbour-lights"), CollectionRules.rewardIds(catalog))
        assertFalse(CollectionRules.rewardShows(keepsake, emptySet()))
        assertTrue(CollectionRules.rewardShows(keepsake, setOf("harbour-lights")))
        assertTrue(CollectionRules.rewardShows(plain, emptySet()))
    }

    /** Until T38 gives events their window, this build leaves them out. */
    @Test
    fun eventsAreHeldBack() {
        val event = RemoteCollection("autumn-week", "Autumn Week", listOf("a"), event = true)
        assertEquals(listOf(seaside), CollectionRules.playable(RemoteCatalog(1, emptyList(), listOf(seaside, event))))
    }

    private val older = RemoteCollection("forest", "Forest", listOf("d", "e"))
    private val newest = RemoteCollection("night", "Night", listOf("f"))

    private fun rows(
        list: List<CollectionProgress>,
        completed: Map<String, Long> = emptyMap(),
        glints: Set<String> = emptySet(),
        anyFinished: Boolean = true,
        largeFont: Boolean = false,
    ) = CollectionRules.rows(list, completed, glints, TODAY, anyFinished, largeFont)

    private val forest get() = progress(OPEN, OPEN, c = older)
    private val sea get() = progress(OPEN, OPEN, OPEN)
    private val night get() = progress(OPEN, c = newest)

    /** D5: a new player's first screen keeps the starter in view. */
    @Test
    fun noRowsAndNoLinkBeforeTheFirstFinish() {
        assertEquals(CollectionRowPick.NONE, rows(listOf(forest, sea), anyFinished = false))
        assertEquals(CollectionRowPick.NONE, rows(emptyList()))
    }

    @Test
    fun theNewestIncompleteCollectionShowsAndTheRestAreInTheSheet() {
        assertEquals(CollectionRowPick(listOf("night"), showAll = true), rows(listOf(forest, sea, night)))
        assertEquals(CollectionRowPick(listOf("night"), showAll = false), rows(listOf(night)))
    }

    @Test
    fun aCompletedOneStaysSevenDaysAsTheSecondRow() {
        val list = listOf(forest, sea, night)
        assertEquals(listOf("night", "seaside"), rows(list, completed = mapOf("seaside" to TODAY - 6)).rows)
        assertEquals(listOf("night"), rows(list, completed = mapOf("seaside" to TODAY - 7)).rows)
        // A complete one puts the link up, even when it's on a row.
        assertTrue(rows(listOf(sea), completed = mapOf("seaside" to TODAY)).showAll)
    }

    /** D4: one row above 1.5× font; the other goes to the sheet. */
    @Test
    fun oneRowAtLargeFont() {
        val pick = rows(listOf(forest, sea, night), completed = mapOf("seaside" to TODAY), largeFont = true)
        assertEquals(listOf("night"), pick.rows)
        assertTrue(pick.showAll)
    }

    /** D7: the reward return's collection takes a row for this visit, at any font size, so its glint can play. */
    @Test
    fun aCompletionWithItsGlintWaitingIsPinnedFirst() {
        val list = listOf(forest, sea, night)
        val completed = mapOf("seaside" to TODAY - 10)
        assertEquals(listOf("seaside", "night"), rows(list, completed, glints = setOf("seaside")).rows)
        assertEquals(listOf("seaside"), rows(list, completed, glints = setOf("seaside"), largeFont = true).rows)
    }

    /** Recorded complete stays complete after a Play again makes a member unfinished. */
    @Test
    fun aRecordedCompletionStaysComplete() {
        val replayed = progress(FINISHED, OPEN, FINISHED)
        assertEquals(listOf("forest"), rows(listOf(forest, replayed), completed = mapOf("seaside" to TODAY - 20)).rows)
    }

    @Test
    fun theSheetIsNewestFirstWithCompleteOnesLast() {
        assertEquals(listOf("night", "forest", "seaside"), CollectionRules.sheet(listOf(forest, sea, night), setOf("seaside")))
        assertEquals(listOf("night", "seaside"), CollectionRules.sheet(listOf(progress(GONE, GONE, c = older), sea, night), emptySet()))
    }

    @Test
    fun aPicturesCollectionIdIsTheNewestListingIt() {
        val again = RemoteCollection("best-of", "Best of", listOf("a"))
        assertEquals("best-of", CollectionRules.collectionOf("a", listOf(seaside, older, again)))
        assertEquals("forest", CollectionRules.collectionOf("d", listOf(seaside, older, again)))
        assertNull(CollectionRules.collectionOf("z", listOf(seaside)))
    }

    private companion object {
        const val TODAY = 20_400L
    }
}
