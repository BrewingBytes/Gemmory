package com.brewingbytes.gemmory.clips

import android.content.Context
import android.graphics.Bitmap
import android.os.StatFs
import android.util.Log
import com.brewingbytes.gemmory.timelapse.ChainOutcome
import com.brewingbytes.gemmory.timelapse.ClipOrder
import com.brewingbytes.gemmory.timelapse.ClipFramePlan
import com.brewingbytes.gemmory.timelapse.ClipOrders
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.EncodeTarget
import com.brewingbytes.gemmory.timelapse.FallbackChain
import com.brewingbytes.gemmory.timelapse.MediaCodecEncoder
import com.brewingbytes.gemmory.timelapse.ShareFrame
import com.brewingbytes.gemmory.copy.strings
import kotlinx.coroutines.CancellationException
import java.io.File

/** What a clip render makes (T47). [STILLS] is the before/after in each language and the finished canvas once. */
enum class ClipFormat(val label: String) {
    NINE_SIXTEEN("9:16"),
    FOUR_FIVE("4:5"),
    STILLS("Stills"),
}

/** How a render ended: the files it saved, and the line to show if it stopped early. */
class ClipResult(val saved: List<File>, val error: String?)

/**
 * The founder clip tool's renderer (T47): a synthetic log from [ClipOrders] through the player's encoder
 * ([MediaCodecEncoder], [FallbackChain]) in the clips' own frame (#144: [ClipFramePlan], the store lockup, the
 * finished-picture hook and the end card), into [dir]
 * (`getExternalFilesDir(DIRECTORY_MOVIES)/clips/`, where `adb pull` reaches it). It bypasses `EncodeManager`: no cache,
 * no Analytics, and the picture needn't have a canvas.
 */
class ClipRenderer(private val context: Context, val dir: File) {
    /**
     * Blocking. For each of [languages] (tags, [ClipLanguages]), in that language: `<id>-<order>-<lang>.mp4`,
     * `<id>-<order>-4x5-<lang>.mp4` and `<id>-before-after-<lang>.png` for [formats]; then `<id>-finished.png` once (it
     * has no words). Stops at the first failure. [onProgress] gets a whole percent over all of them and the language
     * being drawn; [checkCancelled] throws to stop, and the file being written is deleted.
     */
    fun render(
        picture: ClipPicture,
        order: ClipOrder,
        formats: Set<ClipFormat>,
        languages: List<String>,
        onProgress: (percent: Int, language: String) -> Unit,
        checkCancelled: () -> Unit,
    ): ClipResult {
        val saved = ArrayList<File>()
        val pattern = picture.pattern() ?: return ClipResult(saved, "Couldn't read this picture's grid")
        dir.mkdirs()
        val id = picture.ref.id
        val log = ClipOrders.of(order, pattern, ClipOrders.seedOf(id))
        val videos = listOf(ClipFormat.NINE_SIXTEEN, ClipFormat.FOUR_FIVE).filter { it in formats }
        val stills = ClipFormat.STILLS in formats
        val perLanguage = videos.size * ClipFramePlan.FRAMES + if (stills) STILLS_UNITS else 0
        val units = languages.size * perLanguage + if (stills) STILLS_UNITS else 0
        var done = 0
        var percent = -1
        var language = languages.firstOrNull().orEmpty()
        fun progress(at: Int) {
            val next = if (units == 0) 100 else at * 100 / units
            if (next > percent) {
                percent = next
                onProgress(next, language)
            }
        }
        progress(0)
        for (tag in languages) {
            language = tag
            val localized = ClipLanguages.context(context, tag)
            val title = picture.titleIn(localized.strings)
            val request = EncodeRequest(picture.ref, pattern, log, title, source = null, watermark = true, frame = ShareFrame.CLIP)
            for (format in videos) {
                val file = File(dir, fileName(id, order, format, tag))
                val error = video(localized, request, file, if (format == ClipFormat.NINE_SIXTEEN) EncodeTarget.CHAIN else FOUR_FIVE, checkCancelled) {
                    progress(done + it)
                }
                if (error != null) return ClipResult(saved, error)
                saved += file
                done += ClipFramePlan.FRAMES
            }
            if (stills) {
                checkCancelled()
                val file = File(dir, fileName(id, order, ClipFormat.STILLS, tag))
                val error = still(file) { ClipStills.beforeAfter(localized, request) }
                if (error != null) return ClipResult(saved, error)
                saved += file
                done += STILLS_UNITS
                progress(done)
            }
        }
        if (stills) {
            checkCancelled()
            val file = File(dir, "$id-finished.png")
            val error = still(file) { ClipStills.finished(pattern) }
            if (error != null) return ClipResult(saved, error)
            saved += file
            progress(units)
        }
        return ClipResult(saved, null)
    }

