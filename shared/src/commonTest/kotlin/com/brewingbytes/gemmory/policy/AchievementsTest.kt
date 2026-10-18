package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.converter.AspectRatio
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.save.Decoded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementsTest {
    private val store = CodecStore(AchievementCodec::encode) { (AchievementCodec.decode(it) as? Decoded.Ok)?.value }

    private fun ledger() = AchievementLedger(store)

    private fun finished(size: CanvasSize?, photo: Boolean = false) = GameEvent.Finished(size, photo)

    private fun AchievementLedger.pending(player: String = P1) = pendingFor(player).map { it.achievement to it.steps }

    @Test
    fun aFreshInstallHasNothingToSend() {
        assertEquals(emptyList(), ledger().pending())
    }

    @Test
    fun theStarterFinishEarnsFirstSparkleAndCountsTowardTheTotals() {
        val l = ledger()
        assertTrue(l.record(finished(null)))
        assertEquals(listOf(Achievement.FIRST_SPARKLE to 1, Achievement.TRAY_REGULAR to 1, Achievement.GALLERY_WALL to 1), l.pending())
    }

    @Test
    fun eachSizeEarnsItsOwn() {
        for ((size, achievement) in listOf(
            CanvasSize.QUICK to Achievement.QUICK_STUDY,
            CanvasSize.MEDIUM to Achievement.STEADY_HANDS,
            CanvasSize.BIG to Achievement.THE_LONG_EVENING,
        )) {
            val state = AchievementRules.apply(AchievementState(), finished(size))
            assertEquals(setOf(Achievement.FIRST_SPARKLE, achievement), state.unlocked, "$size")
        }
    }

    @Test
    fun aPhotoFinishEarnsYourOwnGemAndCountsTowardPhotoAlbum() {
        val state = AchievementRules.apply(AchievementState(), finished(CanvasSize.QUICK, photo = true))
        assertEquals(setOf(Achievement.FIRST_SPARKLE, Achievement.QUICK_STUDY, Achievement.YOUR_OWN_GEM), state.unlocked)
        assertEquals(1, state.progress(Achievement.PHOTO_ALBUM))
        val picture = AchievementRules.apply(state, finished(CanvasSize.QUICK))
        assertEquals(1, picture.progress(Achievement.PHOTO_ALBUM), "a picture isn't a photo canvas")
        assertEquals(2, picture.progress(Achievement.TRAY_REGULAR))
    }

    @Test
    fun sharesEarnByKind() {
        val done = AchievementRules.apply(AchievementState(), GameEvent.Shared(progress = false))
        assertEquals(setOf(Achievement.SHOW_AND_TELL), done.unlocked)
        val progress = AchievementRules.apply(AchievementState(), GameEvent.Shared(progress = true))
        assertEquals(setOf(Achievement.WORK_IN_PROGRESS), progress.unlocked)
    }

    @Test
    fun incrementalStepsStopAtTheirTarget() {
        var state = AchievementState()
        repeat(30) { state = AchievementRules.apply(state, finished(CanvasSize.MEDIUM, photo = true)) }
        assertEquals(5, state.progress(Achievement.TRAY_REGULAR))
        assertEquals(25, state.progress(Achievement.GALLERY_WALL))
        assertEquals(5, state.progress(Achievement.PHOTO_ALBUM))
        assertEquals(30, state.finishes, "the count itself goes on")
    }

    @Test
    fun streaksKeepTheBestLength() {
        val l = ledger()
        (1..8).forEach { l.record(GameEvent.StreakDay(it)) }
        assertFalse(l.record(GameEvent.StreakDay(1)), "a broken streak starting over changes nothing")
        assertEquals(listOf(Achievement.A_WEEK_OF_GEMS to 7, Achievement.A_MONTH_OF_GEMS to 8), l.pending())
    }

    @Test
    fun aConfirmedSendIsNeverSentAgain() {
        val l = ledger()
        l.record(finished(CanvasSize.QUICK))
        l.pendingFor(P1).forEach { l.confirm(P1, it) }
        assertEquals(emptyList(), l.pending())
        assertTrue(l.record(GameEvent.Shared(progress = true)), "a new one-off is earned")
        assertEquals(listOf(Achievement.WORK_IN_PROGRESS to 1), l.pending(), "only the new one")
        l.record(finished(CanvasSize.QUICK))
        assertEquals(
            listOf(Achievement.WORK_IN_PROGRESS to 1, Achievement.TRAY_REGULAR to 2, Achievement.GALLERY_WALL to 2),
            l.pending(),
            "Quick Study is already confirmed; the counters go as absolute steps",
        )
    }

    @Test
    fun aFailedSendStaysPendingAndSurvivesARestart() {
        val l = ledger()
        l.record(finished(CanvasSize.BIG))
        val first = l.pending()
        // Nothing confirmed (offline, or not signed in): the next process sends the same again.
        assertEquals(first, ledger().pending())
    }

    @Test
    fun anotherPlayerIsSentEverythingAgain() {
        val l = ledger()
        l.record(finished(CanvasSize.QUICK))
        l.pendingFor(P1).forEach { l.confirm(P1, it) }
        assertEquals(4, l.pendingFor(P2).size)
        assertEquals(P2, l.state.player)
    }

    @Test
    fun aConfirmationForAPlayerWhoLeftIsIgnored() {
        val l = ledger()
        l.record(finished(null))
        val sent = l.pendingFor(P1).first()
        l.pendingFor(P2)
        l.confirm(P1, sent)
        assertEquals(emptyMap(), l.state.confirmed)
    }

    @Test
    fun aLowerConfirmationNeverLowersTheSteps() {
        val state = AchievementState(finishes = 3, player = P1, confirmed = mapOf(Achievement.TRAY_REGULAR to 3))
        val after = AchievementRules.confirm(state, P1, PendingAchievement(Achievement.TRAY_REGULAR, 2))
        assertEquals(3, after.confirmed[Achievement.TRAY_REGULAR])
    }

    @Test
    fun backfillCountsWhatIsOnThePhoneOnce() {
        val l = ledger()
        val onDisk = listOf(finished(null), finished(CanvasSize.BIG), finished(CanvasSize.QUICK, photo = true))
        assertTrue(l.backfill(onDisk, streak = 4))
        assertEquals(3, l.state.finishes)
        assertEquals(1, l.state.photoFinishes)
        assertEquals(4, l.state.bestStreak)
        assertEquals(
            setOf(Achievement.FIRST_SPARKLE, Achievement.THE_LONG_EVENING, Achievement.QUICK_STUDY, Achievement.YOUR_OWN_GEM),
            l.state.unlocked,
        )
        assertFalse(l.backfill(onDisk, streak = 4), "it runs once")
        assertFalse(ledger().backfill(onDisk + onDisk, streak = 9), "even after a restart")
        assertEquals(3, ledger().state.finishes)
    }

    @Test
    fun backfillOnAnEmptyPhoneStillMarksItDone() {
        val l = ledger()
        assertTrue(l.backfill(emptyList(), streak = 0))
        assertEquals(emptyList(), l.pending())
        assertTrue(l.state.backfilled)
    }

    @Test
    fun eventsAfterTheBackfillAddToIt() {
        val l = ledger()
        l.backfill(List(4) { finished(CanvasSize.MEDIUM) }, streak = 0)
        l.record(finished(CanvasSize.MEDIUM))
        assertEquals(5, l.state.progress(Achievement.TRAY_REGULAR))
    }

    @Test
    fun theFileRoundTrips() {
        val state = AchievementState(
            finishes = 31,
            photoFinishes = 6,
            bestStreak = 12,
            unlocked = setOf(Achievement.FIRST_SPARKLE, Achievement.WORK_IN_PROGRESS, Achievement.THE_LONG_EVENING),
            backfilled = true,
            player = "g04217638819735",
            confirmed = mapOf(Achievement.FIRST_SPARKLE to 1, Achievement.GALLERY_WALL to 25),
        )
        assertEquals(state, (AchievementCodec.decode(AchievementCodec.encode(state)) as Decoded.Ok).value)
        assertEquals(AchievementState(), (AchievementCodec.decode(AchievementCodec.encode(AchievementState())) as Decoded.Ok).value)
    }

    @Test
    fun aDamagedFileIsRefused() {
        val bytes = AchievementCodec.encode(AchievementState(finishes = 2, unlocked = setOf(Achievement.FIRST_SPARKLE)))
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        assertIs<Decoded.Refused>(AchievementCodec.decode(bytes))
        assertNull(store.apply { this.bytes = bytes }.load(), "a refused file starts fresh")
    }

    @Test
    fun codesAreUniqueAndFitTheBitmask() {
        assertEquals(Achievement.entries.size, Achievement.entries.map { it.code }.toSet().size)
        assertTrue(Achievement.entries.all { it.code in 0..31 })
        assertEquals(12, Achievement.entries.size, "the founder's list (2026-10-18)")
    }

    @Test
    fun sizesComeFromTheGridShape() {
        for (size in CanvasSize.entries) for (ratio in AspectRatio.entries) {
            val spec = GridSpec.of(size, ratio)
            assertEquals(size, AchievementRules.sizeOf(spec.width, spec.height))
        }
        assertNull(AchievementRules.sizeOf(GridSpec.STARTER.width, GridSpec.STARTER.height))
    }

    private companion object {
        const val P1 = "player-1"
        const val P2 = "player-2"
    }
}
