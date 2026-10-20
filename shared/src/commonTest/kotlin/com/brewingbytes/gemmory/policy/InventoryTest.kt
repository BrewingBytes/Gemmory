package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.Refusal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryTest {
    private val store = CodecStore(InventoryCodec::encode) { (InventoryCodec.decode(it) as? Decoded.Ok)?.value }
    private var rolls = ArrayDeque<Int>()

    private fun inventory() = Inventory(store) { rolls.removeFirst() }

    @Test
    fun earnSpendAndDoubleSpend() {
        val inv = inventory()
        assertFalse(inv.spend(Item.FILLS, "use-1"), "nothing to spend yet")
        inv.earn(Item.FILLS, Inventory.FILLS_PER_VIDEO)
        assertTrue(inv.spend(Item.FILLS, "use-1"))
        assertTrue(inv.spend(Item.FILLS, "use-1"), "a repeat of the same use is fine")
        assertEquals(4, inv.count(Item.FILLS), "and takes nothing more")
        assertTrue(inv.spend(Item.FILLS, "use-2"))
        assertEquals(3, inv.count(Item.FILLS))
        // The same id for another item is another spend.
        inv.earn(Item.UNLOCK_TOKEN, 1)
        assertTrue(inv.spend(Item.UNLOCK_TOKEN, "use-2"))
        assertEquals(0, inv.count(Item.UNLOCK_TOKEN))
        assertFalse(inv.spend(Item.UNLOCK_TOKEN, "use-3"))
    }

    @Test
    fun aRefundGivesBackOnlyWhatWasSpent() {
        val inv = inventory()
        inv.earn(Item.PHOTO_CANVAS, 2)
        assertTrue(inv.takeCanvas("draft-1"))
        assertEquals(1, inv.canvases)
        inv.returnCanvas("draft-2")
        assertEquals(1, inv.canvases, "draft-2 took nothing")
        inv.returnCanvas("draft-1")
        inv.returnCanvas("draft-1")
        assertEquals(2, inv.canvases, "a second refund gives nothing")
    }

    @Test
    fun everyFifthStreakDayEarnsASpinAndMissedSpinsAddUp() {
        val inv = inventory()
        for (length in 1..4) inv.onStreakDay(length)
        assertEquals(0, inv.state.spinsWaiting)
        assertNull(inv.spin())
        inv.onStreakDay(5)
        for (length in 6..10) inv.onStreakDay(length)
        assertEquals(2, inv.state.spinsWaiting, "days 5 and 10, both unspun")
        // A broken streak starts over: day 5 of the new one is another gift.
        inv.onStreakDay(1)
        inv.onStreakDay(5)
        assertEquals(3, inv.state.spinsWaiting)
    }

    @Test
    fun aSpinAddsItsPrizeAtOnce() {
        val inv = inventory()
        repeat(3) { inv.onStreakDay(5) }
        rolls += listOf(0, 95, 70)
        assertEquals(WheelPrize.FILLS_5, inv.spin())
        assertEquals(WheelPrize.UNLOCK_TOKEN, inv.spin())
        assertEquals(WheelPrize.PHOTO_CANVAS, inv.spin())
        assertNull(inv.spin(), "no spin left")
        assertEquals(InventoryState(fills = 5, canvases = 1, tokens = 1, spinsEarned = 3, spinsUsed = 3), inv.state)
        // What's on disk is the same: a kill right after a spin can't spin it again.
        assertEquals(inv.state, Inventory(store).state)
    }

    @Test
    fun theOddsAreTheSegmentWidths() {
        val counts = (0 until 100).groupingBy(WheelPrize::forRoll).eachCount()
        assertEquals(WheelPrize.entries.associateWith { it.percent }, counts)
        assertEquals(100, WheelPrize.entries.sumOf { it.percent })
        assertEquals(WheelPrize.FILLS_5, WheelPrize.forRoll(49))
        assertEquals(WheelPrize.FILLS_10, WheelPrize.forRoll(50))
        assertEquals(WheelPrize.FILLS_10, WheelPrize.forRoll(69))
        assertEquals(WheelPrize.PHOTO_CANVAS, WheelPrize.forRoll(89))
        assertEquals(WheelPrize.UNLOCK_TOKEN, WheelPrize.forRoll(99))
    }

    @Test
    fun theFileRoundTripsAndRefusesDamage() {
        val state = InventoryState(
            fills = 300,
            canvases = 2,
            tokens = 1,
            spinsEarned = 4,
            spinsUsed = 3,
            recent = listOf(Spend("photo-1", Item.PHOTO_CANVAS), Spend("fill-région-9", Item.FILLS)),
        )
        val bytes = InventoryCodec.encode(state)
        assertEquals(state, assertIs<Decoded.Ok<InventoryState>>(InventoryCodec.decode(bytes)).value)
        assertContentEquals(InventoryCodec.MAGIC, bytes.copyOf(4))
        val flipped = bytes.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        assertIs<Decoded.Refused>(InventoryCodec.decode(flipped))
        // More spins used than earned is malformed, CRC or not.
        val bad = InventoryCodec.encode(state.copy(spinsUsed = 5))
        assertEquals(Decoded.Refused(Refusal.Malformed), InventoryCodec.decode(bad))
    }

    /** T37: a collection's token is earned once per id, kept in the same write as the count, across reloads. */
    @Test
    fun aOneTimeEarnIsGivenOnce() {
        val inv = inventory()
        assertTrue(inv.earnOnce(Item.UNLOCK_TOKEN, 1, "collection-seaside"))
        assertFalse(inv.earnOnce(Item.UNLOCK_TOKEN, 1, "collection-seaside"))
        assertEquals(1, inv.count(Item.UNLOCK_TOKEN))
        // A fresh read of the file (the app restarted after a crash) still knows it.
        val again = inventory()
        assertFalse(again.earnOnce(Item.UNLOCK_TOKEN, 1, "collection-seaside"))
        assertTrue(again.earnOnce(Item.UNLOCK_TOKEN, 1, "collection-forest"))
        assertEquals(2, again.count(Item.UNLOCK_TOKEN))
        // Spending the token doesn't forget the earn.
        assertTrue(again.spend(Item.UNLOCK_TOKEN, "unlock-a"))
        assertFalse(again.earnOnce(Item.UNLOCK_TOKEN, 1, "collection-seaside"))
    }

    /** R1: a v1 file (before T37) reads as before, with no one-time earns, and is written back as v2. */
    @Test
    fun aVersionOneFileStillReads() {
        val state = InventoryState(fills = 7, tokens = 2, spinsEarned = 1, recent = listOf(Spend("photo-1", Item.PHOTO_CANVAS)))
        val v2 = Envelope.open(InventoryCodec.encode(state), InventoryCodec.MAGIC) as Decoded.Ok<Envelope.Opened>
        // v1's body is v2's without the trailing empty earn list (one varint 0).
        val v1 = Envelope.wrap(InventoryCodec.MAGIC, 1, v2.value.body.copyOf(v2.value.body.size - 1))
        assertEquals(state, assertIs<Decoded.Ok<InventoryState>>(InventoryCodec.decode(v1)).value)
        val rewarded = state.copy(rewarded = setOf("collection-seaside"))
        assertEquals(rewarded, assertIs<Decoded.Ok<InventoryState>>(InventoryCodec.decode(InventoryCodec.encode(rewarded))).value)
    }

    @Test
    fun aDamagedFileStartsEmpty() {
        store.bytes = byteArrayOf(1, 2, 3)
        assertEquals(InventoryState(), inventory().state)
    }
}
