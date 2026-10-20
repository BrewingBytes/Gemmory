package com.brewingbytes.gemmory.policy

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodaysPictureTest {
    private val clock = FakeClock("2026-10-17T09:00")
    private val store = object : StateStore<TodayState> {
        var saved: TodayState? = null

        override fun load() = saved

        override fun save(value: TodayState) {
            saved = value
        }
    }
    private val locked = (1..42).map { "p$it" }

    private fun today() = TodaysPicture(clock.day, store)

    @Test
    fun theSamePictureAllDayEvenOnceItIsStarted() {
        val t = today()
        val pick = assertNotNull(t.pick(locked, vip = false))
        clock.set("2026-10-17T23:59")
        // Started (or unlocked) now, so it's no longer a candidate: still today's picture.
        assertEquals(pick, t.pick(locked - pick, vip = false))
        assertEquals(pick, today().pick(locked - pick, vip = false), "a relaunch reads the same pick")
        assertEquals(pick, t.picked(vip = false))
    }

    /**
     * R1 (eng D3, D10): the evening reminder makes the day's pick when it runs first, and the gallery's later call,
     * with a drop or a new picture among its candidates by then, reads the same one.
     */
    @Test
    fun whoeverPicksFirstTheOtherReadsTheSamePick() {
        clock.set("2026-10-17T20:30")
        val byReminder = assertNotNull(today().pick(locked, vip = false))
        clock.set("2026-10-17T21:10")
        assertEquals(byReminder, today().pick(listOf("tonights-drop") + locked, vip = false), "the gallery opened from the notification")
    }

    @Test
    fun aNewPictureAfterLocalMidnight() {
        val t = today()
        val first = t.pick(locked, vip = false)
        clock.set("2026-10-18T00:00")
        assertNull(t.picked(vip = false), "nothing picked yet on the new day")
        val second = t.pick(locked, vip = false)
        assertNotNull(second)
        assertNotEquals(first, second, "never the same picture two days running while another is left")
    }

    @Test
    fun differentDaysOfferDifferentPictures() {
        val t = today()
        val picks = (17..31).map { day ->
            clock.set("2026-10-${day}T12:00")
            t.pick(locked, vip = false)
        }
        assertTrue(picks.toSet().size >= 10, "15 days should spread over the catalog: $picks")
    }

    @Test
    fun neverAPictureOutsideTheCandidates() {
        val t = today()
        // The caller leaves out what's unlocked or started; whatever is left is all it may pick.
        val open = setOf("p1", "p2", "p3", "p5", "p8")
        for (day in 1..60) {
            clock.set("2026-11-01T12:00")
            clock.advanceMinutes(day * 24L * 60)
            val pick = t.pick(locked - open, vip = false)
            assertTrue(pick != null && pick !in open, "day $day picked $pick")
        }
    }

    @Test
    fun aTimeZoneChangeFollowsTheLocalDate() {
        clock.set("2026-10-17T23:30")
        val t = today()
        val home = t.pick(locked, vip = false)
        // The same instant is already the 18th in Tokyo: a new local date, a new picture.
        clock.zone = TimeZone.of("Asia/Tokyo")
        val east = t.pick(locked, vip = false)
        assertNotEquals(home, east)
        // A zone that keeps the same local date keeps the picture.
        clock.zone = TimeZone.of("Asia/Seoul")
        assertEquals(east, t.pick(locked, vip = false))
    }

    @Test
    fun nothingToPickWhenEverythingIsOpen() {
        val t = today()
        assertNull(t.pick(emptyList(), vip = false))
        assertNull(t.picked(vip = false))
        // A Start over later the same day frees a picture: it can be today's after all.
        assertEquals("p7", t.pick(listOf("p7"), vip = false))
    }

    @Test
    fun vipGetsItsOwnPickAndALapseRepicks() {
        val t = today()
        val all = listOf("free1", "free2") + locked
        val vipPick = assertNotNull(t.pick(all, vip = true))
        assertEquals(vipPick, t.pick(all - vipPick, vip = true), "VIP's pick holds all day too")
        assertNull(t.picked(vip = false), "VIP's pick isn't a free grant")
        val free = t.pick(locked, vip = false)
        assertTrue(free in locked, "after a lapse, the free pick is a locked picture")
    }

    @Test
    fun theRuleIsTheSameOnEveryPlatform() {
        // Pinned, so a change to the seed can't silently move everyone's picture of the day.
        assertEquals("p34", TodaysPicture.choose(20_378L, locked, avoid = null))
        assertEquals(null, TodaysPicture.choose(20_378L, emptyList(), avoid = null))
        assertEquals("p1", TodaysPicture.choose(20_378L, listOf("p1"), avoid = "p1"), "the only candidate, even if it was yesterday's")
    }
}
