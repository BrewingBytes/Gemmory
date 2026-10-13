package com.brewingbytes.gemmory.play

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.PlaceResult
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Kill-and-resume on the device filesystem with real time (eng D8, design D25). A process death is a store whose
 * scope dies with no further writes; the reopen is a new store on the same files, as after a restart.
 */
@RunWith(AndroidJUnit4::class)
class KillAndResumeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = File(instrumentation.targetContext.filesDir, "kill-test").also { it.deleteRecursively() }
    private val ref = CanvasRef(CanvasKind.PHOTO, "kill1")
    private val scopes = mutableListOf<CoroutineScope>()

    // Medium-sized: 78×104, 24 colors.
    private val pattern = Pattern(
        width = 78,
        height = 104,
        palette = List(24) { PaletteColor(it + 1, 0xFF000000.toInt() or (it * 0x0A0908), Lab(it * 4.0, 0.0, 0.0)) },
        cells = IntArray(78 * 104) { (it / 7) % 24 },
    )

    @After
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        root.deleteRecursively()
    }

    /** A fresh "process": its own scope and single writer, sweeping at start like the app does. */
    private fun newStore(): Pair<CanvasStore, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        return CanvasStore(root, scope, Dispatchers.IO.limitedParallelism(1)) to scope
    }

    private fun open(store: CanvasStore): PlaySession = runBlocking {
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(ref))
        lateinit var session: PlaySession
        instrumentation.runOnMainSync { session = store.start(opened) }
        session
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** Places the next unplaced gem of the selected color. */
    private fun placeNext(session: PlaySession) {
        val slot = session.selectedSlot
        val cell = (0 until session.cellCount).first { !session.isPlaced(it) && pattern.cells[it] == slot }
        onMain { assertIs<PlaceResult.Placed>(session.place(cell)) }
    }

    @Test
    fun killWhilePlayingLosesAtMostFiveSeconds() {
        val (store, scope) = newStore()
        assertEquals(WriteResult.Written, runBlocking { store.create(ref, pattern) })
        val session = open(store)

        // A steady drag-like stream: one gem every 50 ms for 7.5 s, so the 5 s max wait fires mid-stream.
        val placedAt = mutableListOf<Long>()
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < 7_500) {
            placeNext(session)
            placedAt += SystemClock.elapsedRealtime()
            SystemClock.sleep(50)
        }
        val killedAt = SystemClock.elapsedRealtime()
        var fullLog = IntArray(0)
        onMain { fullLog = session.placedLogForTest() }
        scope.cancel() // process death: no onStop, no further writes

        val (store2, _) = newStore()
        val resumed = open(store2)
        val kept = resumed.placedCount
        val lost = placedAt.size - kept
        Log.i(TAG, "placed ${placedAt.size}, kept $kept, lost $lost")
        assertTrue(kept > 0, "the 5 s max-wait write never happened")
        // What's kept is exactly the first `kept` placements, in order.
        var resumedLog = IntArray(0)
        onMain { resumedLog = resumed.placedLogForTest() }
        assertContentEquals(fullLog.copyOf(kept), resumedLog)
        // Every lost placement was made in the last 5 s (plus a little scheduling slack).
        if (lost > 0) {
            val oldestLost = placedAt[kept]
            assertTrue(killedAt - oldestLost <= 5_000 + SLACK_MS, "lost ${killedAt - oldestLost} ms of placements")
        }
    }

    @Test
    fun killAfterOnStopLosesNothingAndRestoresTheView() {
        val (store, scope) = newStore()
        assertEquals(WriteResult.Written, runBlocking { store.create(ref, pattern) })
        val session = open(store)
        repeat(30) { placeNext(session) }
        onMain {
            session.viewport.onLayout(1080, 1700, 2.625f)
            session.select(5)
            session.viewport.set(31f, 40.5f, 60.25f)
        }
        val zoom = session.viewport.zoomDp
        val centerX = session.viewport.centerX
        val centerY = session.viewport.centerY
        var log = IntArray(0)
        onMain { log = session.placedLogForTest() }

        // Activity onStop, then the system kills the backgrounded process.
        lateinit var stopped: kotlinx.coroutines.Deferred<Unit>
        onMain { stopped = session.onStop() }
        runBlocking { stopped.await() }
        scope.cancel()

        val (store2, _) = newStore()
        val resumed = open(store2)
        onMain { resumed.viewport.onLayout(1080, 1700, 2.625f) }
        var resumedLog = IntArray(0)
        onMain { resumedLog = resumed.placedLogForTest() }
        assertContentEquals(log, resumedLog)
        assertEquals(5, resumed.selectedSlot)
        assertEquals(zoom, resumed.viewport.zoomDp)
        assertEquals(centerX, resumed.viewport.centerX, 1e-3f)
        assertEquals(centerY, resumed.viewport.centerY, 1e-3f)
    }

    @Test
    fun leavingTheCanvasWritesBeforeItReopens() {
        val (store, _) = newStore()
        assertEquals(WriteResult.Written, runBlocking { store.create(ref, pattern) })
        val session = open(store)
        repeat(12) { placeNext(session) }
        onMain { session.close() }
        // Reopened right away in the same process: open waits for the last write of the closed session.
        val reopened = open(store)
        assertEquals(12, reopened.placedCount)
    }

    private companion object {
        const val TAG = "KillAndResumeTest"
        const val SLACK_MS = 250L
    }
}
