package com.brewingbytes.gemmory.bundled

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #34: what Today's picture may pick from, for a free player and for VIP. */
class TodayCandidatesTest {
    private val locked = BundledPictures.ALL.filter { it.locked }.map { it.id }

    @Test
    fun aFreePlayerGetsALockedPictureNeitherUnlockedNorStarted() {
        assertEquals(locked, todayCandidates(started = emptySet(), unlocked = emptySet(), vip = false))
        val c = todayCandidates(started = setOf("the-kiss", "lemons"), unlocked = setOf("wave"), vip = false)
        assertEquals(locked - setOf("the-kiss", "wave"), c)
        assertTrue(BundledPictures.FREE.none { it in c }, "a free picture is never today's")
    }

    @Test
    fun neverTheStarter() {
        assertFalse(BundledPictures.STARTER.id in todayCandidates(emptySet(), emptySet(), vip = false))
        assertFalse(BundledPictures.STARTER.id in todayCandidates(emptySet(), emptySet(), vip = true))
    }

    @Test
    fun nothingToPickWhenEveryLockedPictureIsOpen() {
        assertEquals(emptyList(), todayCandidates(started = emptySet(), unlocked = locked.toSet(), vip = false))
    }

    @Test
    fun vipPicksFromEveryPictureNotStarted() {
        val all = BundledPictures.ALL.map { it.id } - BundledPictures.STARTER.id
        assertEquals(all - "cherries", todayCandidates(started = setOf("cherries"), unlocked = setOf("wave"), vip = true))
    }

    /** #43: a new remote picture, when one is eligible, is today's picture before any other. */
    @Test
    fun aNewRemotePictureIsPreferred() {
        val pictures = BundledPictures.ALL.map { TodayOption(it.id, it.locked) } + TodayOption("fox", locked = true) + TodayOption("owl", locked = false)
        assertEquals(listOf("fox"), todayCandidates(emptySet(), emptySet(), vip = false, pictures) { it == "fox" || it == "owl" })
        assertEquals(listOf("fox", "owl"), todayCandidates(emptySet(), emptySet(), vip = true, pictures) { it == "fox" || it == "owl" })
        // Started or unlocked, it's no longer a candidate, so the rest come back.
        assertEquals(locked, todayCandidates(setOf("fox"), emptySet(), vip = false, pictures) { it == "fox" })
        assertEquals(locked + "fox", todayCandidates(emptySet(), emptySet(), vip = false, pictures) { false })
    }

    /** T36: today's drop, when eligible, comes before every other New remote picture. */
    @Test
    fun todaysDropIsPreferred() {
        val pictures = BundledPictures.ALL.map { TodayOption(it.id, it.locked) } + TodayOption("fox", locked = true) + TodayOption("harbor-drop", locked = true)
        val isNew = { id: String -> id == "fox" || id == "harbor-drop" }
        assertEquals(listOf("harbor-drop"), todayCandidates(emptySet(), emptySet(), vip = false, pictures, isDrop = { it == "harbor-drop" }, isNew = isNew))
        assertEquals(listOf("fox"), todayCandidates(setOf("harbor-drop"), emptySet(), vip = false, pictures, isDrop = { it == "harbor-drop" }, isNew = isNew))
    }

    @Test
    fun everyCatalogPictureHasASize() {
        for (p in BundledPictures.ALL - BundledPictures.STARTER) assertTrue(BundledPictures.sizeOf(p.id) != null, p.id)
        assertEquals(null, BundledPictures.sizeOf(BundledPictures.STARTER.id))
    }
}