    /** One MP4 through the fallback chain; null when it's saved, else the error line. */
    private fun video(context: Context, request: EncodeRequest, out: File, targets: List<EncodeTarget>, checkCancelled: () -> Unit, onFrame: (Int) -> Unit): String? {
        fun partialOf(target: EncodeTarget?) = File(dir, if (target == null) "${out.name}.png$PARTIAL" else "${out.name}.${target}$PARTIAL")
        val chain = FallbackChain(
            backend = MediaCodecEncoder(context, request, ::partialOf, free = { StatFs(dir.path).availableBytes }),
            report = { Log.w(TAG, "clip ${out.name}: ${it.target} ${it.codec} ${it.stage}", it.error) },
            targets = targets,
        )
        val outcome = try {
            chain.run(onProgress = { frames, _ -> onFrame(frames) }, checkCancelled = checkCancelled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "clip ${out.name} failed", e)
            return "Couldn't make ${out.name}: ${e::class.simpleName}"
        }
        return when (outcome) {
            is ChainOutcome.Video -> if (partialOf(outcome.target).renameTo(out)) null else "Couldn't save ${out.name}"
            ChainOutcome.Picture -> {
                partialOf(null).delete()
                "Couldn't make ${out.name}: no video encoder for this size"
            }
            ChainOutcome.Failed -> "Couldn't make ${out.name}"
            ChainOutcome.DiskFull -> "Not enough space on this phone for ${out.name}"
        }
    }

    /** One PNG, written to a partial file and renamed; null when it's saved, else the error line. */
    private fun still(out: File, write: () -> Bitmap): String? {
        val partial = File(dir, "${out.name}$PARTIAL")
        return try {
            val bitmap = write()
            try {
                partial.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encode failed" }
                }
            } finally {
                bitmap.recycle()
            }
            if (partial.renameTo(out)) null else "Couldn't save ${out.name}"
        } catch (e: Exception) {
            Log.w(TAG, "clip ${out.name} failed", e)
            partial.delete()
            "Couldn't make ${out.name}: ${e::class.simpleName}"
        }
    }

    /** Deletes partial files a cancelled or killed render left. */
    fun sweep() {
        dir.listFiles()?.filter { it.name.endsWith(PARTIAL) }?.forEach { it.delete() }
    }

    companion object {
        /**
         * A file's name (#152): a video or the before/after still of [format] in [language]. The finished still has no
         * language: `<id>-finished.png`.
         */
        fun fileName(id: String, order: ClipOrder, format: ClipFormat, language: String): String = when (format) {
            ClipFormat.NINE_SIXTEEN -> "$id-${order.fileName}-$language.mp4"
            ClipFormat.FOUR_FIVE -> "$id-${order.fileName}-4x5-$language.mp4"
            ClipFormat.STILLS -> "$id-before-after-$language.png"
        }

        /** 4:5 for feed posts: 1080×1350, then 864×1080. */
        val FOUR_FIVE = listOf(EncodeTarget(1080, 1350), EncodeTarget(864, 1080))

        /** The stills' share of the progress bar, in frames. */
        private const val STILLS_UNITS = 30
        private const val PARTIAL = ".partial"
        private const val TAG = "GemmoryClips"
    }
}
