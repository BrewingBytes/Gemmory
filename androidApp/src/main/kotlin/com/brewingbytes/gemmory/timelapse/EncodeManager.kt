package com.brewingbytes.gemmory.timelapse

import android.util.Log
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Where one canvas's share stands, for the progress sheet (design system, Share progress sheet). */
sealed interface EncodeState {
    data object Idle : EncodeState

    /** [done] of [total] frames of the current attempt. */
    data class Running(val done: Int, val total: Int) : EncodeState {
        val percent: Int get() = if (total == 0) 0 else done * 100 / total
    }

    /**
     * The finished MP4, ready for the share sheet. Not a data class: every result is a new value, so a re-share of
     * the same cached file still reaches the screen.
     */
    class Video(val file: File) : EncodeState {
        override fun toString() = "Video(${file.name})"
    }

    /** No video target worked: the before/after PNG (eng E1). Identity-equal like [Video]. */
    class Picture(val file: File) : EncodeState {
        override fun toString() = "Picture(${file.name})"
    }

    data object Failed : EncodeState

    data object DiskFull : EncodeState
}

/** Non-fatal for one failed attempt (eng E1): ids, target, codec, stage and error class only. */
class TimelapseAttemptException(ref: CanvasRef, failure: AttemptFailure) : Exception(
    "time-lapse ${ref.kind}/${ref.id} target=${failure.target ?: "picture"} codec=${failure.codec} stage=${failure.stage}",
    failure.error,
)

/**
 * App-scoped time-lapse encodes (eng D2, E4d, E7, re-check R-X1 and finding 3). An encode starts on Share tap, runs
 * one at a time per canvas, keeps going while the process lives (no foreground service, design D8), and can be
 * cancelled. Finished MP4s are cached in `cacheDir/timelapse/` under [EncodeRequest.key], so a re-share is instant;
 * cacheDir is never backed up and the files only leave the phone through the player's own share.
 *
 * While an encode runs, `cacheDir/encode-<canvasId>.pending` exists. If the process dies mid-encode the marker stays:
 * the next start deletes the partial MP4s, and [takeKilledNotice] tells that canvas's completion screen once.
 */
