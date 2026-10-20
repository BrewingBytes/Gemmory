package com.brewingbytes.gemmory.reminder

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.ads.AdGateway
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.ads.RewardedLoad
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.TodaysPictures
import com.brewingbytes.gemmory.gallery.TODAY_TAG
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.policy.ReminderReason
import com.brewingbytes.gemmory.policy.ReminderSchedule
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.ConsentFirst
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The evening reminder (T35) through the real app, with the app's clock set by the test: the alarm's broadcast posts
 * Today's picture and the gallery's card names the same one, a reboot re-arms it, turning it off disables the
 * receivers, taps land where the navigation map says, an evening run never ends the first session, and the saved
 * consent is applied before anything logs.
 */
@RunWith(AndroidJUnit4::class)
class ReminderFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val zone = TimeZone.currentSystemDefault()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var now = 0L
    private lateinit var scenario: ActivityScenario<MainActivity>

    private object NoAds : AdGateway {
        override val sdkReady = true
        override fun online() = true
        override suspend fun ensureConsent(activity: Activity) = true
        override suspend fun start() = Unit
        override fun preloadRewarded() = Unit
        override suspend fun loadRewarded() = RewardedLoad.Loaded
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: (shown: Boolean) -> Unit) = false
        override fun preloadInterstitial() = Unit
        override fun showInterstitial(activity: Activity, onShown: () -> Unit) = false
    }

    /** A fixed evening far from the real date. */
    private val evening = LocalDate(2027, 3, 9)

    private fun at(time: LocalTime, date: LocalDate = evening) = LocalDateTime(date, time).toInstant(zone).toEpochMilliseconds()

    @Before
    fun setUp() {
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        now = at(LocalTime(20, 0))
        app.testNowMillis = { now }
        app.resetMonetizationForTest(NoAds)
        // An empty catalog from a fake source: the evening's check never reaches GitHub.
        app.resetRemoteForTest(FakeSource(TestCatalog()), listOf(TestCatalog.KEY))
        app.resetTodaysForTest()
        app.resetRemindersForTest()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        app.reminders.setOn(false)
        clearState()
        app.testNowMillis = null
        app.resetRemoteForTest()
        app.resetMonetizationForTest()
        app.resetTodaysForTest()
        app.resetRemindersForTest()
    }

    private fun clearState() {
        notifications.cancelAll()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        for (file in listOf(PictureUnlocks.FILE, TodaysPictures.FILE, ReminderPrefs.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) =
        compose.waitUntil(matcher.description, timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun posted(timeoutMs: Long = 12_000): android.app.Notification? {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            notifications.activeNotifications.firstOrNull { it.id == ReminderNotifier.NOTIFICATION }?.let { return it.notification }
            Thread.sleep(100)
        }
        return null
    }

    /**
     * Fires the evening alarm's broadcast now, as AlarmManager would, with the time it was set for. First waits for
     * the process to count as backgrounded: Android reports that ~700 ms after the last activity stops, and until then
     * the reminder rightly skips the evening (an earlier test's activity may just have closed).
     */
    private fun fireAlarm(scheduledAt: Long = app.reminders.scheduledAt) {
        waitUntil("the app in the background") {
            var started = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                started = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
            !started
        }
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_EVENING)
                .putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, scheduledAt),
        )
    }

    private fun waitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out: $what" }
            Thread.sleep(50)
        }
    }

    private fun enabled(receiver: Class<*>) =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, receiver)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun tap(reason: ReminderReason, picture: String? = null, canvas: String? = null, day: Long = app.dayClock.today()) =
        Intent(context, MainActivity::class.java)
            .setAction(ReminderNotifier.ACTION_TAP)
            .putExtra(ReminderNotifier.EXTRA_REASON, reason.name)
            .putExtra(ReminderNotifier.EXTRA_DAY, day)
            .also { i ->
                picture?.let { i.putExtra(ReminderNotifier.EXTRA_PICTURE, it) }
                canvas?.let { i.putExtra(ReminderNotifier.EXTRA_CANVAS, it) }
            }

    @Test
    fun theAlarmPostsTodaysPictureAndTheCardNamesTheSameOne() {
        app.reminders.setOn(true)
        assertEquals(at(LocalTime(20, 30)), app.reminders.scheduledAt)
        now = at(LocalTime(20, 41))
        fireAlarm()
        val notification = assertNotNull(posted(), "the evening's reminder")
        val picked = app.todays.freeToday().single()
        val title = BundledPictures.byId(picked)!!.title
        assertEquals("Today's picture: $title", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals(ReminderNotifier.CHANNEL, notification.channelId)
        assertEquals(android.app.Notification.VISIBILITY_PUBLIC, notification.visibility)
        // Re-armed for tomorrow evening once the run ends.
        waitUntil("re-armed") { app.reminders.scheduledAt == at(LocalTime(20, 30), LocalDate(2027, 3, 10)) }
        // The gallery's card, made after the reminder picked, names the same picture (eng D3).
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // The card reads as one node (its children's text is cleared), so its description names the picture.
        waitFor(hasTestTag(TODAY_TAG) and hasContentDescription("Today's picture: $title,", substring = true))
    }

    @Test
    fun aRebootReArmsItAndTurningItOffDisablesTheReceivers() {
        app.reminders.setOn(true)
        assertTrue(enabled(ReminderReceiver::class.java) && enabled(ReminderRescheduleReceiver::class.java))
        now = at(LocalTime(21, 15))
        ReminderRescheduleReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(at(LocalTime(20, 30), LocalDate(2027, 3, 10)), app.reminders.scheduledAt)
        app.reminders.setMinutes(ReminderSchedule.EARLIEST)
        assertEquals(at(LocalTime(17, 0), LocalDate(2027, 3, 10)), app.reminders.scheduledAt)
        app.reminders.setOn(false)
        assertEquals(0L, app.reminders.scheduledAt)
        assertTrue(!enabled(ReminderReceiver::class.java) && !enabled(ReminderRescheduleReceiver::class.java))
    }

    @Test
    fun aTapOnTodaysPictureOpensItsCanvas() {
        val picked = runBlocking { app.todays.pick(emptySet(), vip = false, emptyList(), emptySet()) { false } }!!
        scenario = ActivityScenario.launch(tap(ReminderReason.TODAY, picture = picked))
        waitFor(hasTestTag("play-canvas"))
    }

    @Test
    fun aStaleOrCraftedTapLandsOnTheGallery() {
        val picked = runBlocking { app.todays.pick(emptySet(), vip = false, emptyList(), emptySet()) { false } }!!
        // Yesterday's notification, tapped after midnight.
        scenario = ActivityScenario.launch(tap(ReminderReason.TODAY, picture = picked, day = app.dayClock.today() - 1))
        waitFor(hasTestTag("tile-bundled-starter"))
        assertTrue(compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isEmpty())
        scenario.close()
        // A locked picture that isn't today's, and a canvas that doesn't exist.
        val locked = BundledPictures.ALL.first { it.locked && it.id != picked }.id
        scenario = ActivityScenario.launch(tap(ReminderReason.DROP, picture = locked))
        waitFor(hasTestTag("tile-bundled-starter"))
        assertTrue(compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isEmpty())
        scenario.close()
        scenario = ActivityScenario.launch(tap(ReminderReason.ALMOST_FINISHED, canvas = "PHOTO/nope"))
        waitFor(hasTestTag("tile-bundled-starter"))
        assertTrue(compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isEmpty())
    }

    /**
     * Android 10-11: Back finishes the root activity, and Recents relaunches the task's original intent (the
     * notification's) with FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY. That's not a tap: the gallery, not the canvas again.
     */
    @Test
    fun aRelaunchFromRecentsIsNotATap() {
        val picked = app.todays.pick(emptySet(), vip = false, emptyList(), emptySet()) { false }!!
        assertNull(ReminderTap.of(tap(ReminderReason.TODAY, picture = picked).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
        scenario = ActivityScenario.launch(tap(ReminderReason.TODAY, picture = picked).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        waitFor(hasTestTag("tile-bundled-starter"))
        assertTrue(compose.onAllNodes(hasTestTag("play-canvas")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun anAlmostFinishedTapOpensThatCanvas() {
        val starter = CanvasRef(CanvasKind.BUNDLED, BundledPictures.STARTER.id)
        runBlocking {
            val session = app.store.start(assertIs<CanvasStore.Opened.Ready>(app.store.open(starter)))
            for (cell in 0 until 5) {
                session.select(session.pattern.cells[cell])
                session.place(cell)
            }
            session.close()
        }
        scenario = ActivityScenario.launch(tap(ReminderReason.ALMOST_FINISHED, canvas = CanvasStore.reminderKey(starter)))
        waitFor(hasTestTag("play-canvas"))
    }

    /** eng D11: the evening run doesn't count as a cold start, so it can't end a new player's first session. */
    @Test
    fun anEveningRunLeavesTheFirstSessionAlone() {
        val ads = File(context.filesDir, StorageLayout.LEDGER_ADS)
        val before = ads.takeIf { it.isFile }?.readBytes()
        val sessionOver = app.monetization.firstSessionOver.value
        app.reminders.setOn(true)
        now = at(LocalTime(20, 35))
        fireAlarm()
        assertNotNull(posted())
        assertContentEquals(before, ads.takeIf { it.isFile }?.readBytes())
        assertEquals(sessionOver, app.monetization.firstSessionOver.value)
    }

    /** An alarm whose evening is over (the phone slept through it) posts nothing and is re-armed. */
    @Test
    fun aLateAlarmPostsNothing() {
        app.reminders.setOn(true)
        val due = app.reminders.scheduledAt
        now = due + 3 * 60 * 60_000L
        fireAlarm(due)
        waitUntil("re-armed") { app.reminders.scheduledAt == at(LocalTime(20, 30), LocalDate(2027, 3, 10)) }
        assertEquals(null, posted(timeoutMs = 1_000))
    }

    /** eng D6: the saved consent reached Analytics in onCreate, before any component could log. */
    @Test
    fun theSavedConsentWasAppliedBeforeAnythingLogged() {
        val gate = assertIs<ConsentFirst>(app.analytics)
        assertEquals(app.consent.consentMode.value, gate.applied)
    }
}
