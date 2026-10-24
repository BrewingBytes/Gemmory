package com.brewingbytes.gemmory.clips

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.timelapse.ClipOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    /** The latest entry that finished, for TalkBack. */
    val announcement: String? = null,
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
    private val queue = ClipQueue(viewModelScope, Dispatchers.IO) { job, onProgress, checkCancelled ->
        renderer.render(job.picture, job.order, job.formats, job.languages, onProgress, checkCancelled)
    }
    private val _ui = MutableStateFlow(ClipsUi(languages = setOf(ClipLanguages.appTag(application.strings.locale))))
    val ui: StateFlow<ClipsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            renderer.sweep()
            val pictures = clipPictures(application as GemmoryApp)
            _ui.update { it.copy(pictures = pictures) }
        }
        viewModelScope.launch { queue.jobs.collect { jobs -> _ui.update { it.copy(queue = jobs) } } }
        viewModelScope.launch { queue.announcement.collect { line -> _ui.update { it.copy(announcement = line) } } }
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

    /** Queues the panel's choices, languages in [ClipLanguages.ALL]'s order. */
    fun addToQueue() {
        val ui = _ui.value
        val picture = ui.picture ?: return
        if (!ui.canAdd) return
        queue.add(picture, ui.order, ui.formats, ClipLanguages.ALL.map { it.tag }.filter { it in ui.languages })
    }

    override fun onCleared() {
        queue.cancel()
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}
