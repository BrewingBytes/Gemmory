package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** EncodeManager on the real filesystem with a scripted backend (eng D2, E4d, E7, re-check R-X1 and finding 3). */
class EncodeManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ref = CanvasRef(CanvasKind.BUNDLED, "starter")
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")
    private val reports = mutableListOf<Throwable>()
    private val existing = mutableSetOf(ref, photo)
    private val backends = AtomicInteger()

    /** What the next backends do. [gate] holds the encode at frame 100 until it's counted down. */
    private var supported = EncodeTarget.CHAIN.toSet()
    private var failing = emptySet<EncodeTarget>()
    private var pictureFails = false
    private var diskFull = false
    private var failAtFrame = 50
    private var gate: CountDownLatch? = null
    private var atGate: CountDownLatch? = null

    private val pattern = Pattern(
        width = 4,
        height = 3,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(12.0, 1.0, -9.0)),
            PaletteColor(2, 0xFFE0D0C0.toInt(), Lab(85.0, 1.5, 5.0)),
        ),
        cells = IntArray(12) { it % 2 },
    )

    private inner class Scripted(private val partialOf: (EncodeTarget?) -> File) : EncodeBackend {
        override fun probe(target: EncodeTarget): String? = if (target in supported) "c2.fake" else null

        override fun encode(target: EncodeTarget, codec: String, onFrame: (Int, Int) -> Unit, checkCancelled: () -> Unit) {
            val out = partialOf(target)
            out.writeText("partial")
            for (frame in 1..FramePlan.TOTAL_FRAMES) {
                if (frame == 100) {
                    atGate?.countDown()
                    gate?.let { g -> while (!g.await(10, TimeUnit.MILLISECONDS)) checkCancelled() }
                }
                checkCancelled()
                if (target in failing && frame == failAtFrame) {
                    throw EncodeFailure(EncodeStage.ENCODE, if (diskFull) Exception("write failed: ENOSPC") else IllegalStateException())
                }
                onFrame(frame, FramePlan.TOTAL_FRAMES)
            }
            out.writeText("mp4 $target")
        }

        override fun renderPicture() {
            partialOf(null).writeText("png")
            if (pictureFails) throw IllegalStateException("png")
        }

        override fun discard(target: EncodeTarget?) {
            partialOf(target).delete()
        }

        override fun isDiskFull(error: Throwable): Boolean = generateSequence(error) { it.cause }.any { "ENOSPC" in it.message.orEmpty() }

        override fun freeBytes(): Long = Long.MAX_VALUE
    }

    private fun manager() = EncodeManager(
        cacheDir = tmp.root,
        scope = scope,
        backend = { _, partialOf ->
            backends.incrementAndGet()
            Scripted(partialOf)
        },
        canvasExists = { it in existing },
        reporter = { reports += it },
        log = {},
    )

    private fun request(r: CanvasRef = ref, logSize: Int = 12, watermark: Boolean = true) =
        EncodeRequest(r, pattern, IntArray(logSize) { it }, "The Starry Night", source = null, watermark = watermark)

    private fun EncodeManager.await(r: CanvasRef = ref, until: (EncodeState) -> Boolean): EncodeState =
        runBlocking { withTimeout(10_000) { state(r).first(until) } }

    /**
     * Shares and waits for the result and for the job to end: a share while the last job is still finishing is
     * single-flight and ignored, so a test's next share must come after it.
     */
    private fun EncodeManager.shareAndWait(request: EncodeRequest): EncodeState {
        share(request)
        val result = await(request.ref) { it !is EncodeState.Idle && it !is EncodeState.Running }
        awaitStopped(request.ref)
        return result
    }

    private fun EncodeManager.awaitStopped(r: CanvasRef = ref) =
        runBlocking { withTimeout(10_000) { while (isRunning(r)) kotlinx.coroutines.delay(5) } }

    private val dir get() = File(tmp.root, EncodeManager.DIR)
    private fun files() = dir.listFiles()?.map { it.name }?.sorted().orEmpty()

    @After
    fun close() = scope.cancel()

    @Test
    fun shareEncodesIntoTheCacheUnderItsKey() {
        val m = manager()
        val video = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals("starter.12.wm.mp4", video.file.name)
        assertEquals("mp4 1080x1920@30", video.file.readText())
        assertEquals(listOf("starter.12.wm.mp4"), files(), "no partial left")
        assertFalse(m.markerOf(ref).exists(), "marker removed on success")
        assertTrue(reports.isEmpty())
    }

    @Test
    fun theSameKeyIsReusedWithoutEncodingAgain() {
        val m = manager()
        m.shareAndWait(request())
        m.acknowledge(ref)
        assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals(1, backends.get())
    }

    @Test
    fun logLengthAndWatermarkAreInTheKey() {
        val m = manager()
        m.shareAndWait(request())
        m.acknowledge(ref)
        assertEquals("starter.11.wm.mp4", (m.shareAndWait(request(logSize = 11)) as EncodeState.Video).file.name)
        m.acknowledge(ref)
        assertEquals("starter.11.clean.mp4", (m.shareAndWait(request(logSize = 11, watermark = false)) as EncodeState.Video).file.name)
        assertEquals(3, backends.get())
        assertEquals(listOf("starter.11.clean.mp4"), files(), "a new result replaces the canvas's older ones")
    }

    @Test
    fun singleFlightPerCanvas() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        m.share(request())
        m.share(request())
        assertTrue(m.isRunning(ref))
        gate!!.countDown()
        assertIs<EncodeState.Video>(m.await { it is EncodeState.Video })
        assertEquals(1, backends.get())
    }

    @Test
    fun twoCanvasesEncodeIndependently() {
        val m = manager()
        m.share(request())
        m.share(request(photo))
        assertIs<EncodeState.Video>(m.await(ref) { it is EncodeState.Video })
        assertIs<EncodeState.Video>(m.await(photo) { it is EncodeState.Video })
        assertEquals(listOf("photo-1.12.wm.mp4", "starter.12.wm.mp4"), files())
    }

    @Test
    fun progressIsReportedWhileRunning() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        val running = assertIs<EncodeState.Running>(m.await { it is EncodeState.Running && it.done >= 99 })
        assertEquals(22, running.percent)
        assertTrue(m.markerOf(ref).exists(), "pending marker written while encoding")
        gate!!.countDown()
    }

    @Test
    fun cancelDeletesThePartialAndTheMarkerWithNoMessage() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("starter.1080x1920.mp4.partial"), files())
        m.cancel(ref)
        assertEquals(EncodeState.Idle, m.state(ref).value)
        m.awaitStopped()
        assertEquals(emptyList(), files())
        assertFalse(m.markerOf(ref).exists())
        assertEquals(EncodeState.Idle, m.state(ref).value, "a late frame doesn't bring the sheet back")
        assertTrue(reports.isEmpty())
    }

    @Test
    fun shareRightAfterCancelEncodesAgain() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        m.cancel(ref)
        gate = null
        assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals(2, backends.get())
        assertEquals(listOf("starter.12.wm.mp4"), files())
    }

    @Test
    fun restartClearsTheCacheSoTheNextShareIsFresh() {
        // R-X1: finish → Share → Start over → finish → Share encodes a fresh file.
        val m = manager()
        m.shareAndWait(request())
        m.acknowledge(ref)
        runBlocking { m.cancelAndClear(ref) }
        assertEquals(emptyList(), files())
        assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals(2, backends.get())
    }

    @Test
    fun restartDuringAnEncodeCancelsItAndLeavesNothing() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        runBlocking { m.cancelAndClear(ref) }
        assertFalse(m.isRunning(ref))
        assertEquals(emptyList(), files())
        assertFalse(m.markerOf(ref).exists())
        assertEquals(EncodeState.Idle, m.state(ref).value)
    }

    @Test
    fun aCanvasDeletedMidEncodePublishesNothing() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        existing -= ref
        gate!!.countDown()
        m.awaitStopped()
        assertEquals(emptyList(), files(), "E4d: no orphan MP4")
        assertEquals(EncodeState.Idle, m.state(ref).value)
    }

    @Test
    fun silentDropTo720() {
        failing = setOf(EncodeTarget.FULL_HD)
        val m = manager()
        val video = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals("mp4 720x1280@30", video.file.readText())
        assertEquals(listOf("starter.12.wm.mp4"), files())
        assertIs<TimelapseAttemptException>(reports.single())
        assertTrue("target=1080x1920@30" in reports.single().message!!)
    }

    @Test
    fun theShownPercentNeverGoesBackWhenFallingBack() {
        // 1080 fails at 66%; the 720 attempt restarts from frame 0 behind the scenes.
        failing = setOf(EncodeTarget.FULL_HD)
        failAtFrame = 300
        val m = manager()
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val watcher = scope.launch { m.state(ref).collect { if (it is EncodeState.Running) seen += it.percent } }
        assertIs<EncodeState.Video>(m.shareAndWait(request()))
        watcher.cancel()
        assertTrue(seen.isNotEmpty())
        assertEquals(seen.sorted(), seen.toList(), "percent only goes up: $seen")
        assertTrue(seen.last() >= 66)
    }

    @Test
    fun reSharingTheCachedVideoEmitsAgain() {
        // The state may still be Video from the last share (e.g. its screen was recreated before handling it).
        val m = manager()
        val first = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        m.share(request())
        val second = assertIs<EncodeState.Video>(m.await { it is EncodeState.Video && it !== first })
        assertEquals(first.file, second.file)
        assertEquals(1, backends.get())
    }

    @Test
    fun noVideoGivesTheBeforeAfterPicture() {
        supported = emptySet()
        val m = manager()
        val picture = assertIs<EncodeState.Picture>(m.shareAndWait(request()))
        assertEquals("starter.12.wm.png", picture.file.name)
        assertEquals(listOf("starter.12.wm.png"), files())
        assertFalse(m.markerOf(ref).exists())
    }

    @Test
    fun aPictureIsNotReusedSoTheNextShareTriesVideoAgain() {
        supported = emptySet()
        val m = manager()
        m.shareAndWait(request())
        m.acknowledge(ref)
        supported = EncodeTarget.CHAIN.toSet()
        assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals(listOf("starter.12.wm.mp4"), files())
    }

    @Test
    fun everythingFailingIsFailed() {
        supported = emptySet()
        pictureFails = true
        val m = manager()
        assertEquals(EncodeState.Failed, m.shareAndWait(request()))
        assertEquals(emptyList(), files())
        assertFalse(m.markerOf(ref).exists())
    }

    @Test
    fun diskFullStopsWithItsOwnState() {
        failing = setOf(EncodeTarget.FULL_HD)
        diskFull = true
        val m = manager()
        assertEquals(EncodeState.DiskFull, m.shareAndWait(request()))
        assertEquals(emptyList(), files())
        m.acknowledge(ref)
        assertEquals(EncodeState.Idle, m.state(ref).value)
    }

    @Test
    fun startupSweepDeletesPartialsAndKeepsTheMarker() {
        dir.mkdirs()
        File(dir, "starter.1080x1920.mp4.partial").writeText("half")
        File(dir, "photo-1.png.partial").writeText("half")
        File(dir, "photo-1.12.wm.mp4").writeText("done")
        File(tmp.root, "encode-starter.pending").createNewFile()
        val m = manager()
        runBlocking { m.takeKilledNotice(photo) }
        assertEquals(listOf("photo-1.12.wm.mp4"), files())
        assertTrue(m.markerOf(ref).exists())
    }

    @Test
    fun killedEncodeShowsItsNoticeOnce() {
        File(tmp.root, "encode-starter.pending").createNewFile()
        val m = manager()
        assertTrue(runBlocking { m.takeKilledNotice(ref) })
        assertFalse(m.markerOf(ref).exists())
        assertFalse(runBlocking { m.takeKilledNotice(ref) }, "once")
        assertFalse(runBlocking { m.takeKilledNotice(photo) }, "other canvases have no marker")
    }

    @Test
    fun aMarkerNextToAFinishedVideoShowsNoNotice() {
        dir.mkdirs()
        File(dir, "starter.12.wm.mp4").writeText("done")
        File(tmp.root, "encode-starter.pending").createNewFile()
        val m = manager()
        assertFalse(runBlocking { m.takeKilledNotice(ref) })
        assertFalse(m.markerOf(ref).exists(), "the marker is deleted either way")
    }

    @Test
    fun aRunningEncodeIsNotAKilledOne() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        assertFalse(runBlocking { m.takeKilledNotice(ref) })
        assertTrue(m.markerOf(ref).exists())
        gate!!.countDown()
        m.await { it is EncodeState.Video }
        assertFalse(m.markerOf(ref).exists())
    }

    @Test
    fun restartDeletesThePendingMarker() {
        File(tmp.root, "encode-starter.pending").createNewFile()
        val m = manager()
        runBlocking { m.cancelAndClear(ref) }
        assertFalse(m.markerOf(ref).exists())
        assertFalse(runBlocking { m.takeKilledNotice(ref) })
    }
}
