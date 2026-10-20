package com.brewingbytes.gemmory.reminder

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.policy.ReminderOutcome
import com.brewingbytes.gemmory.policy.ReminderReason
import com.brewingbytes.gemmory.policy.StreakState
import com.brewingbytes.gemmory.remote.CheckOutcome
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.RemotePictures
import com.brewingbytes.gemmory.remote.RemoteSchedule
import com.brewingbytes.gemmory.remote.TestCatalog
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The evening run (T35): the bounded catalog check (eng D5), the late rule, and what's posted and logged. */
class ReminderReceiverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val catalog = TestCatalog()
    private val source = FakeSource(catalog)

    /** 20:30 UTC on some evening. */
    private var now = 1_792_000_000_000L / RemoteSchedule.DAY_MILLIS * RemoteSchedule.DAY_MILLIS + (20 * 60 + 30) * 60_000L
    private val scheduled = now
    private val clock = DayClock({ now }) { TimeZone.UTC }
    private val events = java.util.Collections.synchronizedList(mutableListOf<AnalyticsEvent>())
    private val posted = mutableListOf<ReminderOutcome.Post>()
    private var allowed = true
    private var checks = 0
    private var started = emptyList<ReminderCanvas>()

    private val remote by lazy {
        RemotePictures(
            filesDir = tmp.root,
            source = source,
            keys = listOf(TestCatalog.KEY),
            appVersion = 13,
            reservedIds = emptySet(),
            clock = clock,
            scope = scope,
            io = Dispatchers.IO.limitedParallelism(1),
            reporter = NonFatalReporter { },
        )
    }

    private fun job(checkTimeoutMs: Long = RemotePictures.REMINDER_CHECK_MS, streak: StreakState = StreakState()) = ReminderJob(
        clock = clock,
        zone = { TimeZone.UTC },
        check = {
            checks++
            remote.checkForReminder(checkTimeoutMs)
        },
        canvases = { started },
        todaysDrops = { remote.todaysDrops().toList() },
        pickToday = { null },
        streak = { streak },
        foreground = { false },
        notify = { post -> allowed.also { if (it) posted += post } },
        analytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        },
    )

    private fun today(): String = LocalDate.fromEpochDays(now / RemoteSchedule.DAY_MILLIS).toString()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun theCheckGetsEightSecondsOfTheTen() {
        assertEquals(8_000L, RemotePictures.REMINDER_CHECK_MS)
        assertTrue(RemotePictures.REMINDER_CHECK_MS < ReminderReceiver.RUN_BUDGET_MS)
        assertTrue(ReminderReceiver.RUN_BUDGET_MS < 10_000L)
    }

    /** A lapsed player's phone hears of tonight's drop: the check fetches the catalog and today's grid only. */
    @Test
    fun aDueCheckBringsTonightsDropAndOnlyItsGrid() = runBlocking<Unit> {
        catalog.add("lighthouse", CanvasSize.MEDIUM, availableFrom = today())
        catalog.add("fox", CanvasSize.QUICK)
        val outcome = job().run(scheduled)
        assertEquals(ReminderOutcome.Post(ReminderReason.DROP, pictureId = "lighthouse"), outcome)
        assertEquals(listOf(outcome), posted)
        assertEquals(listOf(RemotePictures.CATALOG, RemotePictures.SIGNATURE, "grids/lighthouse.gmrg"), source.requests.map { it.first })
        assertEquals(listOf(AnalyticsEvent.reminderShown(ReminderReason.DROP)), events.toList())
        // It skipped a grid, so it isn't a success: the gallery's next check an hour on fetches the rest.
        now += 61 * 60_000L
        assertEquals(CheckOutcome.UPDATED, remote.check())
        assertEquals(setOf("lighthouse", "fox"), remote.listed().map { it.id }.toSet())
    }

    /** eng D5: a check that doesn't finish in time is cancelled, stores no catalog, and the reasons still run. */
    @Test
    fun aSlowCheckIsCancelledWithoutAPartialCatalog() = runBlocking<Unit> {
        catalog.add("lighthouse", CanvasSize.MEDIUM, availableFrom = today())
        source.latencyMs = 600
        val streak = StreakState(length = 4, lastDay = clock.today() - 1, restDay = clock.today() - 3)
        val startedAt = System.nanoTime()
        val outcome = job(checkTimeoutMs = 200, streak = streak).run(scheduled)
        assertTrue((System.nanoTime() - startedAt) / 1_000_000 < 550, "the run didn't wait for the network")
        assertEquals(ReminderReason.STREAK, assertIs<ReminderOutcome.Post>(outcome).reason)
        // Let the blocked request return: the cancelled check must not store anything after it.
        Thread.sleep(1_500)
        assertEquals(emptyList(), remote.listed())
        assertFalse(File(tmp.root, StorageLayout.REMOTE_CATALOG).exists())
    }

    @Test
    fun aCheckThatIsNotDueSendsNothing() = runBlocking<Unit> {
        catalog.add("fox")
        remote.check(force = true)
        source.requests.clear()
        job().run(scheduled)
        assertEquals(emptyList(), source.requests.toList())
    }

    /** More than 2 h late: logged, and neither the network nor the canvases are touched. */
    @Test
    fun aLateRunPostsNothingAndSkipsTheCheck() = runBlocking<Unit> {
        now = scheduled + 135 * 60_000L
        assertEquals(ReminderOutcome.Late(135), job().run(scheduled))
        assertEquals(0, checks)
        assertEquals(emptyList(), posted)
        assertEquals(listOf(AnalyticsEvent.reminderLate(135)), events.toList())
    }

    @Test
    fun aBlockedPostIsNotCountedAsShown() = runBlocking<Unit> {
        allowed = false
        started = listOf(ReminderCanvas("PHOTO/p1", null, placed = 80, total = 100, lastGemMillis = scheduled - 24 * 60 * 60_000L))
        assertEquals(ReminderReason.ALMOST_FINISHED, assertIs<ReminderOutcome.Post>(job().run(scheduled)).reason)
        assertEquals(emptyList(), events.toList())
    }
}
