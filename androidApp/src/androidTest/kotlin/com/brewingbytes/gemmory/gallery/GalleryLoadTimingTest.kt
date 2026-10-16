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
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.ui.GemRenderer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The gallery's first load on a fresh install (#31): every bundled summary decodes its frozen grid from assets. Logs
 * the store's summaries() time over [RUNS] runs and the launch-to-starter-tile time under [TAG]; it asserts nothing,
 * since an emulator's numbers only compare builds. Left out of connectedDebugAndroidTest; run it with
 * `am instrument -w -e class com.brewingbytes.gemmory.gallery.GalleryLoadTimingTest ...`.
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
            for (summary in summaries) {
                val state = (summary as CanvasSummary.Playable).state
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

        /** The catalog before #31, to compare thumbnail cost against. */
        val BEFORE_31 = setOf("starter", "puppy", "cat", "couple", "sunflowers", "sunset", "cottage")
    }
}
