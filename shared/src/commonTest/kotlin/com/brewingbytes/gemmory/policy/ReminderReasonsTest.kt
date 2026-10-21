package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.converter.CanvasSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReminderReasonsTest {
    private val clock = FakeClock("2026-10-20T20:30")
    private val scheduled = clock.now
    private val today = clock.day.today()
    private val hour = 60 * 60 * 1000L

    private fun canvas(key: String, placed: Int, total: Int = 100, playedHoursAgo: Long? = 24, photo: Boolean = false) =
        ReminderCanvas(key, if (photo) null else key, placed, total, playedHoursAgo?.let { scheduled - it * hour })

    /** A streak of 4 whose last day was yesterday, with a day already forgiven this week: it breaks at midnight. */
    private val breakingStreak = StreakState(length = 4, lastDay = today - 1, restDay = today - 3)

    private fun inputs(
        drops: List<String> = emptyList(),
        pick: String? = null,
        started: List<ReminderCanvas> = emptyList(),
        streak: StreakState = StreakState(),
        foreground: Boolean = false,
        now: Long = scheduled,
    ) = ReminderInputs(scheduled, today, now, ReminderSchedule.dayOf(now, clock.zone), foreground, drops, pick, started, streak)

    private fun post(inputs: ReminderInputs) = assertIs<ReminderOutcome.Post>(ReminderReasons.decide(inputs))

    private val almost = canvas("venice", placed = 82)

    @Test
    fun reasonsComeInPriorityOrder() {
        val all = inputs(drops = listOf("lighthouse"), pick = "huts", started = listOf(almost), streak = breakingStreak)
        assertEquals(ReminderOutcome.Post(ReminderReason.DROP, pictureId = "lighthouse"), post(all))
        assertEquals(ReminderOutcome.Post(ReminderReason.TODAY, pictureId = "huts"), post(all.copy(todaysDrops = emptyList())))
        assertEquals(
            ReminderOutcome.Post(ReminderReason.STREAK, canvas = almost, streak = 4),
            post(all.copy(todaysDrops = emptyList(), todaysPick = null)),
        )
        assertEquals(
            ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, pictureId = "venice", canvas = almost),
            post(all.copy(todaysDrops = emptyList(), todaysPick = null, streak = StreakState())),
        )
    }

    @Test
    fun nothingRealPostsNothing() {
        assertEquals(ReminderOutcome.Quiet(skipped = false), ReminderReasons.decide(inputs()))
    }

    @Test
    fun aDropThatIsAlsoTodaysPictureUsesTheDropCopy() {
        assertEquals(ReminderReason.DROP, post(inputs(drops = listOf("lighthouse"), pick = "lighthouse")).reason)
    }

    @Test
    fun startedPicturesAreNotOffered() {
        val started = listOf(canvas("lighthouse", placed = 1), canvas("huts", placed = 3))
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(drops = listOf("lighthouse"), pick = "huts", started = started)))
        assertEquals("second", post(inputs(drops = listOf("lighthouse", "second"), started = started)).pictureId)
    }

    @Test
    fun theStreakSpeaksOnlyWhenItReallyBreaksAtMidnight() {
        assertEquals(ReminderReason.STREAK, post(inputs(streak = breakingStreak)).reason)
        // A day off is still forgivable tomorrow, so missing today doesn't break it.
        val forgivable = breakingStreak.copy(restDay = StreakState.NEVER)
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(streak = forgivable)))
        // Already finished something today.
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(streak = breakingStreak.copy(lastDay = today))))
        // A one-day streak isn't news.
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(streak = breakingStreak.copy(length = 1))))
        // Already broken.
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(streak = breakingStreak.copy(lastDay = today - 3))))
    }

    /** #101: the VIP freeze or a streak freeze would cover today, so the streak doesn't break at midnight. */
    @Test
    fun aReadyFreezeKeepsTheStreakQuiet() {
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(streak = breakingStreak).copy(cover = Cover(vip = true))))
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(streak = breakingStreak).copy(cover = Cover(freezes = 1))))
        val vipUsed = breakingStreak.copy(vipFreezeDay = today - 2)
        assertEquals(ReminderReason.STREAK, post(inputs(streak = vipUsed).copy(cover = Cover(vip = true))).reason, "VIP freeze used this week")
    }

    @Test
    fun aStreakAfterAForgivenDayCountsTheForgivenDayOut() {
        // Played the day before yesterday, yesterday forgiven: the streak stands today and breaks at midnight.
        val forgiven = StreakState(length = 5, lastDay = today - 2, restDay = today - 10)
        assertEquals(5, post(inputs(streak = forgiven)).streak)
    }

    @Test
    fun theStreakTapGoesToTheMostRecentUnfinishedCanvas() {
        val older = canvas("older", placed = 10, playedHoursAgo = 30)
        val newer = canvas("newer", placed = 10, playedHoursAgo = 5)
        val done = canvas("done", placed = 100, playedHoursAgo = 4)
        assertEquals(newer, post(inputs(streak = breakingStreak, started = listOf(older, done, newer))).canvas)
        assertNull(post(inputs(streak = breakingStreak, started = listOf(done))).canvas)
    }

    @Test
    fun almostFinishedIsPastHalfwayAndPlayedThisWeekMostRecentFirst() {
        val half = canvas("half", placed = 50)
        val stale = canvas("stale", placed = 90, playedHoursAgo = 7 * 24 + 1)
        val unknown = canvas("unknown", placed = 90, playedHoursAgo = null)
        assertEquals(ReminderOutcome.Quiet(false), ReminderReasons.decide(inputs(started = listOf(half, stale, unknown))))
        val recent = canvas("recent", placed = 60, playedHoursAgo = 4)
        assertEquals(recent, post(inputs(started = listOf(almost, recent))).canvas)
        assertEquals(60, recent.percent)
    }

    @Test
    fun thePercentIsRoundedDown() {
        assertEquals(99, canvas("x", placed = 999, total = 1000).percent)
        assertEquals(82, canvas("x", placed = 821, total = 1000).percent)
    }

    @Test
    fun aPhotoCanvasIsAlmostFinishedWithoutAPictureId() {
        val photo = canvas("photo/abc", placed = 80, photo = true)
        val p = post(inputs(started = listOf(photo)))
        assertEquals(ReminderReason.ALMOST_FINISHED, p.reason)
        assertNull(p.pictureId)
        assertEquals(true, p.canvas?.photo)
    }

    @Test
    fun aSkipLeavesOnlyTheStreak() {
        val justPlayed = canvas("venice", placed = 82, playedHoursAgo = 2)
        val all = inputs(drops = listOf("lighthouse"), pick = "huts", started = listOf(justPlayed))
        assertEquals(ReminderOutcome.Quiet(skipped = true), ReminderReasons.decide(all))
        assertEquals(ReminderOutcome.Quiet(skipped = true), ReminderReasons.decide(inputs(drops = listOf("lighthouse"), foreground = true)))
        // The streak still applies while nothing is finished today.
        assertEquals(ReminderReason.STREAK, post(all.copy(streak = breakingStreak)).reason)
        assertEquals(ReminderReason.STREAK, post(inputs(foreground = true, streak = breakingStreak)).reason)
        // A gem placed just over 3 h ago doesn't skip.
        assertEquals(ReminderReason.DROP, post(inputs(drops = listOf("lighthouse"), started = listOf(canvas("x", 1, playedHoursAgo = 4)))).reason)
    }

    @Test
    fun aLateRunPostsNothing() {
        assertEquals(ReminderOutcome.Late(121), ReminderReasons.decide(inputs(drops = listOf("lighthouse"), now = scheduled + 121 * 60_000L)))
        assertEquals(ReminderReason.DROP, post(inputs(drops = listOf("lighthouse"), now = scheduled + 120 * 60_000L)).reason)
        // A later date is late even under 2 h (a 23:30 reminder run at 00:10).
        val lateNight = FakeClock("2026-10-20T23:30")
        val nextDay = lateNight.at("2026-10-21T00:10")
        val run = ReminderInputs(lateNight.now, lateNight.day.today(), nextDay, ReminderSchedule.dayOf(nextDay, lateNight.zone), false, listOf("x"), null, emptyList(), StreakState())
        assertEquals(ReminderOutcome.Late(40), ReminderReasons.decide(run))
    }

    @Test
    fun copyNamesThePictureAndStatesOnlyTheFact() {
        val drop = ReminderOutcome.Post(ReminderReason.DROP, pictureId = "lighthouse")
        assertEquals(ReminderText("Tonight: Lighthouse", "A new picture, free to start today."), ReminderCopy.of(drop, "Lighthouse", null, vip = false))
        val today = ReminderOutcome.Post(ReminderReason.TODAY, pictureId = "huts")
        assertEquals(
            ReminderText("Today's picture: Beach Huts", "A Quick picture, free to start today."),
            ReminderCopy.of(today, "Beach Huts", CanvasSize.QUICK, vip = false),
        )
        assertEquals(
            ReminderText("Today's pick: Beach Huts", "A Quick picture, ready when you are."),
            ReminderCopy.of(today, "Beach Huts", CanvasSize.QUICK, vip = true),
        )
        val streak = ReminderOutcome.Post(ReminderReason.STREAK, streak = 4)
        assertEquals(ReminderText("4 days in a row", "Finish any picture today to keep it going."), ReminderCopy.of(streak, null, null, vip = false))
        val venice = ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, pictureId = "venice", canvas = canvas("venice", placed = 829, total = 1000))
        assertEquals(ReminderText("You're 82% through Venice", "Pick up where you left off."), ReminderCopy.of(venice, "Venice", null, vip = false))
    }

    /** T43: an unfinished mystery is never named; its drop has its own line (design system, notification table). */
    @Test
    fun aMysteryIsNeverNamed() {
        val drop = ReminderOutcome.Post(ReminderReason.DROP, pictureId = "lantern")
        assertEquals(
            ReminderText("Tonight's mystery picture", "A new picture, hidden until you finish it."),
            ReminderCopy.of(drop, "Lantern", null, vip = false, mystery = true),
        )
        val today = ReminderOutcome.Post(ReminderReason.TODAY, pictureId = "lantern")
        assertEquals("Today's picture: Mystery picture", ReminderCopy.of(today, "Lantern", CanvasSize.QUICK, vip = false, mystery = true).title)
        val almost = ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, pictureId = "lantern", canvas = canvas("lantern", placed = 829, total = 1000))
        assertEquals("You're 82% through your mystery picture", ReminderCopy.of(almost, "Lantern", null, vip = false, mystery = true).title)
        assertEquals("Tonight: Lantern", ReminderCopy.of(drop, "Lantern", null, vip = false).title, "R1: an ordinary drop is named")
    }

    @Test
    fun aPhotoCanvasIsNeverNamed() {
        val photo = ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, canvas = canvas("photo/abc", placed = 82, photo = true))
        assertEquals("You're 82% through your photo canvas", ReminderCopy.of(photo, "Grandma's garden", null, vip = false).title)
    }

    @Test
    fun theAskComesAfterTheFirstFinishAndOnceMoreAfterTheThird() {
        assertNull(ReminderAsk.attemptDue(ReminderAskState.NOT_ASKED, finishes = 0, remindersOn = false))
        assertEquals(1, ReminderAsk.attemptDue(ReminderAskState.NOT_ASKED, finishes = 1, remindersOn = false))
        // A skipped ask waits for the next finish.
        assertEquals(1, ReminderAsk.attemptDue(ReminderAskState.NOT_ASKED, finishes = 2, remindersOn = false))
        val notNow = ReminderAsk.afterNotNow(ReminderAskState.NOT_ASKED)
        assertNull(ReminderAsk.attemptDue(notNow, finishes = 2, remindersOn = false))
        assertEquals(2, ReminderAsk.attemptDue(notNow, finishes = 3, remindersOn = false))
        assertEquals(ReminderAskState.DONE, ReminderAsk.afterNotNow(notNow))
        assertNull(ReminderAsk.attemptDue(ReminderAskState.DONE, finishes = 10, remindersOn = false))
        // Already on (from Settings): no ask.
        assertNull(ReminderAsk.attemptDue(ReminderAskState.NOT_ASKED, finishes = 1, remindersOn = true))
    }
}