class EncodeManager(
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val backend: (EncodeRequest, (EncodeTarget?) -> File) -> EncodeBackend,
    private val canvasExists: (CanvasRef) -> Boolean,
    private val reporter: NonFatalReporter = NonFatalReporter.Logcat,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { Log.i("Gemmory", it) },
    private val nowNanos: () -> Long = System::nanoTime,
) : EncodeCanceller {
    val dir = File(cacheDir, DIR)
    private val lock = Any()
    private val jobs = HashMap<CanvasRef, Job>()
    private val states = ConcurrentHashMap<CanvasRef, MutableStateFlow<EncodeState>>()
    private val swept: Job = scope.launch(io) { sweep() }

    fun state(ref: CanvasRef): StateFlow<EncodeState> = flow(ref).asStateFlow()

    /** An encode of [ref] is running or still unwinding from a Cancel. */
    fun isRunning(ref: CanvasRef): Boolean = synchronized(lock) { jobs[ref]?.isCompleted == false }

    /** Share tap. Does nothing while this canvas is already encoding (single-flight). */
    fun share(request: EncodeRequest) {
        val ref = request.ref
        synchronized(lock) {
            val previous = jobs[ref]
            if (previous?.isActive == true) return
            val job = scope.launch(io, start = CoroutineStart.LAZY) {
                // A cancelled encode may still be deleting its partial file.
                previous?.join()
                run(request)
            }
            jobs[ref] = job
            job.invokeOnCompletion { synchronized(lock) { if (jobs[ref] === job) jobs.remove(ref) } }
            job.start()
        }
    }

    /** The sheet's Cancel: stops the encode, which deletes its partial file. Back to completion, no message. */
    fun cancel(ref: CanvasRef) {
        synchronized(lock) {
            jobs[ref]?.cancel()
            flow(ref).value = EncodeState.Idle
        }
    }

    /** The result was shown or handed to the share sheet. */
    fun acknowledge(ref: CanvasRef) {
        val f = flow(ref)
        if (f.value !is EncodeState.Running) f.value = EncodeState.Idle
    }

    /**
     * Start over, Play again, Delete and Delete all (eng E4d, R-X1): stops the encode and waits for it, then deletes
     * the canvas's cached MP4s and PNGs, partial files and pending marker, so a replay gets a fresh video.
     */
    override suspend fun cancelAndClear(ref: CanvasRef) {
        val job = synchronized(lock) { jobs.remove(ref) }
        job?.cancelAndJoin()
        withContext(io) {
            filesOf(ref).forEach { it.delete() }
            markerOf(ref).delete()
        }
        flow(ref).value = EncodeState.Idle
    }

    /**
     * True once, on the completion screen, when the last encode of [ref] was killed with the process: its pending
     * marker is there, no encode is running and no finished MP4 exists. The marker is deleted either way.
     */
    suspend fun takeKilledNotice(ref: CanvasRef): Boolean {
        swept.join()
        if (isRunning(ref)) return false
        return withContext(io) {
            val marker = markerOf(ref)
            if (!marker.exists()) return@withContext false
            marker.delete()
            filesOf(ref).none { it.name.endsWith(".mp4") }
        }
    }

    private suspend fun run(request: EncodeRequest) {
        swept.join()
        val ref = request.ref
        val context = currentCoroutineContext()
        val job = context[Job]!!
        val video = File(dir, "${request.key}.mp4")
        if (video.isFile) {
            post(ref, job, EncodeState.Video(video))
            return
        }
        post(ref, job, EncodeState.Running(0, FramePlan.TOTAL_FRAMES))
        val marker = markerOf(ref)
        val started = nowNanos()
        try {
            dir.mkdirs()
            marker.createNewFile()
            val chain = FallbackChain(
                backend = backend(request) { target -> partialOf(ref, target) },
                report = { reporter.report(TimelapseAttemptException(ref, it)) },
            )
            val outcome = chain.run(
                onProgress = { done, total ->
                    // Only upward whole-percent steps: the sheet has nothing new to draw in between, and a fallback
                    // attempt restarting from 0 holds the bar where it was until it passes it (the drop is silent).
                    val current = flow(ref).value as? EncodeState.Running
                    val next = EncodeState.Running(done, total)
                    if (current == null || next.percent > current.percent) post(ref, job, next)
                },
                checkCancelled = { context.ensureActive() },
            )
            val result = when (outcome) {
                is ChainOutcome.Video -> publish(ref, partialOf(ref, outcome.target), video)?.let(EncodeState::Video)
                ChainOutcome.Picture -> publish(ref, partialOf(ref, null), File(dir, "${request.key}.png"))?.let(EncodeState::Picture)
                ChainOutcome.Failed -> EncodeState.Failed
                ChainOutcome.DiskFull -> EncodeState.DiskFull
            }
            log("time-lapse ${ref.id}: $outcome in ${(nowNanos() - started) / 1_000_000} ms")
            marker.delete()
            // Null: the canvas was deleted meanwhile, so there's nothing to show.
            post(ref, job, result ?: EncodeState.Idle)
        } catch (e: CancellationException) {
            marker.delete()
            throw e
        } catch (e: Exception) {
            reporter.report(e)
            partialsOf(ref).forEach { it.delete() }
            marker.delete()
            post(ref, job, EncodeState.Failed)
        }
    }

    /** Only the canvas's current, uncancelled encode may move its state, so a late frame can't undo a Cancel. */
    private fun post(ref: CanvasRef, job: Job, state: EncodeState) {
        synchronized(lock) { if (jobs[ref] === job && job.isActive) flow(ref).value = state }
    }

    /** Renames the partial into the cache if the canvas still exists (eng E4d); older results of it are dropped. */
    private fun publish(ref: CanvasRef, partial: File, target: File): File? {
        if (!canvasExists(ref)) {
            partial.delete()
            return null
        }
        filesOf(ref).filter { it != partial && it != target && !it.name.endsWith(PARTIAL) }.forEach { it.delete() }
        target.parentFile?.mkdirs()
        if (!partial.renameTo(target)) {
            partial.delete()
            return null
        }
        return target
    }

    /** App start: partial files are from encodes that died with the process. Markers stay for [takeKilledNotice]. */
    private fun sweep() {
        dir.listFiles()?.filter { it.name.endsWith(PARTIAL) }?.forEach { it.delete() }
    }

    private fun flow(ref: CanvasRef) = states.getOrPut(ref) { MutableStateFlow(EncodeState.Idle) }

    private fun filesOf(ref: CanvasRef): List<File> =
        dir.listFiles()?.filter { it.name.startsWith("${ref.id}.") }.orEmpty()

    private fun partialsOf(ref: CanvasRef): List<File> = filesOf(ref).filter { it.name.endsWith(PARTIAL) }

    fun partialOf(ref: CanvasRef, target: EncodeTarget?): File =
        File(dir, if (target == null) "${ref.id}.png$PARTIAL" else "${ref.id}.${target.width}x${target.height}.mp4$PARTIAL")

    fun markerOf(ref: CanvasRef): File = File(cacheDir, "encode-${ref.id}.pending")

    companion object {
        const val DIR = "timelapse"
        private const val PARTIAL = ".partial"
    }
}
