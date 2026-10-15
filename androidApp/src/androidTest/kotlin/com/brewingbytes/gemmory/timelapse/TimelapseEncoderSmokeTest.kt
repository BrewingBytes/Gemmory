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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real MediaCodec encoder on a device (eng E1, Section 3 test plan): both chain targets produce an MP4 that
 * decodes, at the target size, with 450 frames over ~15 s. Also the before/after picture. Not in CI (#11 runs it on
 * Firebase Test Lab); run with `./gradlew :androidApp:connectedDebugAndroidTest`.
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
        assertEquals(FramePlan.TOTAL_FRAMES, frames)
        return out
    }

    private fun assertPlayable(file: File, target: EncodeTarget) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, format.getString(MediaFormat.KEY_MIME))
            assertEquals(target.width, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(target.height, format.getInteger(MediaFormat.KEY_HEIGHT))
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            assertTrue(durationUs in 14_000_000L..16_000_000L, "duration $durationUs")
            extractor.selectTrack(0)
            var samples = 0
            var lastUs = -1L
            while (extractor.sampleTime >= 0) {
                assertTrue(extractor.sampleTime > lastUs, "timestamps increase")
                lastUs = extractor.sampleTime
                samples++
                extractor.advance()
            }
            assertEquals(FramePlan.TOTAL_FRAMES, samples)
        } finally {
            extractor.release()
        }
        // It decodes: a frame from the build and the last frame come back at the target size.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            for (us in listOf(500_000L, 7_000_000L, 14_900_000L)) {
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
    fun starterAtFullHd() {
        assertPlayable(encode(starter(), EncodeTarget.FULL_HD), EncodeTarget.FULL_HD)
    }

    @Test
    fun starterAtHd() {
        assertPlayable(encode(starter(), EncodeTarget.HD), EncodeTarget.HD)
    }

    @Test
    fun quickPhotoCanvasWithIntroAtFullHd() {
        assertPlayable(encode(quickPhoto(), EncodeTarget.FULL_HD), EncodeTarget.FULL_HD)
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
