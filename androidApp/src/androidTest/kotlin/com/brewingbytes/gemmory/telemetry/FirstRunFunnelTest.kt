package com.brewingbytes.gemmory.telemetry

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.WhereFound
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.storage.StorageLayout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T46: the launch funnel on a real disk and real preferences. A test can't cold-start the app's process (lessons
 * 2026-10-20), so each "process" is a new [FirstRun] over a scratch files dir and test-named preferences.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunFunnelTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = File(context.cacheDir, "first-run-test")
    private val streak = File(files, "streak.xml")
    private val marker = context.getSharedPreferences(MARKER, Context.MODE_PRIVATE)
    private val funnel = context.getSharedPreferences(FUNNEL, Context.MODE_PRIVATE)

    private fun launch() = FirstRun(files, streak, marker, funnel)

    @Before
    @After
    fun clear() {
        files.deleteRecursively()
        files.mkdirs()
        marker.edit().clear().commit()
        funnel.edit().clear().commit()
    }

    @Test
    fun aFreshInstallLogsFirstLaunchAndOneFirstGemThenNeverAgain() {
        File(files, StorageLayout.BUNDLED_CANVASES).mkdirs() // an empty kind dir isn't a canvas
        val first = launch()
        assertTrue(first.freshDisk)
        assertTrue(first.referrerDue())
        first.referrerRead("utm_source=share&utm_medium=bundled")
        first.onFirstForeground(1_000)
        File(files, "${StorageLayout.BUNDLED_CANVASES}/starter").mkdirs()
        assertEquals(2_500, first.gemPlaced(3_500))
        assertNull(first.gemPlaced(4_000))
        assertTrue(first.launchDue())
        assertFalse(first.launchDue())

        val second = launch()
        assertFalse(second.freshDisk, "the marker and the canvas are there now")
        assertFalse(second.referrerDue())
        assertEquals("utm_source=share&utm_medium=bundled", second.referrer)
        second.onFirstForeground(9_000)
        assertFalse(second.launchDue())
        assertNull(second.gemPlaced(10_000))
    }

    @Test
    fun aBackgroundStartDoesntUseUpFresh() {
        assertTrue(launch().freshDisk) // a boot or update receiver's process: no foreground, no marker
        val opened = launch()
        assertTrue(opened.freshDisk)
        opened.onFirstForeground(5_000)
        assertTrue(opened.launchDue())
    }

    @Test
    fun aRestoredOrUpgradedPhoneLogsNeither() {
        for (seed in listOf<() -> Unit>(
            { File(files, "${StorageLayout.BUNDLED_CANVASES}/starter").mkdirs() },
            { File(files, StorageLayout.ACHIEVEMENTS).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1)) },
            { streak.writeText("<map />") },
            { marker.edit().putBoolean("launched", true).commit() },
        )) {
            clear()
            seed()
            val app = launch()
            assertFalse(app.freshDisk)
            assertFalse(app.referrerDue())
            app.onFirstForeground(1_000)
            assertFalse(app.launchDue())
            assertNull(app.gemPlaced(2_000))
        }
    }

    /**
     * T56 (#157, eng R1): the first fresh finish decides "Where did you find Gemmory?" and commits it, with the
     * answers' seed, before the row shows; a later finish, a relaunch, or consent granted later never asks again.
     */
    @Test
    fun theFirstFinishDecidesWhereFoundOnDiskAndNoLaterFinishAsks() {
        val first = launch()
        first.onFirstForeground(1_000)
        val ask = first.firstRunAsk(analyticsGranted = true, seed = 42, firstFinish = true)
        assertEquals(FirstRunAsk.Show(WhereFoundAnswer.order(42)), ask)
        assertEquals(WhereFound.DECIDED.name, funnel.getString("where_found", null), "on disk before the row shows")
        assertEquals(42L, funnel.getLong("where_found_seed", 0))
        assertEquals(FirstRunAsk.No, first.firstRunAsk(analyticsGranted = true, seed = 7, firstFinish = true), "the second finish")
        assertEquals(FirstRunAsk.No, launch().firstRunAsk(analyticsGranted = true, seed = 7, firstFinish = true), "after a relaunch")

        clear()
        val denied = launch()
        denied.onFirstForeground(1_000)
        assertEquals(FirstRunAsk.No, denied.firstRunAsk(analyticsGranted = false, seed = 1, firstFinish = true))
        assertEquals(WhereFound.NOT_SHOWN.name, funnel.getString("where_found", null))
        assertEquals(FirstRunAsk.No, launch().firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true), "consent granted later")
    }

    /** T56: an install that isn't first-run (restored, upgraded) is never asked, and nothing is written for it. */
    @Test
    fun anInstallThatIsntFirstRunIsNeverAsked() {
        marker.edit().putBoolean("launched", true).commit()
        val app = launch()
        app.onFirstForeground(1_000)
        assertEquals(FirstRunAsk.No, app.firstRunAsk(analyticsGranted = true, seed = 1, firstFinish = true))
        assertFalse(funnel.contains("where_found"))
    }

    private companion object {
        const val MARKER = "new_player_test"
        const val FUNNEL = "first_run_test"
    }
}
