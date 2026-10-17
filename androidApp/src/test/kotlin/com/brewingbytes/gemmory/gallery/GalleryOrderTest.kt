package com.brewingbytes.gemmory.gallery

import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.VipPlan
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** #58: the Pictures tab shows every picture a tap opens first, then the locked ones, each in the #31 ramp. */
class GalleryOrderTest {
    /** The tiles as the store lists them: catalog order, [started] ones with a gem placed. */
    private fun tiles(started: Set<String> = emptySet()) = BundledPictures.ALL.map {
        Tile(CanvasRef(CanvasKind.BUNDLED, it.id), it.title, 0.75f, null, 0, done = false, damaged = false, started = it.id in started)
    }

    private fun order(
        unlocked: Set<String> = emptySet(),
        entitlements: Entitlements = Entitlements.FREE,
        started: Set<String> = emptySet(),
        todayFree: String? = null,
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

    @Test
    fun aStartedLockedPictureJoinsTheOpenOnesInItsSize() {
        val open = listOf("starter", "lemons", "balloon", "the-kiss", "cherries", "tuscan-hills", "puppy", "cat")
        assertEquals(open + lockedExcept("the-kiss"), order(started = setOf("the-kiss")))
    }

    /** #34: today's free picture opens without a video, so today it's an open one in its size; nothing else moves. */
    @Test
    fun todaysFreePictureJoinsTheOpenOnesInItsSize() {
        val open = listOf("starter", "lemons", "balloon", "the-kiss", "cherries", "tuscan-hills", "puppy", "cat")
        assertEquals(open + lockedExcept("the-kiss"), order(todayFree = "the-kiss"))
        assertEquals(BundledPictures.ALL.map { it.id }, order(entitlements = Entitlements(plan = VipPlan.YEARLY), todayFree = "the-kiss"))
    }

    @Test
    fun vipSeesThePlainRamp() {
        val all = BundledPictures.ALL.map { it.id }
        assertEquals(all, order(entitlements = Entitlements(plan = VipPlan.YEARLY)))
        assertEquals(all, order(unlocked = setOf("wave"), entitlements = Entitlements(plan = VipPlan.WEEKLY), started = setOf("the-kiss")))
    }
}
