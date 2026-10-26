package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T46 (eng O-E7/8): a fresh install is one with none of its played state on the disk, then a marker. */
class NewPlayerTest {
    private val empty = NewPlayer.Disk(canvas = false, achievements = false, streak = false, marker = false)

    private class MemoryStore : CheckedStateStore<FirstRunState> {
        var saved: FirstRunState? = null

        /** A full disk: writes don't land. */
        var failing = false

        override fun load() = saved

        override fun trySave(value: FirstRunState): Boolean {
            if (failing) return false
            saved = value
            return true
        }
    }

    private fun begun(): Pair<MemoryStore, FirstRunFunnel> {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        funnel.begin(1_000)
        return store to funnel
    }

    @Test
    fun anEmptyDiskIsFreshAndAnyOfTheFourIsNot() {
        assertTrue(NewPlayer.isFresh(empty), "a new install, or an 0.8.1 install never opened before the update")
        assertFalse(NewPlayer.isFresh(empty.copy(canvas = true)))
        assertFalse(NewPlayer.isFresh(empty.copy(achievements = true)))
        assertFalse(NewPlayer.isFresh(empty.copy(streak = true)))
        assertFalse(NewPlayer.isFresh(empty.copy(marker = true)), "a second launch")
        assertFalse(NewPlayer.isFresh(NewPlayer.Disk(canvas = true, achievements = true, streak = true, marker = true)), "a restored phone")
    }

    @Test
    fun firstLaunchAndFirstGemAreLoggedOnceAcrossRelaunches() {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        assertFalse(funnel.fresh)
        assertFalse(funnel.launchDue(), "nothing before the first foreground")
        assertNull(funnel.gemPlaced(5_000))
        funnel.begin(1_000)
        assertTrue(funnel.fresh)
        funnel.begin(9_000)
        assertEquals(1_000, store.saved?.firstLaunchMillis, "the clock starts once")
        assertTrue(funnel.launchDue())
        assertFalse(funnel.launchDue())
        assertEquals(41_000, funnel.gemPlaced(42_000))
        assertNull(funnel.gemPlaced(43_000))
        val relaunched = FirstRunFunnel(store)
        assertTrue(relaunched.fresh)
        assertFalse(relaunched.launchDue())
        assertNull(relaunched.gemPlaced(99_000))
    }

    @Test
    fun aFirstLaunchNotLoggedBeforeTheProcessDiedIsLoggedNextTime() {
        val store = MemoryStore()
        FirstRunFunnel(store).begin(1_000)
        val next = FirstRunFunnel(store)
        assertEquals(500, next.gemPlaced(1_500), "the first gem can come before consent has settled")
        assertTrue(next.launchDue(), "first_launch still logs once consent settles")
    }

    @Test
    fun aClockSetBackNeverGivesANegativeTime() {
        val funnel = FirstRunFunnel(MemoryStore())
        funnel.begin(10_000)
        assertEquals(0, funnel.gemPlaced(4_000))
    }

    @Test
    fun aStoreWithoutAFunnelIsNeverFresh() {
        val funnel = FirstRunFunnel(MemoryStore())
        assertFalse(funnel.referrerDue(freshDisk = false), "an upgrader or a restored phone reads no referrer")
        assertFalse(funnel.launchDue())
        assertNull(funnel.gemPlaced(1))
    }

    @Test
    fun theReferrerIsReadOnceAndKeptWhenItEndsBeforeTheFirstForeground() {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        assertTrue(funnel.referrerDue(freshDisk = true))
        funnel.referrerRead("utm_source=share")
        assertFalse(funnel.referrerDue(freshDisk = true))
        assertNull(store.saved, "nothing written before the first foreground")
        funnel.begin(1_000)
        assertEquals(FirstRunState(1_000, referrerRead = true, referrerRaw = "utm_source=share"), store.saved)
        assertFalse(FirstRunFunnel(store).referrerDue(freshDisk = false))
        assertEquals("utm_source=share", FirstRunFunnel(store).referrer)
    }

    @Test
    fun aReadStillGoingAtTheFirstForegroundIsSavedWhenItEnds() {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        assertTrue(funnel.referrerDue(freshDisk = true))
        funnel.begin(1_000)
        assertTrue(funnel.referrerDue(freshDisk = false))
        funnel.referrerRead(null)
        assertEquals(FirstRunState(1_000, referrerRead = true), store.saved, "read once, even when Play gave nothing")
        funnel.referrerRead("late")
        assertNull(store.saved?.referrerRaw)
    }

    @Test
    fun aLandingKeepsItsPictureFreeThatDayAndDropsTheReferrer() {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        funnel.begin(1_000)
        funnel.referrerRead("utm_source=share&utm_content=fox-in-snow")
        assertFalse(funnel.landed)
        funnel.landingEnded("fox-in-snow", day = 20_000)
        val reread = FirstRunFunnel(store)
        assertTrue(reread.landed)
        assertEquals("fox-in-snow", reread.sharedFree(today = 20_000))
        assertNull(reread.sharedFree(today = 20_001), "free until local midnight, then it follows its lock")
        assertNull(reread.referrer, "the raw referrer is dropped once the landing has used it")
    }

    @Test
    fun aLandingThatOpenedNothingOnlyDropsTheReferrer() {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        funnel.begin(1_000)
        funnel.referrerRead("utm_content=crafted")
        funnel.landingEnded(null, day = 20_000)
        assertFalse(funnel.landed)
        assertNull(funnel.sharedFree(20_000))
        assertNull(funnel.referrer)
    }

