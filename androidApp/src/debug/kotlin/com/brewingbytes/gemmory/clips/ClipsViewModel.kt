package com.brewingbytes.gemmory.clips

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
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

/** One row's choices and where its last render stands. */
data class ClipRow(
    val order: ClipOrder = ClipOrder.BY_COLOR,
    val formats: Set<ClipFormat> = ClipFormat.entries.toSet(),
    val status: ClipStatus = ClipStatus.Idle,
)

sealed interface ClipStatus {
    data object Idle : ClipStatus

    data class Rendering(val percent: Int) : ClipStatus

    /** [saved] files written; [error] is the line under the row when the render stopped early. */
    data class Done(val saved: Int, val error: String?) : ClipStatus
}

/** The Clips screen: null [pictures] while the list loads. One render at a time ([rendering] is its picture's id). */
data class ClipsUi(val pictures: List<ClipPicture>? = null, val rows: Map<String, ClipRow> = emptyMap(), val rendering: String? = null) {
    fun row(id: String): ClipRow = rows[id] ?: ClipRow()
}

/** The founder clip tool (T47). Leaving the screen cancels a running render, which deletes its partial file. */
class ClipsViewModel(application: Application) : AndroidViewModel(application) {
    val renderer = ClipRenderer(
        application,
        File(application.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(application.filesDir, "movies"), "clips"),
    )
    private val _ui = MutableStateFlow(ClipsUi())
    val ui: StateFlow<ClipsUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            renderer.sweep()
            val pictures = clipPictures(application as GemmoryApp)
            _ui.update { it.copy(pictures = pictures) }
        }
    }

    fun pickOrder(id: String, order: ClipOrder) = edit(id) { it.copy(order = order) }

    fun toggleFormat(id: String, format: ClipFormat) =
        edit(id) { it.copy(formats = if (format in it.formats) it.formats - format else it.formats + format) }

    /** Does nothing while another render runs, or with no format picked. */
    fun render(picture: ClipPicture) {
        val id = picture.ref.id
        val row = _ui.value.row(id)
        if (_ui.value.rendering != null || row.formats.isEmpty()) return
        _ui.update { it.copy(rendering = id) }
        setStatus(id, ClipStatus.Rendering(0))
        job = viewModelScope.launch(Dispatchers.IO) {
            val context = currentCoroutineContext()
            try {
                val result = renderer.render(
                    picture,
                    row.order,
                    row.formats,
                    onProgress = { setStatus(id, ClipStatus.Rendering(it)) },
                    checkCancelled = { context.ensureActive() },
                )
                setStatus(id, ClipStatus.Done(result.saved.size, result.error))
            } finally {
                _ui.update { it.copy(rendering = null) }
            }
        }
    }

    /** The chain deletes the partial file of a cancelled attempt; the next visit sweeps anything left. */
    override fun onCleared() {
        job?.cancel()
    }

    private fun setStatus(id: String, status: ClipStatus) = edit(id) { it.copy(status = status) }

    private fun edit(id: String, change: (ClipRow) -> ClipRow) =
        _ui.update { ui -> ui.copy(rows = ui.rows + (id to change(ui.row(id)))) }
}
