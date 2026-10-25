package com.brewingbytes.gemmory.telemetry

import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T56 (#157) on the real `first_run` store: its decision round-trips the preferences and a failed commit shows nothing. */
class FirstRunTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val marker = FakePrefs()
    private val funnel = FakePrefs()
    private val reports = mutableListOf<Throwable>()

    private fun launch() = FirstRun(
        filesDir = tmp.root,
        streakPrefs = File(tmp.root, "streak.xml"),
        marker = marker,
        funnel = funnel,
        reporter = NonFatalReporter { reports += it },
    )

    @Test
    fun theDecisionAndItsOrderSurviveARelaunch() {
        val first = launch()
        first.onFirstForeground(1_000)
        val ask = first.firstRunAsk(analyticsGranted = true, seed = 11)
        assertIs<FirstRunAsk.Show>(ask)
        assertEquals("DECIDED", funnel.values["where_found"])
        assertEquals(11L, funnel.values["where_found_seed"])
        val again = launch()
        assertEquals(FirstRunAsk.No, again.firstRunAsk(analyticsGranted = true, seed = 12), "asked once per install")
        assertTrue(again.whereFoundAnswered(WhereFoundAnswer.VIDEO))
        assertEquals("ANSWERED", funnel.values["where_found"])
        assertFalse(launch().whereFoundAnswered(WhereFoundAnswer.FRIEND))
    }

    @Test
    fun aFirstRunFileFromBeforeT56Loads() {
        funnel.values["first_launch_millis"] = 1_000L
        funnel.values["gem_logged"] = true
        assertIs<FirstRunAsk.Show>(launch().firstRunAsk(analyticsGranted = true, seed = 1))
    }

    @Test
    fun aCommitThatFailsShowsNothingAndIsReported() {
        val run = launch()
        run.onFirstForeground(1_000)
        funnel.commitFails = true
        assertEquals(FirstRunAsk.SaveFailed, run.firstRunAsk(analyticsGranted = true, seed = 1))
        assertEquals(1, reports.size)
        assertEquals(FirstRunAsk.No, run.firstRunAsk(analyticsGranted = true, seed = 1))
    }
}
