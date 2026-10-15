package com.brewingbytes.gemmory.play

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.PerfTest
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.StorageLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import kotlin.math.sin

/**
 * Frame timing of a Big canvas panned and zoomed across every zoom level (#10). Two-finger gestures only, so it
 * navigates the same way on every renderer. Logs per-phase frame durations under [TAG]; it asserts nothing, since an
 * emulator's numbers only compare builds. Left out of connectedDebugAndroidTest; run it with
 * `am instrument -w -e class com.brewingbytes.gemmory.play.CanvasFrameTimingTest ...`; `-e highlight outline|dim`
 * times another Selected color style (#19, default tint).
 */
@PerfTest
@RunWith(AndroidJUnit4::class)
class CanvasFrameTimingTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ref = CanvasRef(CanvasKind.PHOTO, "perf-big")
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
        context.getSharedPreferences("consent", Context.MODE_PRIVATE).edit().putBoolean("firstLaunchDone", true).commit()
        val app = context.applicationContext as GemmoryApp
        val style = HighlightStyle.fromKey(InstrumentationRegistry.getArguments().getString("highlight"))
        instrumentation.runOnMainSync { app.settings.setHighlight(style) }
        Log.i(TAG, "style ${style.key}")
        val store = app.store
        runBlocking { store.create(ref, bigPattern()) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        instrumentation.runOnMainSync { (context.applicationContext as GemmoryApp).settings.setHighlight(HighlightStyle.DEFAULT) }
        File(context.filesDir, StorageLayout.CANVASES).deleteRecursively()
    }

    private fun session(): PlaySession? {
        var session: PlaySession? = null
        scenario.onActivity { activity ->
            session = (ViewModelProvider(activity)[PlayViewModel::class.java].ui as? PlayUi.Ready)?.session
        }
        return session
    }

    @Test
    fun bigCanvasAcrossZoomLevels() {
        compose.onNodeWithText("My Photos").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tile-photo-perf-big")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tile-photo-perf-big").performClick()
        compose.waitUntil(5_000) { session()?.viewport?.isLaidOut == true }
        val session = session()!!
        // Place a third of the gems so placed and unplaced cells both show.
        instrumentation.runOnMainSync {
            for (cell in 0 until session.cellCount step 3) {
                session.select(session.pattern.cells[cell])
                session.place(cell)
            }
        }
        compose.waitForIdle()

        lateinit var window: Window
        scenario.onActivity { window = it.window }
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val stages = Collections.synchronizedList(mutableListOf<LongArray>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            stages += STAGES.map { metrics.getMetric(it) }.toLongArray()
        }
        instrumentation.runOnMainSync { window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(android.os.Looper.getMainLooper())) }

        val location = IntArray(2)
        instrumentation.runOnMainSync { window.decorView.getLocationOnScreen(location) }
        val vp = session.viewport
        val bounds = compose.onNodeWithTag("play-canvas").fetchSemanticsNode().boundsInWindow
        val cx = location[0] + bounds.center.x
        val cy = location[1] + bounds.center.y
        val phases = linkedMapOf<String, List<Long>>()

        fun phase(name: String, gesture: () -> Unit) {
            SystemClock.sleep(300)
            frames.clear()
            stages.clear()
            gesture()
            SystemClock.sleep(300)
            phases[name] = frames.toList()
            val stageMedians = STAGE_NAMES.indices.joinToString(" ") { i ->
                val sorted = stages.map { it[i] / 1_000_000.0 }.sorted()
                "${STAGE_NAMES[i]}=${if (sorted.isEmpty()) "-" else "%.1f".format(sorted[sorted.size / 2])}"
            }
            Log.i(TAG, "$name stage medians (ms): $stageMedians")
            Log.i(TAG, "$name: zoom now ${"%.1f".format(vp.zoomDp)}dp")
        }

        phase("fit pan") { twoFingerPan(cx, cy, 300f, 2_000) }
        phase("pinch fit->~10dp") { pinch(cx, cy, 200f, 200f * 10f / vp.zoomDp, 1_500) }
        phase("~10dp pan") { twoFingerPan(cx, cy, 300f, 2_000) }
        phase("pinch ->~20dp") { pinch(cx, cy, 150f, 300f, 1_200) }
        phase("~20dp pan") { twoFingerPan(cx, cy, 300f, 2_000) }
        phase("pinch ->48dp") { pinch(cx, cy, 120f, 400f, 1_500) }
        phase("48dp pan") { twoFingerPan(cx, cy, 300f, 2_000) }
        phase("pinch ->fit") { pinch(cx, cy, 500f, 60f, 1_500); pinch(cx, cy, 500f, 60f, 1_000) }

        instrumentation.runOnMainSync { window.removeOnFrameMetricsAvailableListener(listener) }
        for ((name, list) in phases) Log.i(TAG, summary(name, list))
        Log.i(TAG, summary("ALL", phases.values.flatten()))
    }

    private fun summary(name: String, nanos: List<Long>): String {
        if (nanos.isEmpty()) return "$name: no frames"
        val ms = nanos.map { it / 1_000_000.0 }.sorted()
        fun p(q: Double) = "%.1f".format(ms[((ms.size - 1) * q).toInt()])
        // The emulator's frame totals include the wait for vsync, so a missed frame is one over 1.5 vsyncs.
        val janky = ms.count { it > 25.0 }
        return "$name: frames=${ms.size} p50=${p(0.5)} p90=${p(0.9)} p95=${p(0.95)} p99=${p(0.99)} max=${p(1.0)} " +
            "missed(>25ms)=$janky (${"%.1f".format(100.0 * janky / ms.size)}%)"
    }

    /** Two fingers 120px apart moving in a circle of [radius] px around ([x], [y]). */
    private fun twoFingerPan(x: Float, y: Float, radius: Float, millis: Long) {
        twoFingers(millis) { t ->
            val ox = radius * sin(t * 2 * Math.PI).toFloat()
            val oy = radius * sin(t * 4 * Math.PI).toFloat() * 0.5f
            floatArrayOf(x - 60 + ox, y + oy, x + 60 + ox, y + oy)
        }
    }

    /** Fingers moving apart (or together) horizontally from [fromSpan] to [toSpan] px. */
    private fun pinch(x: Float, y: Float, fromSpan: Float, toSpan: Float, millis: Long) {
        twoFingers(millis) { t ->
            val half = (fromSpan + (toSpan - fromSpan) * t) / 2f
            floatArrayOf(x - half, y, x + half, y)
        }
    }

    private fun twoFingers(millis: Long, at: (Float) -> FloatArray) {
        val ui = instrumentation.uiAutomation
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, count: Int, p: FloatArray, time: Long): MotionEvent {
            val props = Array(count) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(count) { MotionEvent.PointerCoords().apply { x = p[it * 2]; y = p[it * 2 + 1]; pressure = 1f; size = 1f } }
            return MotionEvent.obtain(down, time, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        }
        val start = at(0f)
        ui.injectInputEvent(event(MotionEvent.ACTION_DOWN, 1, start, down), true)
        ui.injectInputEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, start, down), true)
        var now = down
        while (now - down < millis) {
            SystemClock.sleep(8)
            now = SystemClock.uptimeMillis()
            ui.injectInputEvent(event(MotionEvent.ACTION_MOVE, 2, at(((now - down) / millis.toFloat()).coerceAtMost(1f)), now), true)
        }
        val end = at(1f)
        ui.injectInputEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, end, now), true)
        ui.injectInputEvent(event(MotionEvent.ACTION_UP, 1, end, now), true)
    }

    /** Big portrait (106×142), 36 colors in soft blobs, like a converted photo. */
    private fun bigPattern(): Pattern {
        val w = 106
        val h = 142
        val palette = List(36) { i ->
            val r = (40 + i * 37) % 256
            val g = (90 + i * 53) % 256
            val b = (150 + i * 71) % 256
            PaletteColor(i + 1, (0xFF shl 24) or (r shl 16) or (g shl 8) or b, Lab(i.toDouble(), 0.0, 0.0))
        }
        val cells = IntArray(w * h) { index ->
            val x = index % w
            val y = index / w
            val v = sin(x / 7.0) + sin(y / 9.0) + sin((x + y) / 13.0) + ((x * 31 + y * 17) % 5) * 0.08
            (((v + 3.4) / 6.8) * 36).toInt().coerceIn(0, 35)
        }
        return Pattern(w, h, palette, cells)
    }

    private companion object {
        const val TAG = "CanvasFrameTiming"
        val STAGES = intArrayOf(
            FrameMetrics.INPUT_HANDLING_DURATION,
            FrameMetrics.DRAW_DURATION,
            FrameMetrics.SYNC_DURATION,
            FrameMetrics.COMMAND_ISSUE_DURATION,
            FrameMetrics.SWAP_BUFFERS_DURATION,
            FrameMetrics.GPU_DURATION,
        )
        val STAGE_NAMES = listOf("input", "draw", "sync", "issue", "swap", "gpu")
    }
}
