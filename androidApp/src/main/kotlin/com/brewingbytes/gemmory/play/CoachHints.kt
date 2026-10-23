package com.brewingbytes.gemmory.play

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.game.CanvasZoom

/** The three first-time hints (design D12), with their exact copy ([text], a string resource). */
enum class CoachHint(val key: String, @StringRes val text: Int) {
    ZOOM_IN("zoomIn", R.string.hint_zoom_in),
    PICK_COLOR("pickColor", R.string.hint_pick_color),
    TAP_ZOOMED("tapZoomed", R.string.hint_tap_zoomed),
}

/**
 * First-time hints (design D12): each shows once ever, one at a time, and fades after its action or after 6s.
 * - Zoom in: on a canvas that opens below 16dp cells; done when cells reach 16dp.
 * - Pick a color: the first time cells reach 16dp; done on the first placement.
 * - Tap zoomed: on the first tap-to-zoom; shows before "Pick a color", which that same tap triggers.
 *
 * A hint counts as shown the moment it appears. A hint whose action happens while it waits in line is dropped
 * without being marked, so it can still show later. The flags hold no photo data and may be backed up
 * (founder call 2026-10-14).
 */
class CoachHints(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    var current by mutableStateOf<CoachHint?>(null)
        private set
    private val waiting = ArrayDeque<CoachHint>()

    fun onOpened(zoomDp: Float) {
        if (zoomDp < CanvasZoom.PLACE_MIN_DP) offer(CoachHint.ZOOM_IN) else onZoom(zoomDp)
    }

    fun onZoom(zoomDp: Float) {
        if (zoomDp < CanvasZoom.PLACE_MIN_DP) return
        done(CoachHint.ZOOM_IN)
        offer(CoachHint.PICK_COLOR)
    }

    fun onTapZoom() {
        offer(CoachHint.TAP_ZOOMED, first = true)
        done(CoachHint.ZOOM_IN)
    }

    fun onPlaced() {
        done(CoachHint.PICK_COLOR)
        done(CoachHint.TAP_ZOOMED)
    }

    /** The 6s timeout of [hint]; nothing if another hint is showing by now. */
    fun expire(hint: CoachHint) = done(hint)

    /** Leaving the canvas: hints still waiting are dropped (not marked shown). */
    fun reset() {
        waiting.clear()
        current = null
    }

    fun wasShown(hint: CoachHint): Boolean = prefs.getBoolean(hint.key, false)

    private fun offer(hint: CoachHint, first: Boolean = false) {
        if (wasShown(hint) || hint == current || hint in waiting) return
        if (current == null) show(hint) else if (first) waiting.addFirst(hint) else waiting.addLast(hint)
    }

    private fun done(hint: CoachHint) {
        waiting.remove(hint)
        if (current == hint) {
            current = null
            waiting.removeFirstOrNull()?.let(::show)
        }
    }

    private fun show(hint: CoachHint) {
        current = hint
        prefs.edit { putBoolean(hint.key, true) }
    }

    private companion object {
        const val FILE = "hints"
    }
}
