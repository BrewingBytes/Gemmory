package com.brewingbytes.gemmory.gallery

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.VipPlan
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #58: the Pictures tab shows every picture a tap opens first, then the locked ones, each in the #31 ramp. #89: the
 * finished canvases leave that order for the Finished section at the end, newest finish first.
 */
class GalleryOrderTest {
    /** The tiles as the store lists them: catalog order, [started] ones with a gem placed. */
    private fun tiles(started: Set<String> = emptySet()) = BundledPictures.ALL.map {
        Tile(CanvasRef(CanvasKind.BUNDLED, it.id), it.title(XmlStrings.en), 0.75f, null, 0, done = false, damaged = false, started = it.id in started, lockable = it.locked)
    }

    private fun order(
        unlocked: Set<String> = emptySet(),
        entitlements: Entitlements = Entitlements.FREE,
        started: Set<String> = emptySet(),
        todayFree: Set<String> = emptySet(),
    ): List<String> = picturesInOrder(tiles(started), unlocked, entitlements, todayFree).map { it.ref.id }

    /** The locked pictures in catalog order, less [open]. */
    private fun lockedExcept(vararg open: String) =
        BundledPictures.ALL.filter { it.locked && it.id !in open }.map { it.id }

    @Test
    fun aFreePlayerSeesTheStarterAndTheFreePicturesFirst() {
        val open = listOf("starter", "lemons", "balloon", "cherries", "tuscan-hills", "puppy", "cat")
        assertEquals(open + lockedExcept(), order())
        assertEquals(BundledPictures.FREE.size + 1, open.size)
    }

    @Test
    fun aPictureUnlockedByAVideoJoinsTheOpenOnesInItsSize() {
        val open = listOf("starter", "wave", "lemons", "balloon", "cherries", "tuscan-hills", "puppy", "cat")
        assertEquals(open + lockedExcept("wave"), order(unlocked = setOf("wave")))
    }

    /**
     * T37 (D25), R1: an earned keepsake is a catalog-locked remote picture whose id is in the unlocks, so it joins the
     * open group in its size like any unlocked picture, and the bundled order around it doesn't move.
     */
    @Test
    fun anEarnedKeepsakeIsAnOpenPicture() {
        val keepsake = Tile(CanvasRef(CanvasKind.REMOTE, "harbour-lights"), "Harbour Lights", 1f, null, 0, done = false, damaged = false, lockable = true)
        val ordered = picturesInOrder(tiles() + keepsake, setOf("harbour-lights"), Entitlements.FREE).map { it.ref.id }
        val open = listOf("starter", "lemons", "balloon", "cherries", "tuscan-hills", "puppy", "cat", "harbour-lights")
        assertEquals(open + lockedExcept(), ordered)
    }

    @Test
    fun aStartedLockedPictureJoinsTheOpenOnesInItsSize() {
        val open = listOf("starter", "lemons", "balloon", "the-kiss", "cherries", "tuscan-hills", "puppy", "cat")
        assertEquals(open + lockedExcept("the-kiss"), order(started = setOf("the-kiss")))
    }

    /** #34: today's free picture opens without a video, so today it's an open one in its size; nothing else moves. */
    @Test
    fun todaysFreePictureJoinsTheOpenOnesInItsSize() {
        val open = listOf("starter", "lemons", "balloon", "the-kiss", "cherries", "tuscan-hills", "puppy", "cat")
        assertEquals(open + lockedExcept("the-kiss"), order(todayFree = setOf("the-kiss")))
        assertEquals(BundledPictures.ALL.map { it.id }, order(entitlements = Entitlements(plan = VipPlan.YEARLY), todayFree = setOf("the-kiss")))
    }

    /** #43: a remote picture follows the same rule by its catalog flag: open ones with the open, locked ones after. */
    @Test
    fun remotePicturesOpenOrLockByTheirCatalogFlag() {
        fun remote(id: String, locked: Boolean) =
            Tile(CanvasRef(CanvasKind.REMOTE, id), id, 1f, null, 0, done = false, damaged = false, lockable = locked)
        val tiles = tiles() + remote("fox", locked = false) + remote("lanterns", locked = true)
        val order = picturesInOrder(tiles, emptySet(), Entitlements.FREE).map { it.ref.id }
        assertEquals(listOf("starter", "lemons", "balloon", "cherries", "tuscan-hills", "puppy", "cat", "fox"), order.take(8))
        assertEquals("lanterns", order.last())
        val unlocked = picturesInOrder(tiles, setOf("lanterns"), Entitlements.FREE).map { it.ref.id }
        assertEquals(listOf("fox", "lanterns"), unlocked.take(9).takeLast(2), "a video or token unlock opens it")
        assertEquals(tiles.map { it.ref.id }, picturesInOrder(tiles, emptySet(), Entitlements(plan = VipPlan.YEARLY)).map { it.ref.id })
    }

