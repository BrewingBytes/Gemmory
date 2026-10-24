package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
    private val reports = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
    private val keys = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val reporter = object : NonFatalReporter {
        override fun report(error: Throwable) {
            reports += error
        }

        override fun setKey(key: String, value: String) {
            keys[key] = value
        }
    }
    private var clock: () -> Long = System::nanoTime
    private val events = java.util.Collections.synchronizedList(mutableListOf<AnalyticsEvent>())
    private val existing = mutableSetOf(ref, photo)
    private val backends = AtomicInteger()

    /** What the next backends do. [gate] holds the encode at frame 100 until it's counted down. */
    private var supported = EncodeTarget.CHAIN.toSet()
    private var failing = emptySet<EncodeTarget>()
    private var pictureFails = false
    private var diskFull = false
    private var failAtFrame = 50
    private var gate: CountDownLatch? = null

    /** The feed cut's backends (T51): which targets fail, and whether [gate] holds only the feed cut. */
    private var feedFailing = emptySet<EncodeTarget>()
    private var gateFeedOnly = false
    private val feedBackends = AtomicInteger()
    private var atGate: CountDownLatch? = null

    /** Runs on the encode thread after each reported frame, once the manager has posted its progress. */
    private var afterFrame: (() -> Unit)? = null

    private val pattern = Pattern(
        width = 4,
        height = 3,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(12.0, 1.0, -9.0)),
            PaletteColor(2, 0xFFE0D0C0.toInt(), Lab(85.0, 1.5, 5.0)),
        ),
        cells = IntArray(12) { it % 2 },
    )

    private inner class Scripted(private val partialOf: (EncodeTarget?) -> File, private val feed: Boolean = false) : EncodeBackend {
        override fun probe(target: EncodeTarget): String? = if (target in supported) "c2.fake" else null

        override fun encode(target: EncodeTarget, codec: String, onFrame: (Int, Int) -> Unit, checkCancelled: () -> Unit) {
            val out = partialOf(target)
            out.writeText("partial")
            val frames = if (feed) FeedFramePlan.FRAMES else FramePlan.TOTAL_FRAMES
            for (frame in 1..frames) {
                if (frame == 100 && (feed || !gateFeedOnly)) {
                    atGate?.countDown()
                    gate?.let { g -> while (!g.await(10, TimeUnit.MILLISECONDS)) checkCancelled() }
                }
                checkCancelled()
                if (target in (if (feed) feedFailing else failing) && frame == failAtFrame) {
                    throw EncodeFailure(EncodeStage.ENCODE, if (diskFull) Exception("write failed: ENOSPC") else IllegalStateException())
                }
                onFrame(frame, frames)
                afterFrame?.invoke()
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

    private fun manager(feedCuts: Boolean = false) = EncodeManager(
        cacheDir = tmp.root,
        scope = scope,
        backend = { request, partialOf ->
            val feed = request.frame == ShareFrame.FEED
            (if (feed) feedBackends else backends).incrementAndGet()
            Scripted(partialOf, feed)
        },
        canvasExists = { it in existing },
        reporter = reporter,
        analytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        },
        log = {},
        nowNanos = { clock() },
        feedCuts = feedCuts,
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
        assertEquals("starter.12.wm.v2.en.mp4", video.file.name)
        assertEquals("mp4 1080x1920@30", video.file.readText())
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files(), "no partial left")
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
        assertEquals("starter.12.clean.v2.en.mp4", (m.shareAndWait(request(watermark = false)) as EncodeState.Video).file.name)
        m.acknowledge(ref)
        assertEquals("starter.progress.11.wm.v2.en.mp4", (m.shareAndWait(request(logSize = 11)) as EncodeState.Video).file.name)
        assertEquals(3, backends.get())
        assertEquals(listOf("starter.progress.11.wm.v2.en.mp4"), files(), "a new result replaces the canvas's older ones")
    }

    @Test
    fun aVideoCachedInThePreT53FrameIsNotReused() {
        dir.mkdirs()
        File(dir, "starter.12.wm.mp4").writeText("old frame")
        val m = manager()
        assertEquals("starter.12.wm.v2.en.mp4", (m.shareAndWait(request()) as EncodeState.Video).file.name)
        assertEquals(1, backends.get(), "encoded again in the new frame")
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files(), "the old one is dropped")
    }

    @Test
    fun theFrameAndEndCardFollowTheWatermarkAndSurviveIt() {
        val clip = EncodeRequest(ref, pattern, IntArray(12) { it }, "x", source = null, watermark = true, frame = ShareFrame.CLIP)
        assertFalse(clip.endCard, "founder clips have no end card")
        assertEquals(ShareFrame.CLIP, clip.withWatermark(false).frame)
        assertTrue(request().endCard)
        assertFalse(request().withWatermark(false).endCard, "VIP gets no end card")
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
        assertEquals(listOf("photo-1.12.wm.v2.en.mp4", "starter.12.wm.v2.en.mp4"), files())
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
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files())
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
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files())
        assertIs<TimelapseAttemptException>(reports.single())
        assertTrue("target=1080x1920@30" in reports.single().message!!)
    }

    @Test
    fun anAttemptReportNamesCodecTargetStageAndErrorButNoIdPathOrTitle() {
        failing = setOf(EncodeTarget.FULL_HD)
        val m = manager()
        assertIs<EncodeState.Video>(m.shareAndWait(request(photo)))
        val report = assertIs<TimelapseAttemptException>(reports.single())
        assertEquals("time-lapse photo target=1080x1920@30 codec=c2.fake stage=ENCODE", report.message)
        assertIs<EncodeFailure>(report.cause)
        val text = generateSequence<Throwable>(report) { it.cause }.joinToString { it.toString() }
        for (secret in listOf(photo.id, "The Starry Night", tmp.root.path)) assertFalse(secret in text, secret)
    }

    @Test
    fun aBundledReportNamesThePicture() {
        failing = setOf(EncodeTarget.FULL_HD)
        manager().shareAndWait(request())
        assertTrue(reports.single().message!!.startsWith("time-lapse bundled/starter "))
    }

    @Test
    fun everyEncodeSetsItsWallTimeAndOutcomeKeys() {
        val ticks = java.util.concurrent.ConcurrentLinkedQueue(listOf(1_000_000_000L, 3_345_000_000L))
        clock = { ticks.poll() ?: 0L }
        val m = manager()
        m.shareAndWait(request())
        assertEquals(mapOf(EncodeManager.KEY_WALL_MS to "2345", EncodeManager.KEY_OUTCOME to "video_1920"), keys.toMap())
        assertEquals(
            listOf(AnalyticsEvent("timelapse_made", mapOf("source" to "bundled", "size" to "starter", "outcome" to "video_1920", "wall_ms" to 2345L))),
            events.toList(),
        )

        m.acknowledge(ref)
        ticks += listOf(0L, 10_000_000L)
        supported = emptySet()
        m.shareAndWait(request(logSize = 11))
        assertEquals(mapOf(EncodeManager.KEY_WALL_MS to "10", EncodeManager.KEY_OUTCOME to "picture"), keys.toMap())
    }

    @Test
    fun aCachedReshareSetsNoKeys() {
        val m = manager()
        m.shareAndWait(request())
        m.acknowledge(ref)
        keys.clear()
        events.clear()
        m.shareAndWait(request())
        assertTrue(keys.isEmpty())
        assertTrue(events.isEmpty(), "a cached re-share encodes nothing")
    }

    @Test
    fun theShownPercentNeverGoesBackWhenFallingBack() {
        // 1080 fails at 66%; the 720 attempt restarts from frame 0 behind the scenes.
        failing = setOf(EncodeTarget.FULL_HD)
        failAtFrame = 300
        val m = manager()
        // Read on the encode thread after every frame: a flow collector on another thread can miss every step (the
        // state flow keeps only the latest value), which failed on fast CI runners.
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        afterFrame = { (m.state(ref).value as? EncodeState.Running)?.let { seen += it.percent } }
        assertIs<EncodeState.Video>(m.shareAndWait(request()))
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
        assertEquals("starter.12.wm.v2.en.png", picture.file.name)
        assertEquals(listOf("starter.12.wm.v2.en.png"), files())
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
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files())
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
        File(dir, "photo-1.12.wm.v2.en.mp4").writeText("done")
        File(tmp.root, "encode-starter.pending").createNewFile()
        val m = manager()
        runBlocking { m.takeKilledNotice(photo) }
        assertEquals(listOf("photo-1.12.wm.v2.en.mp4"), files())
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
        File(dir, "starter.12.wm.v2.en.mp4").writeText("done")
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

    @Test
    fun aProgressVideoIsKeyedApartFromTheFinishedOne() {
        val m = manager()
        val progress = assertIs<EncodeState.Video>(m.shareAndWait(request(logSize = 6)))
        assertEquals("starter.progress.6.wm.v2.en.mp4", progress.file.name)
        m.acknowledge(ref)
        assertEquals("starter.progress.7.wm.v2.en.mp4", (m.shareAndWait(request(logSize = 7)) as EncodeState.Video).file.name, "one more gem, a new video")
        m.acknowledge(ref)
        assertEquals("starter.12.wm.v2.en.mp4", (m.shareAndWait(request()) as EncodeState.Video).file.name, "the finished one is never the progress one")
        assertEquals(3, backends.get())
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files(), "finishing drops the progress video")
    }

    @Test
    fun aPlacementStopsARunningProgressEncode() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        val m = manager()
        m.share(request(logSize = 6))
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        assertTrue(m.progressMarkerOf(ref).exists())
        assertFalse(m.markerOf(ref).exists(), "a progress share has its own marker")
        m.onPlaced(ref)
        assertEquals(EncodeState.Idle, m.state(ref).value)
        m.awaitStopped()
        assertEquals(emptyList(), files())
        assertFalse(m.progressMarkerOf(ref).exists())
    }

    @Test
    fun aPlacementLeavesAWaitingResultAlone() {
        val m = manager()
        val video = m.shareAndWait(request(logSize = 6))
        m.onPlaced(ref)
        assertEquals(video, m.state(ref).value, "only a running encode stops")
    }

    @Test
    fun aKilledProgressEncodeNeverShowsTheFinishedNotice() {
        File(tmp.root, "encode-starter.progress.pending").createNewFile()
        val m = manager()
        assertFalse(runBlocking { m.takeKilledNotice(ref) }, "a progress share has no completion screen to tell")
        assertFalse(m.progressMarkerOf(ref).exists(), "the startup sweep (awaited above) deletes it")
    }

    @Test
    fun aLeftoverProgressVideoDoesNotHideAKilledFinishedEncode() {
        dir.mkdirs()
        File(dir, "starter.progress.6.wm.v2.en.mp4").writeText("progress")
        File(tmp.root, "encode-starter.pending").createNewFile()
        val m = manager()
        assertTrue(runBlocking { m.takeKilledNotice(ref) })
    }

    @Test
    fun aFinishedShareAlsoMakesTheFeedCut() {
        val m = manager(feedCuts = true)
        val video = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals("starter.12.wm.v2.en.mp4", video.file.name)
        assertEquals("starter.12.feed.v1.en.mp4", video.feed?.name)
        assertEquals("mp4 1080x1920@30", video.feed?.readText())
        assertEquals(listOf("starter.12.feed.v1.en.mp4", "starter.12.wm.v2.en.mp4"), files(), "both kept, no partial left")
        assertEquals(1, backends.get())
        assertEquals(1, feedBackends.get())
        assertFalse(m.markerOf(ref).exists())
        assertEquals(1, events.count { it.name == "timelapse_made" }, "one encode for the player, the feed cut included")
    }

    @Test
    fun vipAndProgressSharesHaveNoFeedCut() {
        val m = manager(feedCuts = true)
        assertEquals(null, assertIs<EncodeState.Video>(m.shareAndWait(request(watermark = false))).feed, "VIP keeps one clean video")
        m.acknowledge(ref)
        assertEquals(null, assertIs<EncodeState.Video>(m.shareAndWait(request(logSize = 6))).feed, "nothing finished to open on")
        assertEquals(0, feedBackends.get())
    }

    @Test
    fun bothFilesAreReusedFromTheCache() {
        val m = manager(feedCuts = true)
        m.shareAndWait(request())
        m.acknowledge(ref)
        val again = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals("starter.12.feed.v1.en.mp4", again.feed?.name)
        assertEquals(1, backends.get())
        assertEquals(1, feedBackends.get())
    }

    @Test
    fun aVideoCachedBeforeT51GetsOnlyTheFeedCut() {
        dir.mkdirs()
        File(dir, "starter.12.wm.v2.en.mp4").writeText("cached")
        val m = manager(feedCuts = true)
        val video = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals("cached", video.file.readText(), "the video isn't made again")
        assertEquals("starter.12.feed.v1.en.mp4", video.feed?.name)
        assertEquals(0, backends.get())
        assertEquals(1, feedBackends.get())
        assertEquals(0, events.count { it.name == "timelapse_made" }, "the player's video was a cached re-share")
    }

    @Test
    fun aFeedCutThatFailsSharesTheVideoAlone() {
        feedFailing = EncodeTarget.CHAIN.toSet()
        val m = manager(feedCuts = true)
        val video = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals("starter.12.wm.v2.en.mp4", video.file.name)
        assertEquals(null, video.feed)
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files(), "no picture for the feed cut, no partial left")
        assertEquals(EncodeTarget.CHAIN.size, reports.count { it is TimelapseAttemptException }, "each feed attempt is a non-fatal")
    }

    @Test
    fun theBarCountsTheVideoAndTheFeedCut() {
        val m = manager(feedCuts = true)
        val seen = java.util.Collections.synchronizedList(mutableListOf<EncodeState.Running>())
        afterFrame = { (m.state(ref).value as? EncodeState.Running)?.let { seen += it } }
        m.shareAndWait(request())
        val total = FramePlan(12, photoIntro = false, endCard = true).frameCount + FeedFramePlan.FRAMES
        assertTrue(seen.all { it.total >= total - (FramePlan.END_CARD_FRAMES) }, "both files counted from the start")
        assertEquals(total, seen.last().total)
        assertEquals(100, seen.last().percent)
        assertTrue(seen.zipWithNext().all { (a, b) -> b.percent >= a.percent }, "the bar never goes back")
    }

    @Test
    fun cancelDuringTheFeedCutKeepsTheVideoAndDropsThePartial() {
        gate = CountDownLatch(1)
        atGate = CountDownLatch(1)
        gateFeedOnly = true
        val m = manager(feedCuts = true)
        m.share(request())
        assertTrue(atGate!!.await(5, TimeUnit.SECONDS))
        m.cancel(ref)
        m.awaitStopped()
        assertEquals(EncodeState.Idle, m.state(ref).value)
        assertEquals(listOf("starter.12.wm.v2.en.mp4"), files(), "the finished video stays cached; the feed partial is gone")
        assertFalse(m.markerOf(ref).exists())
    }

    @Test
    fun aFailedFeedCutIsNotTriedAgainOnTheNextShare() {
        feedFailing = EncodeTarget.CHAIN.toSet()
        val m = manager(feedCuts = true)
        m.shareAndWait(request())
        m.acknowledge(ref)
        val again = assertIs<EncodeState.Video>(m.shareAndWait(request()))
        assertEquals(null, again.feed)
        assertEquals(1, feedBackends.get(), "the second share goes straight to the cached video")
        assertEquals(1, backends.get())
        // Start over forgets it: a replay gets a fresh try.
        runBlocking { m.cancelAndClear(ref) }
        m.shareAndWait(request())
        assertEquals(2, feedBackends.get())
    }

    @Test
    fun theEncodeTimeIsTheVideosOwnWithoutTheFeedCut() {
        val ticks = AtomicInteger()
        // Every reading of the clock moves it a second on: started, the video's end, then the feed cut's two readings.
        clock = { ticks.getAndIncrement() * 1_000_000_000L }
        val m = manager(feedCuts = true)
        m.shareAndWait(request())
        val made = events.single { it.name == "timelapse_made" }
        assertEquals(1_000L, made.params["wall_ms"], "started to the video's end only")
    }
}
