package com.brewingbytes.gemmory.timelapse

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.os.StatFs
import android.view.Surface
import com.brewingbytes.gemmory.storage.AtomicFiles
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * [EncodeBackend] on MediaCodec (eng E1): AVC with Surface input. [FrameArt] keeps the canvas in one bitmap and adds
 * each frame's new gems to it; [GlFrameWriter] puts each frame on the input surface with its [FramePlan] time, and
 * the encoder's output goes to MediaMuxer on a callback thread. B-frames are off ([MediaFormat.KEY_MAX_B_FRAMES]),
 * so samples come out in frame order.
 */
class MediaCodecEncoder(
    private val context: Context,
    private val request: EncodeRequest,
    private val partialOf: (EncodeTarget?) -> File,
    // context.cacheDir re-creates the directory if the system cleared it.
    private val free: () -> Long = { StatFs(context.cacheDir.path).availableBytes },
) : EncodeBackend {
    private val plan = FramePlan(request.log.size, photoIntro = request.source?.isFile == true, endCard = request.endCard)

    override fun probe(target: EncodeTarget): String? {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && MIME in it.supportedTypes.map(String::lowercase) }
            // Hardware first: faster, and the software AVC encoder is the fallback on every phone anyway.
            .sortedBy { if (it.isHardwareAccelerated) 0 else 1 }
        return infos.firstOrNull { info ->
            val caps = info.getCapabilitiesForType(MIME)
            val video = caps.videoCapabilities ?: return@firstOrNull false
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats &&
                target.width % video.widthAlignment == 0 &&
                target.height % video.heightAlignment == 0 &&
                video.areSizeAndRateSupported(target.width, target.height, target.fps.toDouble())
        }?.name
    }

    override fun encode(target: EncodeTarget, codec: String, onFrame: (Int, Int) -> Unit, checkCancelled: () -> Unit) {
        val output = partialOf(target)
        // The system clears an app's cache under storage pressure, folder included, even between attempts.
        output.parentFile?.mkdirs()
        output.delete()
        val thread = HandlerThread("timelapse-mux").apply { start() }
        val sink = MuxSink(output)
        var mediaCodec: MediaCodec? = null
        var surface: Surface? = null
        var art: FrameArt? = null
        var writer: GlFrameWriter? = null
        try {
            val format = MediaFormat.createVideoFormat(MIME, target.width, target.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, target.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, target.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                // Not real time: the encoder may take as long as it needs per frame.
                setInteger(MediaFormat.KEY_PRIORITY, 1)
            }
            // Held before configuring, so `finally` releases it even when configure or start fails: the next target
            // may need the same hardware encoder instance.
            val c = try {
                MediaCodec.createByCodecName(codec)
            } catch (e: Exception) {
                throw EncodeFailure(EncodeStage.CONFIGURE, e)
            }
            mediaCodec = c
            // Open before the callbacks go live: some encoders send their output format right after start(), before
            // the first frame, and the mux thread must find the muxer there.
            try {
                sink.open()
            } catch (e: IOException) {
                throw EncodeFailure(EncodeStage.MUX, e)
            }
            try {
                c.setCallback(sink, Handler(thread.looper))
                c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface = c.createInputSurface()
                c.start()
            } catch (e: Exception) {
                throw EncodeFailure(EncodeStage.CONFIGURE, e)
            }
            art = FrameArt(context, request, target.width, target.height)
            // Held before the upload, so `finally` releases its context and textures when an upload fails.
            val gl = try {
                GlFrameWriter(surface, target.width, target.height).also { writer = it }
            } catch (e: Exception) {
                throw EncodeFailure(EncodeStage.CONFIGURE, e)
            }
            try {
                gl.upload(art.background, art.canvas, art.source, art.overlay, art.card)
            } catch (e: Exception) {
                throw EncodeFailure(EncodeStage.CONFIGURE, e)
            }
            for (i in 0 until plan.frameCount) {
                checkCancelled()
                sink.rethrow()
                try {
                    val frame = plan.frame(i)
                    val (changed, photoAlpha) = art.apply(frame)
                    val endCard = (frame as? Frame.EndCard)?.let { art.endCard(it.alpha) }
                    gl.write(art.canvas, changed, art.boardRect, photoAlpha, plan.presentationTimeUs(i), art.overlayRect, endCard)
                } catch (e: Exception) {
                    throw EncodeFailure(EncodeStage.ENCODE, e)
                }
                onFrame(i + 1, plan.frameCount)
            }
            try {
                mediaCodec.signalEndOfInputStream()
            } catch (e: Exception) {
                throw EncodeFailure(EncodeStage.ENCODE, e)
            }
            sink.awaitEnd(checkCancelled)
            sink.finish()
            if (sink.samples != plan.frameCount) {
                throw EncodeFailure(EncodeStage.ENCODE, IllegalStateException("${sink.samples} of ${plan.frameCount} frames came out"))
            }
        } finally {
            writer?.let { w -> runCatching { w.release() } }
            runCatching { mediaCodec?.stop() }
            runCatching { mediaCodec?.release() }
            surface?.release()
            sink.release()
            thread.quitSafely()
            art?.release()
        }
    }

    override fun renderPicture() {
        val out = partialOf(null)
        out.parentFile?.mkdirs()
        val bitmap = FrameArt.beforeAfter(context, request)
        try {
            out.outputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IOException("PNG encode failed")
                stream.fd.sync()
            }
        } finally {
            bitmap.recycle()
        }
    }

    override fun discard(target: EncodeTarget?) {
        partialOf(target).delete()
    }

    /**
     * ENOSPC anywhere in the chain, a write that failed with almost nothing left, or the partial file vanishing
     * (ENOENT): the system deletes cache files, ours included, when storage runs low.
     */
    override fun isDiskFull(error: Throwable): Boolean {
        if (AtomicFiles.isDiskFull(error)) return true
        if (generateSequence(error) { it.cause }.any { "ENOENT" in it.message.orEmpty() }) return true
        return runCatching { free() < LOW_SPACE_BYTES }.getOrDefault(false)
    }

    override fun freeBytes(): Long = free()

    /** Encoder callbacks on the mux thread: the muxer starts on the output format and gets each sample. */
    private class MuxSink(private val output: File) : MediaCodec.Callback() {
        @Volatile
        private var muxer: MediaMuxer? = null
        private var track = -1
        private val error = AtomicReference<Throwable?>()
        private val ended = CountDownLatch(1)

        @Volatile
        var samples = 0
            private set

        fun open() {
            muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            try {
                val m = checkNotNull(muxer) { "muxer not open" }
                track = m.addTrack(format)
                m.start()
            } catch (e: Exception) {
                fail(EncodeFailure(EncodeStage.MUX, e))
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!isConfig && info.size > 0 && error.get() == null) {
                    check(track >= 0) { "sample before the output format" }
                    val buffer = codec.getOutputBuffer(index) ?: error("no output buffer $index")
                    try {
                        muxer!!.writeSampleData(track, buffer, info)
                    } catch (e: Exception) {
                        fail(EncodeFailure(EncodeStage.MUX, e))
                    }
                    samples++
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ended.countDown()
            } catch (e: Exception) {
                fail(EncodeFailure(EncodeStage.ENCODE, e))
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) = fail(EncodeFailure(EncodeStage.ENCODE, e))

        private fun fail(e: Throwable) {
            error.compareAndSet(null, e)
            ended.countDown()
        }

        fun rethrow() {
            error.get()?.let { throw it }
        }

        /** Waits for the end of stream, polling [checkCancelled]; a stalled encoder is an encode error. */
        fun awaitEnd(checkCancelled: () -> Unit) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(END_TIMEOUT_S)
            while (!ended.await(100, TimeUnit.MILLISECONDS)) {
                checkCancelled()
                if (System.nanoTime() > deadline) throw EncodeFailure(EncodeStage.ENCODE, TimeoutException("no end of stream"))
            }
            rethrow()
        }

        /** Writes the MP4's index; a failure here is a mux error (often: the disk filled up). */
        fun finish() {
            try {
                muxer!!.stop()
            } catch (e: Exception) {
                throw EncodeFailure(EncodeStage.MUX, e)
            } finally {
                muxer!!.release()
                muxer = null
            }
            // The MP4 is shared as soon as it's published, so it must be on disk first.
            try {
                FileOutputStream(output, true).use { it.fd.sync() }
            } catch (e: IOException) {
                throw EncodeFailure(EncodeStage.MUX, e)
            }
        }

        fun release() {
            muxer?.let { m -> runCatching { m.release() } }
            muxer = null
        }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val END_TIMEOUT_S = 10L
        private const val LOW_SPACE_BYTES = 8L * 1024 * 1024
    }
}
