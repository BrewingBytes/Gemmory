package com.brewingbytes.gemmory.timelapse

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.app.TestLanguage
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * The share videos for a PR's before/after: the player's share ([ShareFrame.PLAYER], "player-…") and a founder clip's
 * 9:16 ([ShareFrame.CLIP], #144, "clip-…"), for a wide and a tall picture, a progress share and VIP, plus both PNG
 * fallbacks and the feed cut of each picture ([ShareFrame.FEED], T51, "feed-…"), into
 * `getExternalFilesDir(null)/share-captures/` for `adb pull`. A device check.
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
        // `-e appLocale <tag>` (T49): the frames in that language; the app's resources follow within a moment.
        TestLanguage.requested?.let { tag ->
            TestLanguage.set(context, tag)
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (context.applicationContext.strings.locale.toLanguageTag() != tag && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            check(context.applicationContext.strings.locale.toLanguageTag() == tag) { "the app didn't switch to $tag" }
        }
    }

    @After
    fun tearDown() {
        TestLanguage.set(context, null)
    }

    private fun request(picture: BundledPicture, percent: Int = 100, watermark: Boolean = true, frame: ShareFrame): EncodeRequest {
        val bytes = context.assets.open(picture.asset).use { it.readBytes() }
        val pattern = (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        val log = pattern.cells.indices.shuffled(Random(7)).toIntArray()
        return EncodeRequest(CanvasRef(CanvasKind.BUNDLED, picture.id), pattern, log.copyOf(log.size * percent / 100), picture.title(context.applicationContext.strings), null, watermark, frame)
    }

    private fun encode(name: String, request: EncodeRequest) {
        val file = File(out, "$name.mp4")
        val encoder = MediaCodecEncoder(context.applicationContext, request, { t -> if (t == null) File(out, "$name.png") else file })
        val codec = checkNotNull(encoder.probe(EncodeTarget.FULL_HD))
        encoder.encode(EncodeTarget.FULL_HD, codec, { _, _ -> }, {})
        Log.i("Gemmory", "capture $name: ${file.length()} bytes")
    }

    private fun png(name: String, request: EncodeRequest) {
        val bitmap = FrameArt.beforeAfter(context.applicationContext, request)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun captures() {
        val wave = BundledPictures.ALL.first { it.id == "wave" }
        val starter = BundledPictures.STARTER
        // In another language only the player's share is captured; a clip's other languages come from the Clips screen.
        val frames = if (TestLanguage.requested == null) listOf("clip" to ShareFrame.CLIP, "player" to ShareFrame.PLAYER) else listOf("player" to ShareFrame.PLAYER)
        for ((tag, frame) in frames) {
            encode("$tag-wave", request(wave, frame = frame))
            encode("$tag-starter", request(starter, frame = frame))
            encode("$tag-progress", request(wave, percent = 47, frame = frame))
            encode("$tag-vip", request(wave, watermark = false, frame = frame))
            png("$tag-fallback", request(wave, frame = frame))
        }
        // T51: the feed cut beside a finished non-VIP share, in whichever language runs.
        encode("feed-wave", request(wave, frame = ShareFrame.FEED))
        encode("feed-starter", request(starter, frame = ShareFrame.FEED))
    }
}
