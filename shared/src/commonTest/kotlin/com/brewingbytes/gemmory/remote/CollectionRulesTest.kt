package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.remote.MemberState.COMING
import com.brewingbytes.gemmory.remote.MemberState.FINISHED
import com.brewingbytes.gemmory.remote.MemberState.GONE
import com.brewingbytes.gemmory.remote.MemberState.OPEN
import com.brewingbytes.gemmory.remote.RemoteFixtures.grid
import com.brewingbytes.gemmory.remote.RemoteFixtures.picture
import com.brewingbytes.gemmory.policy.ReminderSchedule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

    /** T38 turns events on: the gallery plays them beside the collections, in catalog order. */
    @Test
    fun eventsArePlayed() {
        val event = RemoteCollection("autumn-week", "Autumn Week", listOf("a"), event = true)
        assertEquals(listOf(seaside, event), CollectionRules.playable(RemoteCatalog(1, emptyList(), listOf(seaside, event))))
    }

    private val week = RemoteCollection("golden-week", "Golden Week", (1..7).map { "w$it" }, event = true)

    /** The week's 7 drops on 7 consecutive dates, the first on 2026-11-02, listed out of order. */
    private val weekCatalog = RemoteCatalog(
        1,
        (7 downTo 1).map { picture("w$it", grid(), minAppVersion = 13, availableFrom = "2026-11-0${it + 1}") },
        listOf(week),
    )

    private val firstDay = LocalDate.parse("2026-11-02").toEpochDays()

    @Test
    fun anEventsWindowIsItsFirstDropAndTheSixDaysAfter() {
        assertEquals(firstDay..firstDay + 6, CollectionRules.eventWindow(week, weekCatalog))
        // An ordinary collection, or an event with no dated member, has none: it's just a collection.
        assertNull(CollectionRules.eventWindow(seaside, weekCatalog))
        val undated = RemoteCatalog(1, (1..7).map { picture("w$it", grid()) }, listOf(week))
        assertNull(CollectionRules.eventWindow(week, undated))
    }

    @Test
    fun threeFinishedInTheWindowEarnTheExclusive() {
        val window = CollectionRules.eventWindow(week, weekCatalog)!!
        val days = mapOf("w1" to firstDay, "w2" to firstDay + 1)
        assertEquals(2, CollectionRules.finishedInWindow(week, window) { days[it] })
        assertFalse(CollectionRules.eventEarned(2))
        val three = days + ("w5" to firstDay + 4)
        assertEquals(3, CollectionRules.finishedInWindow(week, window) { three[it] })
        assertTrue(CollectionRules.eventEarned(3))
    }

    /** The window is inclusive local dates: 23:59 on its last day counts, 00:01 the day after doesn't. */
    @Test
    fun aFinishAt2359OnTheLastDayCountsAnd0001TheNextDayDoesNot() {
        val zone = TimeZone.of("Europe/Bucharest")
        val window = CollectionRules.eventWindow(week, weekCatalog)!!
        val lastDay = LocalDate.parse("2026-11-08")
        val at = { d: LocalDate, h: Int, m: Int -> ReminderSchedule.dayOf(LocalDateTime(d, LocalTime(h, m)).toInstant(zone).toEpochMilliseconds(), zone) }
        val two = mapOf("w1" to firstDay, "w2" to firstDay + 1)
        val late = two + ("w7" to at(lastDay, 23, 59))
        assertEquals(3, CollectionRules.finishedInWindow(week, window) { late[it] })
        val tooLate = two + ("w7" to at(LocalDate.parse("2026-11-09"), 0, 1))
        assertEquals(2, CollectionRules.finishedInWindow(week, window) { tooLate[it] })
    }

    /** Eng outside voice #15: a finish without a `play` date (before T35) counts for collections, not for events. */
    @Test
    fun aFinishWithoutADateDoesNotCountForAnEvent() {
        val window = CollectionRules.eventWindow(week, weekCatalog)!!
        val days = mapOf("w1" to firstDay, "w2" to firstDay + 1, "w3" to null)
        assertEquals(2, CollectionRules.finishedInWindow(week, window) { days[it] })
    }

    @Test
    fun aPicturesEventIdIsTheNewestEventListingIt() {
        assertEquals("golden-week", CollectionRules.eventOf("w3", listOf(seaside, week)))
        assertNull(CollectionRules.eventOf("a", listOf(seaside, week)))
        // R1: collection_id is unchanged, so an event member's names the event too (founder 2026-10-20).
        assertEquals("golden-week", CollectionRules.collectionOf("w3", listOf(seaside, week)))
    }

    private val older = RemoteCollection("forest", "Forest", listOf("d", "e"))
    private val newest = RemoteCollection("night", "Night", listOf("f"))

    private fun rows(
        list: List<CollectionProgress>,
        completed: Map<String, Long> = emptyMap(),
        glints: Set<String> = emptySet(),
        anyFinished: Boolean = true,
        largeFont: Boolean = false,
        inWindow: Set<String> = emptySet(),
    ) = CollectionRules.rows(list, completed, glints, TODAY, anyFinished, largeFont, inWindow)

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

    private val autumn = RemoteCollection("autumn-week", "Autumn Week", listOf("g", "h"), event = true)
    private val event get() = progress(FINISHED, COMING, c = autumn)

    /** T38: the event in its window comes first, then the newest other incomplete collection. */
    @Test
    fun theEventInItsWindowIsTheFirstRow() {
        // The event is listed before night, so night is the newer collection; the window still puts the event first.
        val list = listOf(forest, event, night)
        assertEquals(listOf("autumn-week", "night"), rows(list, inWindow = setOf("autumn-week")).rows)
        // Above 1.5× font it's the one row (D4).
        assertEquals(listOf("autumn-week"), rows(list, inWindow = setOf("autumn-week"), largeFont = true).rows)
    }

    /** D7: a reward return's row is pinned for the visit, ahead of the event, even at large font. */
    @Test
    fun aPinnedGlintStillComesBeforeTheEvent() {
        val list = listOf(forest, event, night)
        val completed = mapOf("forest" to TODAY)
        assertEquals(listOf("forest", "autumn-week"), rows(list, completed, glints = setOf("forest"), inWindow = setOf("autumn-week")).rows)
        assertEquals(listOf("forest"), rows(list, completed, glints = setOf("forest"), inWindow = setOf("autumn-week"), largeFont = true).rows)
        // The event's own earn is pinned too, before completion (its glint plays at the 3rd).
        assertEquals(listOf("autumn-week", "night"), rows(list, glints = setOf("autumn-week"), inWindow = setOf("autumn-week")).rows)
    }

    /** After its window the event is an ordinary collection: the newest incomplete one leads. */
    @Test
    fun afterItsWindowTheEventRanksAsACollection() {
        assertEquals(listOf("night"), rows(listOf(forest, event, night)).rows)
        assertEquals(listOf("autumn-week"), rows(listOf(forest, night, event)).rows)
    }

    /** R1: with no event in its window, T37's rows are unchanged. */
    @Test
    fun withNoEventInItsWindowTheRowsAreT37s() {
        val list = listOf(forest, sea, night)
        val completed = mapOf("seaside" to TODAY - 6)
        assertEquals(listOf("night", "seaside"), rows(list, completed, inWindow = emptySet()).rows)
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
