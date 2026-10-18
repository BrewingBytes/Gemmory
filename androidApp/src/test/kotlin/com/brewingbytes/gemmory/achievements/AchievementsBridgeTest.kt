package com.brewingbytes.gemmory.achievements

import android.app.Activity
import android.content.Intent
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.policy.Achievement
import com.brewingbytes.gemmory.policy.AchievementCodec
import com.brewingbytes.gemmory.policy.GameEvent
import com.brewingbytes.gemmory.storage.FileStateStore
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bridge to Play Games (#49) through a fake session: offline, process death, sign-in and the backfill gate. */
class AchievementsBridgeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    /** A fake games client: [online] decides whether sends are confirmed; it records what reached the "server". */
    private class FakeSession(var player: String? = "p1", var online: Boolean = true, var signIn: SignIn = SignIn.Declined) : GamesSession {
        val server = mutableListOf<String>()
        var signIns = 0

        override suspend fun player() = player

        override suspend fun signIn(): SignIn {
            signIns++
            if (signIn is SignIn.SignedIn) player = (signIn as SignIn.SignedIn).player
            return signIn
        }

        override suspend fun unlock(id: String): Boolean {
            if (online) server += id
            return online
        }

        override suspend fun setSteps(id: String, steps: Int): Boolean {
            if (online) server += "$id=$steps"
            return online
        }

        override suspend fun achievementsIntent(): Intent? = null
    }

    private val backend = object : GamesBackend {
        override val available = true

        override fun session(activity: Activity): GamesSession = error("tests pass a session")
    }

    private fun achievements() = Achievements(
        store = FileStateStore(File(tmp.root, "achievements/state"), AchievementCodec::encode, AchievementCodec::decode, NonFatalReporter { }),
        backend = backend,
        ids = { it.name.lowercase() },
        scope = scope,
        worker = Dispatchers.IO.limitedParallelism(1),
    )

    private fun Achievements.backfilled(): Achievements = apply { runBlocking { backfill { emptyList<GameEvent.Finished>() to 0 }.join() } }

    @Test
    fun anUnlockEarnedOfflineIsSentOnceOnline() = runBlocking<Unit> {
        val a = achievements().backfilled()
        a.record(GameEvent.Shared(progress = false)).join()
        val session = FakeSession(online = false)
        a.sync(session)
        assertEquals(emptyList(), session.server, "offline: nothing confirmed")
        session.online = true
        a.sync(session)
        assertEquals(listOf("show_and_tell"), session.server)
        a.sync(session)
        assertEquals(listOf("show_and_tell"), session.server, "confirmed once, never sent again")
    }

    @Test
    fun whatWasntConfirmedSurvivesProcessDeath() = runBlocking<Unit> {
        val first = achievements().backfilled()
        first.record(GameEvent.Finished(CanvasSize.QUICK, photo = false)).join()
        first.sync(FakeSession(player = null))
        // A new process reads the same file.
        val session = FakeSession()
        achievements().sync(session)
        assertEquals(listOf("first_sparkle", "quick_study", "tray_regular=1", "gallery_wall=1"), session.server)
    }

    @Test
    fun incrementalsGoAsAbsoluteStepsSoAReplayCantDoubleCount() = runBlocking<Unit> {
        val a = achievements().backfilled()
        repeat(2) { a.record(GameEvent.Finished(null, photo = false)).join() }
        val session = FakeSession()
        a.sync(session)
        a.record(GameEvent.Finished(null, photo = false)).join()
        a.sync(session)
        a.sync(session)
        assertEquals(listOf("first_sparkle", "tray_regular=2", "gallery_wall=2", "tray_regular=3", "gallery_wall=3"), session.server)
    }

    @Test
    fun nobodySignedInIsSilentAndKeepsEverything() = runBlocking<Unit> {
        val a = achievements().backfilled()
        a.record(GameEvent.StreakDay(3)).join()
        val session = FakeSession(player = null)
        a.sync(session)
        assertEquals(0, session.signIns, "a sync never asks the player to sign in")
        assertEquals(emptyMap(), a.state.confirmed)
    }

    @Test
    fun theRowSignsInOnATapAndSendsBeforeOpening() = runBlocking<Unit> {
        val a = achievements().backfilled()
        a.record(GameEvent.StreakDay(7)).join()
        val session = FakeSession(player = null, signIn = SignIn.SignedIn("p9"))
        val (result, _) = a.open(session)
        assertEquals(listOf("a_week_of_gems=7", "a_month_of_gems=7"), session.server)
        // The fake has no screen to give, which the row shows as a failure.
        assertEquals(OpenResult.FAILED, result)
        assertEquals("p9", a.state.player)
    }

    @Test
    fun aDeclinedSignInShowsNothingAndAFailedOneSaysSo() = runBlocking<Unit> {
        val a = achievements().backfilled()
        assertEquals(OpenResult.DECLINED, a.open(FakeSession(player = null, signIn = SignIn.Declined)).first)
        assertEquals(OpenResult.FAILED, a.open(FakeSession(player = null, signIn = SignIn.Failed)).first)
    }

    @Test
    fun eventsWaitForTheBackfillSoNothingCountsTwice() = runBlocking<Unit> {
        val a = achievements()
        val onPhone = listOf(GameEvent.Finished(CanvasSize.BIG, photo = false))
        a.backfill { onPhone to 2 }
        a.record(GameEvent.Finished(CanvasSize.MEDIUM, photo = false)).join()
        assertEquals(2, a.state.finishes)
        assertEquals(2, a.state.bestStreak)
        assertTrue(Achievement.THE_LONG_EVENING in a.state.unlocked)
        // A restart doesn't count the phone again.
        val again = achievements()
        again.backfill { (onPhone + onPhone) to 9 }.join()
        assertEquals(2, again.state.finishes)
    }

    @Test
    fun aMissingIdIsKeptUntilABuildHasIt() = runBlocking<Unit> {
        val a = Achievements(
            store = FileStateStore(File(tmp.root, "achievements/state"), AchievementCodec::encode, AchievementCodec::decode, NonFatalReporter { }),
            backend = backend,
            ids = { if (it == Achievement.FIRST_SPARKLE) "" else it.name.lowercase() },
            scope = scope,
            worker = Dispatchers.IO.limitedParallelism(1),
        ).backfilled()
        a.record(GameEvent.Finished(null, photo = false)).join()
        val session = FakeSession()
        a.sync(session)
        assertEquals(listOf("tray_regular=1", "gallery_wall=1"), session.server)
        val next = FakeSession()
        achievements().sync(next)
        assertEquals(listOf("first_sparkle"), next.server, "a build with the id sends it")
    }

    @Test
    fun aHungPlayGamesCallNeverHoldsUpCounting() = runBlocking<Unit> {
        val a = achievements().backfilled()
        a.record(GameEvent.Finished(null, photo = false)).join()
        // Without Play Games' module a task never completes (lessons, 2026-10-18).
        val hung = object : GamesSession by FakeSession() {
            override suspend fun unlock(id: String): Boolean = awaitCancellation()
        }
        val stuck = launch(Dispatchers.Default) { a.sync(hung) }
        withTimeout(5_000) { a.record(GameEvent.Shared(progress = false)).join() }
        assertTrue(Achievement.SHOW_AND_TELL in a.state.unlocked)
        stuck.cancel()
        val session = FakeSession()
        withTimeout(5_000) { a.sync(session) }
        assertEquals(listOf("first_sparkle", "show_and_tell", "tray_regular=1", "gallery_wall=1"), session.server, "a cancelled round frees the next one")
    }

    @Test
    fun recordingTicksChangesOnlyWhenSomethingWasEarned() = runBlocking<Unit> {
        val a = achievements().backfilled()
        val before = a.changes.value
        a.record(GameEvent.Shared(progress = true)).join()
        a.record(GameEvent.Shared(progress = true)).join()
        assertEquals(before + 1, a.changes.value)
    }
}
