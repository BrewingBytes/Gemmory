package com.brewingbytes.gemmory.timelapse

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * The share videos for a PR's before/after (T53): today's frame ([ShareFrame.CLIP], the frame the player's share had
 * before T53, unchanged for the founder clips) and the new one, for a wide and a tall picture, a progress share and
 * VIP, plus both PNG fallbacks, into `getExternalFilesDir(null)/share-captures/` for `adb pull`. A device check.
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class ShareCaptures {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.getExternalFilesDir(null), "share-captures")

    @Before
    fun setUp() {
        assumeDeviceChecks()
        out.deleteRecursively()
        out.mkdirs()
    }

    private fun request(picture: BundledPicture, percent: Int = 100, watermark: Boolean = true, frame: ShareFrame): EncodeRequest {
        val bytes = context.assets.open(picture.asset).use { it.readBytes() }
        val pattern = (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        val log = pattern.cells.indices.shuffled(Random(7)).toIntArray()
        return EncodeRequest(CanvasRef(CanvasKind.BUNDLED, picture.id), pattern, log.copyOf(log.size * percent / 100), picture.title, null, watermark, frame)
    }

    private fun encode(name: String, request: EncodeRequest) {
        val file = File(out, "$name.mp4")
        val encoder = MediaCodecEncoder(context, request, { t -> if (t == null) File(out, "$name.png") else file })
        val codec = checkNotNull(encoder.probe(EncodeTarget.FULL_HD))
        encoder.encode(EncodeTarget.FULL_HD, codec, { _, _ -> }, {})
        Log.i("Gemmory", "capture $name: ${file.length()} bytes")
    }

    private fun png(name: String, request: EncodeRequest) {
        val bitmap = FrameArt.beforeAfter(context, request)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun captures() {
        val wave = BundledPictures.ALL.first { it.id == "wave" }
        val starter = BundledPictures.STARTER
        for ((tag, frame) in listOf("today" to ShareFrame.CLIP, "new" to ShareFrame.PLAYER)) {
            encode("$tag-wave", request(wave, frame = frame))
            encode("$tag-starter", request(starter, frame = frame))
            encode("$tag-progress", request(wave, percent = 47, frame = frame))
            encode("$tag-vip", request(wave, watermark = false, frame = frame))
            png("$tag-fallback", request(wave, frame = frame))
        }
    }
}
