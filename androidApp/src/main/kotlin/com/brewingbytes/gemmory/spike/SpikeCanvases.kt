package com.brewingbytes.gemmory.spike

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Photo canvas ids and titles for the spike's temporary Play entry point. Canvas records and titles come with #13. */
object SpikeCanvases {
    private const val PREFIX = "photo-"

    /** Sortable by creation time, and a valid CanvasRef id. */
    fun newId(): String = PREFIX + System.currentTimeMillis()

    /** "My photo · 13 Oct" (design D26). */
    fun title(id: String): String {
        val millis = id.removePrefix(PREFIX).toLongOrNull() ?: return "My photo"
        return "My photo · " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }
}
