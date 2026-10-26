package com.brewingbytes.gemmory.streak

import com.brewingbytes.gemmory.copy.strings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.gallery.tileTag
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CoachHint
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.PlayUi
import com.brewingbytes.gemmory.play.PlayViewModel
import com.brewingbytes.gemmory.policy.ReminderAskState
import com.brewingbytes.gemmory.reminder.ReminderPrefs
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.widget.StreakWidget
import com.brewingbytes.gemmory.widget.StreakWidgetMidnightReceiver
import com.brewingbytes.gemmory.widget.StreakWidgetState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The home-screen widget (#103) through the real app: a finish lights it, the midnight refresh (its receiver, as the
 * alarm delivers it) turns a done day into "Not yet today", a broken streak starts over, and a tap opens the streak
 * screen over the gallery, from a cold start or with a canvas open. What the widget shows is what [StreakWidgets]
 * last pushed; placing one on a launcher is the PR's manual check.
 */
@RunWith(AndroidJUnit4::class)
class StreakWidgetFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as GemmoryApp
    private val streakPrefs get() = context.getSharedPreferences("streak", Context.MODE_PRIVATE)
    private var scenario: ActivityScenario<MainActivity>? = null
    private val today = LocalDate.now().toEpochDay()
    private val photo = CanvasRef(CanvasKind.PHOTO, "widget")
    private val refresher = ComponentName(context, StreakWidgetMidnightReceiver::class.java)

    @Before
    fun setUp() {
        clearState()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val hints = context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit()
        CoachHint.entries.forEach { hints.putBoolean(it.key, true) }
        hints.commit()
        app.resetTodaysForTest(enabled = false)
        ReminderPrefs(context.getSharedPreferences(ReminderPrefs.FILE, Context.MODE_PRIVATE)).ask = ReminderAskState.DONE
        app.resetRemindersForTest()
    }

    @After
    fun tearDown() {
        scenario?.close()
        app.testNowMillis = null
        context.packageManager.setComponentEnabledSetting(refresher, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        clearState()
        context.getSharedPreferences("hints", Context.MODE_PRIVATE).edit().clear().commit()
        app.resetStreakForTest()
        app.resetTodaysForTest()
        app.resetRemindersForTest()
    }

    private fun clearState() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
        for (file in listOf("streak", ReminderPrefs.FILE)) context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun seed(length: Int, lastDay: Long, restDay: Long? = null) {
        val edit = streakPrefs.edit().clear().putInt("length", length).putLong("lastDay", lastDay)
        restDay?.let { edit.putLong("restDay", it) }
        edit.commit()
        app.resetStreakForTest(celebrations = false)
    }

    private fun waitPushed(expected: StreakWidgetState) = waitFor("the widget at $expected", 20_000) { app.streakWidgets.pushed.value == expected }

    @Test
    fun aFinishLightsIt() {
        seed(6, today - 1)
        waitPushed(StreakWidgetState(6, lit = false))
        assertEquals("Not yet today", app.streakWidgets.state.value.line(context.strings))
        runBlocking { app.streak.played(CanvasStore.reminderKey(photo)).await() }
        waitPushed(StreakWidgetState(7, lit = true))
        assertEquals("Done today", app.streakWidgets.state.value.line(context.strings))
    }

    @Test
    fun midnightTurnsADoneDayIntoNotYetToday() {
        seed(6, today)
        waitPushed(StreakWidgetState(6, lit = true))
        // The alarm's broadcast after midnight, to the receiver a placed widget enables.
        context.packageManager.setComponentEnabledSetting(refresher, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        val tomorrow = System.currentTimeMillis() + DAY_MS
        app.testNowMillis = { tomorrow }
        context.sendBroadcast(Intent(context, StreakWidgetMidnightReceiver::class.java).setAction(StreakWidgetMidnightReceiver.ACTION_MIDNIGHT))
        waitPushed(StreakWidgetState(6, lit = false))
        assertEquals("Not yet today", app.streakWidgets.state.value.line(context.strings))
    }

    @Test
    fun aBrokenStreakStartsOver() {
        // In its repair window: the repair is on the streak screen, never on the widget.
        seed(6, today - 3, restDay = today - 6)
        waitPushed(StreakWidgetState(0, lit = false))
        assertEquals("Start a streak", app.streakWidgets.state.value.line(context.strings))
    }

    @Test
    fun aTapOpensTheStreakScreenOverTheGallery() {
        seed(6, today - 1)
        scenario = ActivityScenario.launch(StreakWidget.tapIntent(context))
        waitFor("the streak screen", 60_000) { exists(STREAK_SCREEN_TAG) }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("the gallery", 60_000) { shows("My Photos") }
    }

    @Test
    fun aTapWithACanvasOpenLeavesItForTheStreakScreen() {
        seed(6, today - 1)
        runBlocking { app.store.create(photo, A11yTestSupport.pattern(8, 6, 3)) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("the gallery", 60_000) { shows("My Photos") }
        compose.onNode(hasText("My Photos")).performClick()
        waitFor("the photos", 60_000) { exists(tileTag(photo)) }
        compose.onNodeWithTag(tileTag(photo)).performClick()
        waitFor("the canvas") { session()?.viewport?.isLaidOut == true }
        instrumentation.runOnMainSync { repeat(3) { session()!!.placeNext() } }
        // A tap with the app running reaches this activity's onNewIntent (CLEAR_TOP | SINGLE_TOP, as on the launcher);
        // ActivityScenario's task would start a second one, so the intent is delivered the way the launcher's arrives.
        var launched: Intent? = null
        scenario!!.onActivity {
            launched = it.intent
            instrumentation.callActivityOnNewIntent(it, StreakWidget.tapIntent(it))
        }
        waitFor("the streak screen", 60_000) { exists(STREAK_SCREEN_TAG) }
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("the gallery") { shows("My Photos") && !exists(STREAK_SCREEN_TAG) }
        // The canvas was closed and saved on the way.
        assertTrue(runBlocking { app.store.isStarted(photo) }, "the gems placed before the tap are kept")
        // onNewIntent set the tap as the activity's intent; ActivityScenario finds its activity by the one it launched.
        scenario!!.onActivity { it.intent = launched }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** On a timeout, the screen is saved to /data/local/tmp/widget-<what>.png for a look at what showed instead. */
    private fun waitFor(what: String, timeout: Long = 10_000, condition: () -> Boolean) = try {
        compose.waitUntil(what, timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        A11yTestSupport.shell("screencap -p /data/local/tmp/widget-${what.replace(' ', '-')}.png")
        throw e
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario?.onActivity { session = (ViewModelProvider(it)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session }
        return session
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
