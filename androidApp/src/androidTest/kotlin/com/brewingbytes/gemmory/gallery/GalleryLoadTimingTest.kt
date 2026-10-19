package com.brewingbytes.gemmory.gallery

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.PerfTest
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.remote.CheckOutcome
import com.brewingbytes.gemmory.remote.FakeSource
import com.brewingbytes.gemmory.remote.TestCatalog
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gallery's first load on a fresh install (#31): every bundled summary decodes its frozen grid from assets. Logs
 * the store's summaries() time over [RUNS] runs and the launch-to-starter-tile time under [TAG]; the first test asserts
 * nothing, since an emulator's numbers only compare builds. The second (T36, eng D12) adds a year of daily drops (365
 * remote pictures) and fails if summaries() takes more than twice as long as with the bundled pictures alone. Left
 * out of connectedDebugAndroidTest; run it with `am instrument -w -e class
 * com.brewingbytes.gemmory.gallery.GalleryLoadTimingTest ...`.
 */
@PerfTest
@RunWith(AndroidJUnit4::class)
class GalleryLoadTimingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun freshInstall() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
    }

    @After
    fun close() {
        scenario?.close()
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        app.resetRemoteForTest()
    }

    private val app get() = context.applicationContext as GemmoryApp

    private fun medianSummariesMs(): Double = (1..RUNS).map {
        val t = SystemClock.elapsedRealtimeNanos()
        runBlocking { app.store.summaries() }
        (SystemClock.elapsedRealtimeNanos() - t) / 1e6
    }.sorted()[RUNS / 2]

    /** T36, eng D12: unstarted remote pictures come from catalog metadata, so a year of drops costs little. */
    @Test
    fun theGalleryHoldsAtAYearOfDrops() {
        val catalog = TestCatalog()
        val source = FakeSource(catalog)
        File(context.filesDir, StorageLayout.REMOTE).deleteRecursively()
        app.resetRemoteForTest(source, listOf(TestCatalog.KEY))
        medianSummariesMs()
        val bundledMs = medianSummariesMs()

        val sizes = CanvasSize.entries
        repeat(REMOTE) { i -> catalog.add("drop-%03d".format(i), sizes[i % sizes.size], salt = i * 7919, publish = false) }
        catalog.publish()
        assertEquals(CheckOutcome.UPDATED, runBlocking { app.remote.check(force = true) })
        val start = SystemClock.elapsedRealtimeNanos()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        val tileMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
        val (pictures, _) = runBlocking { app.store.summaries() }
        assertEquals(REMOTE, pictures.count { it is CanvasSummary.Listed })
        val remoteMs = medianSummariesMs()
        Log.i(TAG, "remote=$REMOTE launchToStarterTile=%.0fms summaries bundledOnly=%.1fms withRemote=%.1fms".format(tileMs, bundledMs, remoteMs))
        assertTrue(remoteMs <= 2 * bundledMs, "summaries() with $REMOTE drops took %.1fms, over twice %.1fms".format(remoteMs, bundledMs))
    }

    @Test
    fun timeTheFirstGalleryLoad() {
        val start = SystemClock.elapsedRealtimeNanos()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tile-bundled-starter")).fetchSemanticsNodes().isNotEmpty() }
        val tileMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6

        val store = (context.applicationContext as GemmoryApp).store
        val runs = (1..RUNS).map {
            val t = SystemClock.elapsedRealtimeNanos()
            runBlocking { store.summaries() }
            (SystemClock.elapsedRealtimeNanos() - t) / 1e6
        }.sorted()
        // The gallery renders every tile's thumbnail before it shows any (GalleryViewModel.tile), at its cell size.
        val (pictures, _) = runBlocking { store.summaries() }
        fun renderMs(summaries: List<CanvasSummary>): Double {
            val t = SystemClock.elapsedRealtimeNanos()
            for (summary in summaries.filterIsInstance<CanvasSummary.Playable>()) {
                val state = summary.state
                GemRenderer.render(state.pattern, (240 / state.pattern.width).coerceIn(2, 10), state::isPlaced).recycle()
            }
            return (SystemClock.elapsedRealtimeNanos() - t) / 1e6
        }
        val renderMs = renderMs(pictures)
        val before31Ms = renderMs(pictures.filter { it.ref.id in BEFORE_31 })
        Log.i(
            TAG,
            "pictures=${BundledPictures.ALL.size} launchToStarterTile=%.0fms summaries median=%.1fms min=%.1fms max=%.1fms renderAllThumbs=%.0fms renderThe7Before31=%.0fms"
                .format(tileMs, runs[runs.size / 2], runs.first(), runs.last(), renderMs, before31Ms),
        )
    }

    private companion object {
        const val TAG = "GalleryLoadTiming"
        const val RUNS = 7

        /** A year of daily drops. */
        const val REMOTE = 365

        /** The catalog before #31, to compare thumbnail cost against. */
        val BEFORE_31 = setOf("starter", "puppy", "cat", "couple", "sunflowers", "sunset", "cottage")
    }
}
