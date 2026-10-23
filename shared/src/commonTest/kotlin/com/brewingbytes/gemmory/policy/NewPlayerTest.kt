package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T46 (eng O-E7/8): a fresh install is one with none of its played state on the disk, then a marker. */
class NewPlayerTest {
    private val empty = NewPlayer.Disk(canvas = false, achievements = false, streak = false, marker = false)

    private class MemoryStore : StateStore<FirstRunState> {
        var saved: FirstRunState? = null

        override fun load() = saved

        override fun save(value: FirstRunState) {
            saved = value
        }
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
}