    @Test
    fun aReadEndingAfterTheLandingIsNotKept() {
        val store = MemoryStore()
        val funnel = FirstRunFunnel(store)
        funnel.begin(1_000)
        funnel.landingEnded(null, day = 20_000) // the player left the gallery before the 2 s read ended
        funnel.referrerRead("utm_content=fox-in-snow")
        assertNull(funnel.referrer)
        assertTrue(store.saved!!.referrerRead, "still read once")
    }

    // T56 (#157): "Where did you find Gemmory?" at a first-run install's first fresh finish, once.

    @Test
    fun theFirstFreshFinishOfAFirstRunInstallAsksOnceAndSavesFirst() {
        val (store, funnel) = begun()
        val ask = funnel.firstRunAsk(analyticsGranted = true, seed = 7, firstFinish = true)
        assertEquals(FirstRunAsk.Show(WhereFoundAnswer.order(7)), ask)
        assertEquals(WhereFound.DECIDED, store.saved?.whereFound, "saved before anything shows")
        assertEquals(7, store.saved?.whereFoundSeed)
        assertEquals(FirstRunAsk.No, funnel.firstRunAsk(analyticsGranted = true, seed = 8, firstFinish = true), "a second finish never asks")
        assertEquals(FirstRunAsk.No, FirstRunFunnel(store).firstRunAsk(analyticsGranted = true, seed = 9, firstFinish = true), "nor after a relaunch")
    }

    @Test
    fun anInstallThatIsNotFirstRunIsNeverAsked() {
        val store = MemoryStore()
        assertEquals(FirstRunAsk.No, FirstRunFunnel(store).firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true))
        assertNull(store.saved, "an upgrader or a restored phone writes nothing")
    }

    @Test
    fun consentDeniedAtTheFirstFinishIsNotShownAndGrantingItLaterNeverAsks() {
        val (store, funnel) = begun()
        assertEquals(FirstRunAsk.No, funnel.firstRunAsk(analyticsGranted = false, seed = 1, firstFinish = true))
        assertEquals(WhereFound.NOT_SHOWN, store.saved?.whereFound)
        assertEquals(FirstRunAsk.No, FirstRunFunnel(store).firstRunAsk(analyticsGranted = true, seed = 2, firstFinish = true), "only the first finish counts")
    }

    @Test
    fun aDecisionThatDoesNotSaveShowsNothingAndIsNotRetried() {
        val (store, funnel) = begun()
        store.failing = true
        assertEquals(FirstRunAsk.SaveFailed, funnel.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true))
        assertEquals(FirstRunAsk.No, funnel.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true), "not retried in this process")
        store.failing = false
        funnel.gemPlaced(2_000)
        assertEquals(WhereFound.NOT_SHOWN, store.saved?.whereFound, "a later save of the funnel records what happened")
    }

    @Test
    fun anAnswerCountsOnceAndLeavingBeforeTheRowShowsStaysDecided() {
        val (store, funnel) = begun()
        funnel.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true)
        assertEquals(WhereFound.DECIDED, store.saved?.whereFound, "left mid-celebration")
        funnel.whereFoundShown()
        assertEquals(WhereFound.SHOWN, store.saved?.whereFound)
        assertTrue(funnel.whereFoundAnswered(WhereFoundAnswer.FRIEND))
        assertFalse(funnel.whereFoundAnswered(WhereFoundAnswer.VIDEO), "a second tap does nothing")
        assertEquals(WhereFound.ANSWERED, store.saved?.whereFound)
        assertFalse(FirstRunFunnel(store).whereFoundAnswered(WhereFoundAnswer.SKIP), "nor after a relaunch")
    }

    @Test
    fun skipIsFinalToo() {
        val (store, funnel) = begun()
        funnel.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true)
        assertTrue(funnel.whereFoundAnswered(WhereFoundAnswer.SKIP))
        assertEquals(WhereFound.SKIPPED, store.saved?.whereFound)
        assertFalse(funnel.whereFoundAnswered(WhereFoundAnswer.FRIEND))
    }

    /** #160 review: an install that updated into T56 after finishing a canvas isn't asked at its next finish. */
    @Test
    fun aFinishThatIsntTheFirstIsNotShownAndNeverAsked() {
        val (store, funnel) = begun()
        assertTrue(funnel.whereFoundDue())
        assertEquals(FirstRunAsk.No, funnel.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = false))
        assertEquals(WhereFound.NOT_SHOWN, store.saved?.whereFound)
        assertFalse(funnel.whereFoundDue())
        assertFalse(funnel.whereFoundOpen())
        assertEquals(FirstRunAsk.No, FirstRunFunnel(store).firstRunAsk(analyticsGranted = true, seed = 2, firstFinish = true))
    }

    @Test
    fun theQuestionIsOpenFromTheDecisionUntilAnAnswer() {
        val (_, funnel) = begun()
        assertFalse(funnel.whereFoundOpen())
        funnel.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true)
        assertTrue(funnel.whereFoundOpen(), "decided")
        funnel.whereFoundShown()
        assertTrue(funnel.whereFoundOpen(), "shown")
        funnel.whereFoundAnswered(WhereFoundAnswer.SKIP)
        assertFalse(funnel.whereFoundOpen(), "skipped")
    }

    @Test
    fun theOrderIsStablePerSeedWithSomewhereElseLast() {
        val order = WhereFoundAnswer.order(42)
        assertEquals(order, WhereFoundAnswer.order(42), "the same install always sees the same order")
        assertEquals(WhereFoundAnswer.OTHER, order.last())
        assertEquals(
            setOf(WhereFoundAnswer.VIDEO, WhereFoundAnswer.COMMUNITY, WhereFoundAnswer.FRIEND, WhereFoundAnswer.PLAY_SEARCH),
            order.take(4).toSet(),
        )
        assertTrue((0L until 50L).map { WhereFoundAnswer.order(it).first() }.toSet().size > 1, "the first answer varies by install")
    }
}
