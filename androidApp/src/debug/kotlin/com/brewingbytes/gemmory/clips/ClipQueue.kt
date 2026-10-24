package com.brewingbytes.gemmory.clips

import com.brewingbytes.gemmory.timelapse.ClipOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a queued render stands (#152). */
sealed interface ClipStatus {
    data object Waiting : ClipStatus

    /** [percent] over the whole entry; [language] is the tag being drawn. */
    data class Rendering(val percent: Int, val language: String) : ClipStatus

    /** [saved] files written; [error] is the line under the entry when it stopped early. */
    data class Done(val saved: Int, val error: String?) : ClipStatus
}

/** One queued render: a picture with the order, formats and languages it was added with. [key] is its place in line. */
data class ClipJob(
    val key: Int,
    val picture: ClipPicture,
    val order: ClipOrder,
    val formats: Set<ClipFormat>,
    val languages: List<String>,
    val status: ClipStatus = ClipStatus.Waiting,
)

/**
 * The Clips screen's queue (#152): entries render one at a time, oldest first, including ones added while it runs.
 * [add] and the loop that picks the next entry both run on [scope]'s dispatcher (the main thread), so an entry added
 * just as the last one finishes is never left waiting; only [render] runs on [io]. [announcement] is the latest entry
 * that finished, for TalkBack (the percents aren't announced).
 */
class ClipQueue(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val render: (job: ClipJob, onProgress: (percent: Int, language: String) -> Unit, checkCancelled: () -> Unit) -> ClipResult,
) {
    private val _jobs = MutableStateFlow<List<ClipJob>>(emptyList())
    val jobs: StateFlow<List<ClipJob>> = _jobs.asStateFlow()
    private val _announcement = MutableStateFlow<String?>(null)
    val announcement: StateFlow<String?> = _announcement.asStateFlow()
    private var runner: Job? = null
    private var nextKey = 0

    /** Queues a render and starts the queue if it's idle. Call on [scope]'s dispatcher. */
    fun add(picture: ClipPicture, order: ClipOrder, formats: Set<ClipFormat>, languages: List<String>) {
        _jobs.update { it + ClipJob(nextKey++, picture, order, formats, languages) }
        if (runner?.isActive != true) runner = scope.launch { drain() }
    }

    /** Stops the queue; the running render deletes its partial file. */
    fun cancel() {
        runner?.cancel()
    }

    private suspend fun drain() {
        while (true) {
            val job = _jobs.value.firstOrNull { it.status == ClipStatus.Waiting } ?: return
            set(job.key, ClipStatus.Rendering(0, job.languages.first()))
            val result = try {
                withContext(io) {
                    val context = currentCoroutineContext()
                    render(job, { percent, language -> set(job.key, ClipStatus.Rendering(percent, language)) }, { context.ensureActive() })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClipResult(emptyList(), "Couldn't make it: ${e::class.simpleName}")
            }
            set(job.key, ClipStatus.Done(result.saved.size, result.error))
            val saved = if (result.saved.size == 1) "saved 1 file" else "saved ${result.saved.size} files"
            // Numbered by its place in the queue, so two entries that end alike are both announced.
            _announcement.value = listOfNotNull("${job.key + 1}. ${job.picture.title}: $saved", result.error).joinToString(". ")
        }
    }

    private fun set(key: Int, status: ClipStatus) =
        _jobs.update { jobs -> jobs.map { if (it.key == key) it.copy(status = status) else it } }
}
