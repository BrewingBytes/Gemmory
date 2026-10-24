package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemotePicture
import kotlinx.datetime.LocalDate
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

    /**
     * T35 (eng D3): the gallery's card and the evening reminder build Today's choices the same way, from the catalog,
     * with no canvas read: remote pictures join their size's run after the bundled ones, oldest first, and a dated
     * drop is never "New" (T36, eng D4).
     */
    @Test
    fun todaysChoicesMergeRemotePicturesAndKeepDropsOutOfNew() {
        fun remote(id: String, size: CanvasSize, added: String, availableFrom: String? = null) = RemotePicture(
            id, id, AspectRatio.SQUARE, size, 10, 10, 2, "grids/$id.gmrg", "", 0, locked = true, minAppVersion = 1,
            addedAt = LocalDate.parse(added), availableFrom = availableFrom?.let(LocalDate::parse),
        )
        val listed = listOf(
            remote("owl", CanvasSize.QUICK, "2026-10-19"),
            remote("fox", CanvasSize.QUICK, "2026-10-01"),
            remote("harbor-drop", CanvasSize.MEDIUM, "2026-10-20", availableFrom = "2026-10-20"),
        )
        val choices = todayChoices(setOf("cherries"), listed, drops = setOf("harbor-drop")) { true }
        val ids = choices.options.map { it.id }
        val lastQuick = BundledPictures.ALL.last { BundledPictures.canvasSizeOf(it.id) == CanvasSize.QUICK }.id
        assertEquals(listOf(lastQuick, "fox", "owl"), ids.subList(ids.indexOf(lastQuick), ids.indexOf(lastQuick) + 3))
        assertFalse(BundledPictures.STARTER.id in ids, "the starter has no size")
        assertEquals(setOf("owl", "fox"), choices.newIds)
        assertEquals(setOf("cherries"), choices.started)
    }

    @Test
    fun everyCatalogPictureHasASize() {
        for (p in BundledPictures.ALL - BundledPictures.STARTER) assertTrue(BundledPictures.canvasSizeOf(p.id) != null, p.id)
        assertEquals(null, BundledPictures.canvasSizeOf(BundledPictures.STARTER.id))
    }
}