    /** A finished tile, finished on [day] (null: before T35), its canvas made at [created]. */
    private fun finished(id: String, day: Long?, created: Long = 0L, kind: CanvasKind = CanvasKind.BUNDLED) =
        Tile(CanvasRef(kind, id), id, 1f, null, 100, done = true, damaged = false, started = true, finishedDay = day, createdAtMillis = created)

    /** The tiles as [GalleryViewModel.refresh] splits them: what's left to play in the #58 order, then Finished. */
    private fun split(tiles: List<Tile>, unlocked: Set<String> = emptySet()) =
        picturesInOrder(tiles.filterNot { it.done }, unlocked, Entitlements.FREE).map { it.ref.id } to finishedInOrder(tiles).map { it.ref.id }

    @Test
    fun finishedPicturesLeaveTheMainOrderForTheFinishedSection() {
        val tiles = tiles().map { if (it.ref.id in setOf("starter", "puppy", "wave")) it.copy(done = true, started = true, percent = 100) else it }
        val (main, done) = split(tiles)
        assertEquals(listOf("lemons", "balloon", "cherries", "tuscan-hills", "cat") + lockedExcept("wave"), main)
        assertEquals(setOf("starter", "puppy", "wave"), done.toSet())
    }

    /** R1 (eng D10): the open and locked order of what's left is exactly #58's, finished tiles or not. */
    @Test
    fun theOpenAndLockedOrderIsUnchangedByFinishedPictures() {
        val done = setOf("lemons", "the-kiss")
        val tiles = tiles(started = done).map { if (it.ref.id in done) it.copy(done = true) else it }
        val left = tiles.filterNot { it.ref.id in done }
        for (unlocked in listOf(emptySet(), setOf("wave"))) {
            assertEquals(picturesInOrder(left, unlocked, Entitlements.FREE).map { it.ref.id }, split(tiles, unlocked).first)
        }
        assertEquals(order().filterNot { it in done }, split(tiles).first)
    }

    @Test
    fun theNewestFinishComesFirstAndASameDayTieGoesNewestCreatedFirst() {
        val tiles = listOf(finished("a", day = 20_380), finished("b", day = 20_382, created = 1), finished("c", day = 20_382, created = 5))
        assertEquals(listOf("c", "b", "a"), finishedInOrder(tiles).map { it.ref.id })
    }

    /** Finished before T35, a canvas has no date: after every dated one, newest created first. */
    @Test
    fun undatedFinishesGoAfterTheDatedOnesNewestCreatedFirst() {
        val tiles = listOf(finished("old", day = null, created = 1), finished("dated", day = 20_000), finished("newer", day = null, created = 9))
        assertEquals(listOf("dated", "newer", "old"), finishedInOrder(tiles).map { it.ref.id })
    }

    /** Play again or Start over: the tile isn't done, so on the next visit it's back in its place in the #58 order. */
    @Test
    fun aPlayedAgainPictureIsBackInItsPlace() {
        val tiles = tiles().map { if (it.ref.id == "starter") it.copy(done = true, started = true, finishedDay = 20_000) else it }
        assertEquals(listOf("starter"), split(tiles).second)
        val again = tiles.map { if (it.ref.id == "starter") it.copy(done = false, percent = 0) else it }
        assertEquals(order(), split(again).first)
        assertEquals(emptyList(), split(again).second, "nothing finished: no heading")
    }

    @Test
    fun nothingFinishedMeansNoFinishedSection() {
        assertEquals(emptyList(), finishedInOrder(tiles()))
    }

    /** My Photos: unfinished photo canvases stay newest created first; the finished ones go to their own section. */
    @Test
    fun myPhotosKeepTheUnfinishedFirstAndFinishTheirOwnSection() {
        fun photo(id: String, created: Long) =
            Tile(CanvasRef(CanvasKind.PHOTO, id), id, 1f, null, 10, done = false, damaged = false, started = true, createdAtMillis = created)
        val photos = listOf(photo("p3", 3), finished("p2", day = 20_001, created = 2, kind = CanvasKind.PHOTO), photo("p1", 1), finished("p0", day = 20_005, kind = CanvasKind.PHOTO))
        assertEquals(listOf("p3", "p1"), photos.filterNot { it.done }.map { it.ref.id })
        assertEquals(listOf("p0", "p2"), finishedInOrder(photos).map { it.ref.id })
    }

    @Test
    fun vipSeesThePlainRamp() {
        val all = BundledPictures.ALL.map { it.id }
        assertEquals(all, order(entitlements = Entitlements(plan = VipPlan.YEARLY)))
        assertEquals(all, order(unlocked = setOf("wave"), entitlements = Entitlements(plan = VipPlan.WEEKLY), started = setOf("the-kiss")))
    }
}
