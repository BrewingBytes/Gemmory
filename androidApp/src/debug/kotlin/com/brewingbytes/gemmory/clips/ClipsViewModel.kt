package com.brewingbytes.gemmory.clips

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.timelapse.ClipOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
 * The Clips screen (#152): null [pictures] while the list loads; the panel's picked picture, order, formats and
 * languages; and the queue, rendered one entry at a time in order.
 */
data class ClipsUi(
    val pictures: List<ClipPicture>? = null,
    val selected: String? = null,
    val order: ClipOrder = ClipOrder.BY_COLOR,
    val formats: Set<ClipFormat> = ClipFormat.entries.toSet(),
    val languages: Set<String> = setOf("en"),
    val queue: List<ClipJob> = emptyList(),
) {
    val picture: ClipPicture? get() = pictures?.firstOrNull { it.ref.id == selected } ?: pictures?.firstOrNull()

    /** Add to queue needs a picture, a format and a language. */
    val canAdd: Boolean get() = picture != null && formats.isNotEmpty() && languages.isNotEmpty()
}

/** The founder clip tool (T47, queue #152). Leaving the screen cancels the queue; the running render deletes its partial file. */
class ClipsViewModel(application: Application) : AndroidViewModel(application) {
    val renderer = ClipRenderer(
        application,
        File(application.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(application.filesDir, "movies"), "clips"),
    )
    private val _ui = MutableStateFlow(ClipsUi(languages = setOf(ClipLanguages.appTag(application.strings.locale))))
    val ui: StateFlow<ClipsUi> = _ui.asStateFlow()
    private var runner: Job? = null
    private var nextKey = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            renderer.sweep()
            val pictures = clipPictures(application as GemmoryApp)
            _ui.update { it.copy(pictures = pictures) }
        }
    }

    fun select(id: String) = _ui.update { it.copy(selected = id) }

    fun pickOrder(order: ClipOrder) = _ui.update { it.copy(order = order) }

    fun toggleFormat(format: ClipFormat) = _ui.update { it.copy(formats = it.formats.toggle(format)) }

    fun toggleLanguage(tag: String) = _ui.update { it.copy(languages = it.languages.toggle(tag)) }

    /** "All": every language on, or every one off when they all are. */
    fun toggleAll() = _ui.update { ui ->
        val all = ClipLanguages.ALL.map { it.tag }.toSet()
        ui.copy(languages = if (ui.languages == all) emptySet() else all)
    }

    /** Queues the panel's choices (languages in [ClipLanguages.ALL]'s order) and starts the queue if it's idle. */
    fun addToQueue() {
        val ui = _ui.value
        val picture = ui.picture ?: return
        if (!ui.canAdd) return
        val languages = ClipLanguages.ALL.map { it.tag }.filter { it in ui.languages }
        val job = ClipJob(nextKey++, picture, ui.order, ui.formats, languages)
        _ui.update { it.copy(queue = it.queue + job) }
        if (runner?.isActive != true) runner = viewModelScope.launch(Dispatchers.IO) { drain() }
    }

    /** Renders every waiting entry, oldest first, including ones added while it runs. */
    private suspend fun drain() {
        val context = currentCoroutineContext()
        while (true) {
            val job = _ui.value.queue.firstOrNull { it.status == ClipStatus.Waiting } ?: return
            setStatus(job.key, ClipStatus.Rendering(0, job.languages.first()))
            val result = renderer.render(
                job.picture,
                job.order,
                job.formats,
                job.languages,
                onProgress = { percent, language -> setStatus(job.key, ClipStatus.Rendering(percent, language)) },
                checkCancelled = { context.ensureActive() },
            )
            setStatus(job.key, ClipStatus.Done(result.saved.size, result.error))
        }
    }

    /** The chain deletes the partial file of a cancelled attempt; the next visit sweeps anything left. */
    override fun onCleared() {
        runner?.cancel()
    }

    private fun setStatus(key: Int, status: ClipStatus) =
        _ui.update { ui -> ui.copy(queue = ui.queue.map { if (it.key == key) it.copy(status = status) else it }) }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}
