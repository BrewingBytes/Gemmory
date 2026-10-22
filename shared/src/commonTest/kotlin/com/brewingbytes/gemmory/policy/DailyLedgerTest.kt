package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.Refusal
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DailyLedgerTest {
    private val clock = FakeClock()
    private val store = CodecStore(LedgerCodec::encode) { (LedgerCodec.decode(it) as? Decoded.Ok)?.value }
    private val free = Entitlements.FREE
    private val vip = Entitlements(VipPlan.YEARLY)

    private fun ledger() = DailyLedger(clock.day, store)

    private var drafts = 0

    private fun DailyLedger.start(): ConsumeResult = consume("photo-${drafts++}", free)

    /** One step of a day, and the allowance the Preview line shows after it. */
    private class Step(val name: String, val action: (DailyLedger) -> Any?, val expected: Allowance)

    /** The whole day, free → used → video → bonus → … with no cap on videos (design D5 line states, founder call #7). */
    @Test
    fun aDayOfAllowanceStates() {
        val l = ledger()
        val steps = listOf(
            Step("fresh day", { }, Allowance.Free),
            Step("start uses the free one", { it.start() }, Allowance.WatchVideo),
            Step("start with nothing left", { assertEquals(ConsumeResult.NoneLeft, it.start()) }, Allowance.WatchVideo),
            Step("video watched", { it.grantReward() }, Allowance.Bonus),
            Step("start uses the bonus", { it.start() }, Allowance.WatchVideo),
            Step("online no-fill grace", { it.useGrace(RewardType.PHOTO_CANVAS) }, Allowance.Bonus),
            Step("start", { it.start() }, Allowance.WatchVideo),
        ) + (1..10).flatMap { n ->
            listOf(
                Step("video $n", { it.grantReward() }, Allowance.Bonus),
                Step("start $n", { it.start() }, Allowance.WatchVideo),
            )
        } + listOf(
            Step("two videos in a row bank two", { it.grantReward(); it.grantReward() }, Allowance.Bonus),
            Step("first", { it.start() }, Allowance.Bonus),
            Step("second", { it.start() }, Allowance.WatchVideo),
        )
        for (step in steps) {
            step.action(l)
            assertEquals(step.expected, l.allowance(free), step.name)
        }
        assertFalse(Allowance.WatchVideo.canStart)
        assertTrue(Allowance.Bonus.canStart && Allowance.Free.canStart && Allowance.Unlimited.canStart)
    }

    @Test
    fun consumeIsIdempotentPerDraft() {
        val l = ledger()
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = false), l.consume("photo-1", free))
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = true), l.consume("photo-1", free), "double tap")
        assertEquals(Allowance.WatchVideo, l.allowance(free), "one allowance used")
        assertEquals(ConsumeResult.NoneLeft, l.consume("photo-2", free))
        l.grantReward()
        assertEquals(ConsumeResult.Ok(Spent.BONUS, repeat = false), l.consume("photo-2", free))
        assertEquals(ConsumeResult.Ok(Spent.BONUS, repeat = true), l.consume("photo-2", free))
        assertEquals(Allowance.WatchVideo, l.allowance(free))
    }

    @Test
    fun aRepeatStartAfterMidnightStillUsesNothing() {
        val l = ledger()
        clock.set("2026-10-14T23:59:59")
        l.consume("photo-1", free)
        clock.set("2026-10-15T00:00:01")
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = true), l.consume("photo-1", free))
        assertEquals(Allowance.Free, l.allowance(free), "the new day's free one is untouched")
    }

    @Test
    fun vipIsUnlimitedAndUsesNothing() {
        val l = ledger()
        assertEquals(Allowance.Unlimited, l.allowance(vip))
        repeat(5) { assertEquals(ConsumeResult.Ok(null, repeat = false), l.consume("photo-$it", vip)) }
        assertEquals(Allowance.Free, l.allowance(free), "a lapsed VIP still has today's free one")
        assertEquals(Allowance.Unlimited, l.allowance(Entitlements(VipPlan.YEARLY)))
        assertEquals(Allowance.Free, l.allowance(Entitlements(removeAds = true)), "remove-ads doesn't add canvases")
    }

    /** Local date changes, each row: (setup time, check time, whether the day reset). */
    @Test
    fun resetsOnLocalDateChange() {
        data class Row(val name: String, val usedAt: String, val checkAt: String, val reset: Boolean, val zone: TimeZone = FakeClock.BUCHAREST)
        val rows = listOf(
            Row("one second before midnight", "2026-10-14T09:00", "2026-10-14T23:59:59", reset = false),
            Row("at midnight", "2026-10-14T23:59:59", "2026-10-15T00:00", reset = true),
            Row("DST fall-back day is 25 h long", "2026-10-25T00:30", "2026-10-25T23:30", reset = false),
            Row("after the DST fall-back day", "2026-10-25T00:30", "2026-10-26T00:00", reset = true),
            Row("DST spring-forward day is 23 h long", "2026-03-29T00:10", "2026-03-29T23:50", reset = false),
            Row("after the DST spring-forward day", "2026-03-29T23:50", "2026-03-30T00:00", reset = true),
            Row("clock moved back a day (accepted reset)", "2026-10-14T10:00", "2026-10-13T10:00", reset = true),
            Row("clock moved back within the day", "2026-10-14T18:00", "2026-10-14T08:00", reset = false),
        )
        for (row in rows) {
            store.bytes = null
            clock.zone = row.zone
            clock.set(row.usedAt)
            val l = ledger()
            l.start()
            l.useGrace(RewardType.PICTURE)
            clock.set(row.checkAt)
            assertEquals(if (row.reset) Allowance.Free else Allowance.WatchVideo, l.allowance(free), row.name)
            assertEquals(row.reset, l.graceAvailable(RewardType.PICTURE), "${row.name}: grace resets with the allowance")
        }
    }

    @Test
    fun aTimeZoneChangeMovesToday() {
        clock.zone = TimeZone.UTC
        clock.set("2026-10-14T23:30")
        val l = ledger()
        l.start()
        clock.zone = FakeClock.BUCHAREST // UTC+3: it's already 02:30 on the 15th there
        assertEquals(Allowance.Free, l.allowance(free))
    }

    @Test
    fun millisUntilResetFollowsDst() {
        val l = ledger()
        clock.set("2026-10-25T00:00")
        assertEquals(25 * 3_600_000L, l.millisUntilReset(), "fall-back day")
        clock.set("2026-03-29T00:00")
        assertEquals(23 * 3_600_000L, l.millisUntilReset(), "spring-forward day")
        clock.set("2026-10-14T23:59:59")
        assertEquals(1_000L, l.millisUntilReset())
    }

    @Test
    fun graceIsOncePerDayPerRewardType() {
        val l = ledger()
        l.start()
        assertTrue(l.useGrace(RewardType.PHOTO_CANVAS))
        assertFalse(l.useGrace(RewardType.PHOTO_CANVAS), "second photo grace today")
        assertTrue(l.graceAvailable(RewardType.PICTURE), "the picture grace is separate")
        assertTrue(l.useGrace(RewardType.PICTURE))
        assertFalse(l.useGrace(RewardType.PICTURE))
        assertEquals(Allowance.Bonus, l.allowance(free), "the picture grace adds no canvas")
        clock.set("2026-10-15T00:00")
        assertTrue(l.graceAvailable(RewardType.PHOTO_CANVAS) && l.graceAvailable(RewardType.PICTURE), "midnight")
    }

    @Test
    fun videosDontUseUpTheGrace() {
        val l = ledger()
        l.start()
        repeat(5) { l.grantReward() }
        assertTrue(l.graceAvailable(RewardType.PHOTO_CANVAS) && l.graceAvailable(RewardType.PICTURE))
    }

    @Test
    fun releaseGivesBackWhatAFailedStartUsed() {
        val l = ledger()
        l.consume("photo-1", free)
        l.release("photo-1")
        assertEquals(Allowance.Free, l.allowance(free), "free one back")
        l.consume("photo-1", free)
        l.grantReward()
        l.consume("photo-2", free)
        l.release("photo-2")
        assertEquals(Allowance.Bonus, l.allowance(free), "bonus back")
        l.release("photo-unknown")
        assertEquals(Allowance.Bonus, l.allowance(free))
        clock.set("2026-10-15T08:00")
        l.consume("photo-3", free)
        clock.set("2026-10-16T08:00")
        l.release("photo-3")
        assertEquals(Allowance.Free, l.allowance(free), "yesterday's Start gives nothing to today")
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = false), l.consume("photo-3", free), "and is forgotten")
    }

    @Test
    fun stateSurvivesARestart() {
        val first = ledger()
        first.start()
        first.grantReward()
        first.useGrace(RewardType.PICTURE)
        val second = ledger()
        assertEquals(Allowance.Bonus, second.allowance(free))
        assertFalse(second.graceAvailable(RewardType.PICTURE))
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = true), second.consume("photo-0", free))
    }

    @Test
    fun onlyRecentStartsAreRemembered() {
        val l = ledger()
        for (day in listOf("2026-10-14T08:00", "2026-10-15T08:00", "2026-10-16T08:00")) {
            clock.set(day)
            l.start()
            repeat(3) { l.grantReward(); l.start() }
        }
        assertEquals(DailyLedger.MAX_REMEMBERED, (LedgerCodec.decode(store.bytes!!) as Decoded.Ok).value.consumed.size)
    }

    @Test
    fun codecRoundTripsAndRefusesDamage() {
        val state = LedgerState(
            day = 20_740,
            freeUsed = true,
            extrasEarned = 2,
            bonusLeft = 1,
            graceUsed = setOf(RewardType.PICTURE),
            consumed = listOf(Consumption("photo-1", Spent.FREE, 20_740), Consumption("photo-ü", Spent.BONUS, 20_739)),
        )
        val bytes = LedgerCodec.encode(state)
        assertEquals(state, assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(bytes)).value)
        val flipped = bytes.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        assertEquals(Decoded.Refused(Refusal.BadCrc), LedgerCodec.decode(flipped))
        assertEquals(Decoded.Refused(Refusal.TooShort), LedgerCodec.decode(byteArrayOf(1, 2)))
        val many = state.copy(extrasEarned = 300, bonusLeft = 200)
        assertEquals(many, assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(LedgerCodec.encode(many))).value, "no cap")
        val impossible = LedgerCodec.encode(state.copy(bonusLeft = 3))
        assertEquals(Decoded.Refused(Refusal.Malformed), LedgerCodec.decode(impossible), "more bonus than earned")
    }

    @Test
    fun anUnreadableFileStartsAFreshDay() {
        store.bytes = byteArrayOf(0x47, 0x4D, 0x52, 0x4C, 0, 1, 0, 0, 0, 0)
        assertEquals(Allowance.Free, ledger().allowance(free))
    }

    // #38: with the inventory as its bank, extra canvases are kept past midnight and used after today's free one.

    private val bankStore = CodecStore(InventoryCodec::encode) { (InventoryCodec.decode(it) as? Decoded.Ok)?.value }

    @Test
    fun keptCanvasesOutliveMidnightAndComeAfterTheFreeOne() {
        val bank = Inventory(bankStore)
        val l = DailyLedger(clock.day, store, bank)
        l.grantReward()
        l.grantReward()
        assertEquals(2, bank.canvases)
        assertEquals(Allowance.Free, l.allowance(free), "today's free one first")
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = false), l.start())
        assertEquals(Allowance.Bonus, l.allowance(free))
        clock.set("2026-10-15T09:00")
        assertEquals(2, bank.canvases, "nothing expired at midnight")
        assertEquals(ConsumeResult.Ok(Spent.FREE, repeat = false), l.start())
        assertEquals(ConsumeResult.Ok(Spent.KEPT, repeat = false), l.consume("photo-k", free))
        assertEquals(ConsumeResult.Ok(Spent.KEPT, repeat = true), l.consume("photo-k", free), "a repeated Start takes one")
        assertEquals(1, bank.canvases)
        // A Start that couldn't write gives the kept canvas back, even on a later day.
        clock.set("2026-10-16T09:00")
        l.release("photo-k")
        assertEquals(2, bank.canvases)
        assertEquals(ConsumeResult.Ok(null, repeat = false), l.consume("photo-v", vip), "VIP uses nothing")
        assertEquals(2, bank.canvases)
    }

    @Test
    fun theGraceCanvasIsKeptAndTheFillsGraceIsItsOwn() {
        val bank = Inventory(bankStore)
        val l = DailyLedger(clock.day, store, bank)
        l.start()
        assertTrue(l.useGrace(RewardType.PHOTO_CANVAS))
        assertEquals(1, bank.canvases)
        assertTrue(l.graceAvailable(RewardType.FILLS), "the fills grace is separate")
        assertTrue(l.useGrace(RewardType.FILLS))
        assertFalse(l.useGrace(RewardType.FILLS))
        assertEquals(1, bank.canvases, "the caller adds the fills, not the ledger")
        val saved = assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(store.bytes!!)).value
        assertEquals(setOf(RewardType.PHOTO_CANVAS, RewardType.FILLS), saved.graceUsed)
    }

    /** #29: the More colors grace is its own, grants nothing in the ledger, and its bit (8) round-trips. */
    @Test
    fun theColorsGraceIsItsOwnAndRoundTrips() {
        val bank = Inventory(bankStore)
        val l = DailyLedger(clock.day, store, bank)
        l.start()
        assertTrue(l.useGrace(RewardType.COLORS))
        assertFalse(l.useGrace(RewardType.COLORS))
        assertTrue(l.graceAvailable(RewardType.PHOTO_CANVAS), "the photo canvas grace is separate")
        assertEquals(0, bank.canvases, "the caller unlocks More on the photo, the ledger only remembers the grace")
        val bytes = store.bytes!!
        assertEquals(setOf(RewardType.COLORS), assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(bytes)).value.graceUsed)
        // A file written before #29 (bits 1-4 only) still reads the same.
        val old = LedgerCodec.encode(LedgerState(day = 1, graceUsed = setOf(RewardType.PICTURE, RewardType.FILLS)))
        assertEquals(setOf(RewardType.PICTURE, RewardType.FILLS), assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(old)).value.graceUsed)
    }

    /** #102: the streak repair grace is its own, once a day, and its bit (16) round-trips in v2. */
    @Test
    fun theStreakRepairGraceIsItsOwnAndRoundTrips() {
        val l = ledger()
        assertTrue(l.useGrace(RewardType.STREAK_REPAIR))
        assertFalse(l.useGrace(RewardType.STREAK_REPAIR), "once a day")
        assertTrue(l.graceAvailable(RewardType.PICTURE) && l.graceAvailable(RewardType.FILLS), "the others are separate")
        assertEquals(Allowance.Free, l.allowance(free), "it grants no canvas")
        assertEquals(setOf(RewardType.STREAK_REPAIR), assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(store.bytes!!)).value.graceUsed)
        clock.set("2026-10-15T00:00")
        assertTrue(l.graceAvailable(RewardType.STREAK_REPAIR), "midnight")
    }

    /** R1 (#102): a v1 file (before the streak repair) reads exactly as before; v1 can't carry bit 16; v3 is refused. */
    @Test
    fun aV1FileStillDecodes() {
        val state = LedgerState(
            day = 20_740,
            freeUsed = true,
            extrasEarned = 2,
            bonusLeft = 1,
            graceUsed = setOf(RewardType.PICTURE, RewardType.COLORS),
            consumed = listOf(Consumption("photo-1", Spent.FREE, 20_740)),
        )
        val v2 = LedgerCodec.encode(state)
        assertEquals(2, v2[5].toInt(), "written as v2")
        assertEquals(state, assertIs<Decoded.Ok<LedgerState>>(LedgerCodec.decode(withVersion(v2, 1))).value)
        val repair = LedgerCodec.encode(state.copy(graceUsed = setOf(RewardType.STREAK_REPAIR)))
        assertEquals(Decoded.Refused(Refusal.Malformed), LedgerCodec.decode(withVersion(repair, 1)), "no repair bit in v1")
        assertEquals(Decoded.Refused(Refusal.NewerVersion(3)), LedgerCodec.decode(withVersion(v2, 3)))
    }

    /** [bytes] rewritten with envelope [version] and a fresh CRC. */
    private fun withVersion(bytes: ByteArray, version: Int): ByteArray =
        Envelope.wrap(LedgerCodec.MAGIC, version, bytes.copyOfRange(6, bytes.size - 4))

    @Test
    fun aBonusFromBeforeTheUpdateIsStillUsedToday() {
        DailyLedger(clock.day, store).apply {
            start()
            grantReward()
        }
        val l = DailyLedger(clock.day, store, Inventory(bankStore))
        assertEquals(Allowance.Bonus, l.allowance(free))
        assertEquals(ConsumeResult.Ok(Spent.BONUS, repeat = false), l.start())
        assertEquals(Allowance.WatchVideo, l.allowance(free))
    }
}
