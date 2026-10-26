package com.brewingbytes.gemmory.timelapse

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real MediaCodec encoder on a device (eng E1, Section 3 test plan): the chain produces an MP4 that decodes, at
 * the target size or the fallback's, with 504 frames over ~16.8 s (the end card, T53), or 450 over ~15 s for VIP; the
 * 720 target is also forced on its own. Also the before/after picture. Runs on Firebase Test Lab phones from `.github/workflows/testlab.yml` (#11), and with
 * `./gradlew :androidApp:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class TimelapseEncoderSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "smoke").apply { mkdirs() }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** The shipped starter, its cells placed in a shuffled order like a real log. */
    private fun starter(): EncodeRequest {
        val bytes = context.assets.open(BundledPictures.STARTER.asset).use { it.readBytes() }
        val pattern = (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        val log = pattern.cells.indices.shuffled(Random(7)).toIntArray()
        return EncodeRequest(CanvasRef(CanvasKind.BUNDLED, "starter"), pattern, log, "The Starry Night", source = null, watermark = true)
    }

    /** A ~3k-gem Quick-sized photo canvas (56×56) with a source photo, so the intro crossfade runs. */
    private fun quickPhoto(): EncodeRequest {
        val palette = (0 until 16).map { i -> PaletteColor(i + 1, (0xFF shl 24) or (i * 0x0F0B07), Lab(i * 6.0, 0.0, 0.0)) }
        val pattern = Pattern(56, 56, palette, IntArray(56 * 56) { (it / 56 + it % 56) % 16 })
        val source = File(dir, "source.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(560, 560, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF7FB3A0.toInt())
        source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        val log = IntArray(pattern.cells.size) { it }
        return EncodeRequest(CanvasRef(CanvasKind.PHOTO, "photo-smoke"), pattern, log, "My photo · 14 Oct", source, watermark = true)
    }

    private fun encode(request: EncodeRequest, target: EncodeTarget): File {
        val out = File(dir, "${target.width}.mp4")
        val encoder = MediaCodecEncoder(context, request, { t -> if (t == null) File(dir, "picture.png") else out })
        val codec = assertNotNull(encoder.probe(target), "no encoder for $target")
        val started = System.nanoTime()
        var frames = 0
        encoder.encode(target, codec, { done, _ -> frames = done }, {})
        Log.i("Gemmory", "smoke $target on $codec: ${(System.nanoTime() - started) / 1_000_000} ms, ${out.length()} bytes")
        assertEquals(frameCount(request), frames)
        return out
    }

    /**
     * Encodes through the app's [FallbackChain]: 1080×1920, or 720×1280 when this phone's encoders reject or fail the
     * first. Either is a pass (#11: "target or fallback size"); a fallback is logged so the Test Lab logcat shows it.
     */
    private fun encodeByChain(request: EncodeRequest): Pair<File, EncodeTarget> {
        val encoder = MediaCodecEncoder(context, request, { t -> File(dir, if (t == null) "picture.png" else "${t.width}.mp4") })
        val failures = mutableListOf<AttemptFailure>()
        val started = System.nanoTime()
        var frames = 0
        val outcome = FallbackChain(encoder, { failures += it }).run({ done, _ -> frames = done }, {})
        failures.forEach { Log.w("Gemmory", "smoke fallback: ${it.target} on ${it.codec} failed at ${it.stage}", it.error) }
        val target = assertIs<ChainOutcome.Video>(outcome, "no video target worked: $failures").target
        val out = File(dir, "${target.width}.mp4")
        Log.i("Gemmory", "smoke chain $target on ${encoder.probe(target)}: ${(System.nanoTime() - started) / 1_000_000} ms, ${out.length()} bytes")
        assertEquals(frameCount(request), frames)
        return out to target
    }

    /** 504 with the end card (a non-VIP player share), else 450; the feed cut's 210 (T51). */
    private fun frameCount(request: EncodeRequest) =
        if (request.frame == ShareFrame.FEED) FeedFramePlan.FRAMES else FramePlan(request.log.size, photoIntro = false, endCard = request.endCard).frameCount

    private fun assertPlayable(file: File, target: EncodeTarget, frames: Int = FramePlan.TOTAL_FRAMES + FramePlan.END_CARD_FRAMES) {
        val expectedUs = frames * 1_000_000L / FramePlan.FPS
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, format.getString(MediaFormat.KEY_MIME))
            assertEquals(target.width, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(target.height, format.getInteger(MediaFormat.KEY_HEIGHT))
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            assertTrue(durationUs in expectedUs - 1_000_000L..expectedUs + 1_000_000L, "duration $durationUs")
            extractor.selectTrack(0)
            var samples = 0
            var lastUs = -1L
            while (extractor.sampleTime >= 0) {
                assertTrue(extractor.sampleTime > lastUs, "timestamps increase")
                lastUs = extractor.sampleTime
                samples++
                extractor.advance()
            }
            assertEquals(frames, samples)
        } finally {
            extractor.release()
        }
        // It decodes: a frame from the build and the last frame come back at the target size.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            for (us in listOf(500_000L, minOf(7_000_000L, expectedUs / 2), expectedUs - 100_000L)) {
                val frame = assertNotNull(retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST), "frame at $us")
                assertEquals(target.width, frame.width)
                assertEquals(target.height, frame.height)
                frame.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    @Test
    fun starterByChain() {
        val (file, target) = encodeByChain(starter())
        assertPlayable(file, target)
    }

    @Test
    fun vipStarterHasNoEndCard() {
        val (file, target) = encodeByChain(starter().withWatermark(false))
        assertPlayable(file, target, FramePlan.TOTAL_FRAMES)
    }

    @Test
    fun starterAtHd() {
        assertPlayable(encode(starter(), EncodeTarget.HD), EncodeTarget.HD)
    }

    @Test
    fun quickPhotoCanvasWithIntroByChain() {
        val (file, target) = encodeByChain(quickPhoto())
        assertPlayable(file, target)
    }

    @Test
    fun feedCutByChain() {
        val (file, target) = encodeByChain(starter().feed())
        assertPlayable(file, target, FeedFramePlan.FRAMES)
    }

    @Test
    fun quickPhotoFeedCutWithPhotoHookByChain() {
        val (file, target) = encodeByChain(quickPhoto().feed())
        assertPlayable(file, target, FeedFramePlan.FRAMES)
    }

    @Test
    fun beforeAfterPicture() {
        val out = File(dir, "picture.png")
        MediaCodecEncoder(context, quickPhoto(), { out }).renderPicture()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.path, options)
        assertEquals(1080, options.outWidth)
        assertEquals(1920, options.outHeight)
    }
}
