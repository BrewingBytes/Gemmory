package com.brewingbytes.gemmory.telemetry

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val MARKER = "new_player_test"
        const val FUNNEL = "first_run_test"
    }
}
