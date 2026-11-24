package com.brewingbytes.gemmory.timelapse

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.SharedResources
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** EncodeManager with the real encoder (eng E4d, re-check R-X1 and finding 3). */
@RunWith(AndroidJUnit4::class)
class EncodeCancelTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cache = File(context.cacheDir, "cancel-test").apply { deleteRecursively(); mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ref = CanvasRef(CanvasKind.BUNDLED, "starter")
    private var exists = true

    private val manager = EncodeManager(
        cacheDir = cache,
        scope = scope,
        backend = { request, partialOf -> MediaCodecEncoder(context, request, partialOf) },
        canvasExists = { exists },
        reporter = {},
    )

    private val request: EncodeRequest by lazy {
        val bytes = context.assets.open(SharedResources.file(BundledPictures.STARTER.asset)).use { it.readBytes() }
        val pattern = (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        EncodeRequest(ref, pattern, pattern.cells.indices.toList().toIntArray(), "The Starry Night", source = null, watermark = true)
    }

    private companion object {
        /**
         * How long an encode may take to get going or finish. In a freshly started test process the app's own startup
         * work competes for the cores: on the emulator (2026-11-23, #172) the starter's 714 frames ran at about 1.4 a
         * second for the first 150 s, then at about 70, on main's APKs and the branch's alike. A wait ends when its
         * state arrives, so a quick device pays nothing.
         */
        const val ENCODE_WAIT_MS = 240_000L
    }

    @After
    fun cleanUp() {
        scope.cancel()
        cache.deleteRecursively()
    }

    private fun files() = File(cache, EncodeManager.DIR).listFiles()?.map { it.name }?.sorted().orEmpty()

    private fun awaitRunningPast(percent: Int) = runBlocking {
        withTimeout(ENCODE_WAIT_MS) { manager.state(ref).first { it is EncodeState.Running && it.percent >= percent } }
    }

    private fun awaitStopped() = runBlocking { withTimeout(30_000) { while (manager.isRunning(ref)) delay(10) } }

    private fun shareAndWait(): File {
        manager.share(request)
        return runBlocking { withTimeout(ENCODE_WAIT_MS) { assertIs<EncodeState.Video>(manager.state(ref).first { it is EncodeState.Video }).file } }
    }

    @Test
    fun cancelMidEncodeLeavesNoFile() {
        manager.share(request)
        awaitRunningPast(20)
        assertTrue(manager.markerOf(ref).exists())
        manager.cancel(ref)
        awaitStopped()
        assertEquals(emptyList(), files())
        assertFalse(manager.markerOf(ref).exists())
    }

    @Test
    fun deleteMidEncodeLeavesNoFile() {
        manager.share(request)
        awaitRunningPast(20)
        exists = false
        runBlocking { manager.cancelAndClear(ref) }
        assertEquals(emptyList(), files())
        assertFalse(manager.markerOf(ref).exists())
    }

    @Test
    fun startOverThenShareEncodesAFreshFile() {
        val first = shareAndWait()
        val firstModified = first.lastModified()
        manager.acknowledge(ref)
        runBlocking { manager.cancelAndClear(ref) }
        assertFalse(first.exists(), "Start over clears the cached MP4")
        Thread.sleep(1_100)
        val second = shareAndWait()
        assertEquals(first.name, second.name, "same key: a finished canvas always has the same log length")
        assertNotEquals(firstModified, second.lastModified(), "a new encode, not the old file")
    }
}
