package com.brewingbytes.gemmory.play

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.game.CanvasGestures
import com.brewingbytes.gemmory.game.DoubleTap
import com.brewingbytes.gemmory.game.TapChain
import com.brewingbytes.gemmory.game.TapOutcome

/** What the canvas gestures ask of the screen. */
interface CanvasInputHandler {
    /** A gesture started acting (a drag, pinch, long-press or a tap that does something): running zoom animations stop. */
    fun onTouch()

    /** A single tap at ([at]) in canvas px; says what it did. */
    fun onTap(at: Offset): TapOutcome

    /** The second tap of a double-tap whose first tap didn't place a gem. */
    fun onDoubleTap(at: Offset)

    /** A long-press on [cell] selects its color. */
    fun onLongPress(cell: Int)

    /** One cell crossed by a paint drag of [slot]'s color. Cells of other colors and placed gems are skipped silently. */
    fun onPaint(cell: Int, slot: Int)

    /** A paint drag starts, before its first [onPaint]: a Brush stroke (#38) pays one fill for the whole drag. */
    fun onPaintStart() {}
}

private enum class Mode { PENDING, PAINT, PAN, MULTI }

/**
 * The gesture table of design D23, on one pointer stream so the rules can't race each other:
 * - one finger from 16dp: a drag that starts on an unplaced cell of the selected color paints along its path, any
 *   other one-finger drag pans (under 16dp every one-finger drag pans);
 * - two fingers always pan and zoom, for the rest of the gesture;
 * - a tap acts right away; a double-tap zooms only when neither of its taps places (founder call 2026-10-14);
 * - a long-press selects the cell's color, and a drag after it paints if it now can.
 */
fun Modifier.canvasInput(session: PlaySession, handler: CanvasInputHandler): Modifier = pointerInput(session, handler) {
    val viewport = session.viewport
    val chain = TapChain(viewConfiguration.doubleTapTimeoutMillis, DOUBLE_TAP_SLOP.toPx())
    val slop = viewConfiguration.touchSlop
    val longPressMillis = viewConfiguration.longPressTimeoutMillis

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downCell = viewport.cellAt(down.position.x, down.position.y)
        var paints = session.startsPaint(downCell)
        var mode = Mode.PENDING
        var longPressed = false
        var paintSlot = -1
        var last = down.position

        fun paintAlong(from: Offset, to: Offset) {
            val (x0, y0) = viewport.pointAt(from.x, from.y)
            val (x1, y1) = viewport.pointAt(to.x, to.y)
            for (cell in CanvasGestures.cellsAlong(x0, y0, x1, y1, viewport.columns, viewport.rows)) {
                handler.onPaint(cell, paintSlot)
            }
        }

        while (true) {
            val event: PointerEvent? = if (mode == Mode.PENDING && !longPressed) {
                val elapsed = currentEvent.changes.firstOrNull()?.uptimeMillis?.minus(down.uptimeMillis) ?: 0L
                withTimeoutOrNull((longPressMillis - elapsed).coerceAtLeast(1L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                // Held still past the long-press timeout.
                longPressed = true
                chain.clear()
                handler.onTouch()
                if (downCell != null) handler.onLongPress(downCell)
                paints = session.startsPaint(downCell)
                continue
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                val up = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                if (mode == Mode.PENDING && !longPressed) {
                    // Read at lift time: a running zoom may have moved another cell under the finger.
                    val wouldPlace = session.startsPaint(viewport.cellAt(down.position.x, down.position.y))
                    when (chain.second(down.uptimeMillis, down.position.x, down.position.y, wouldPlace)) {
                        // The second tap of a tap-to-zoom lets that zoom finish.
                        DoubleTap.SWALLOW -> Unit
                        DoubleTap.TOGGLE_ZOOM -> {
                            handler.onTouch()
                            handler.onDoubleTap(down.position)
                        }
                        null -> {
                            handler.onTouch()
                            chain.first(up.uptimeMillis, down.position.x, down.position.y, handler.onTap(down.position))
                        }
                    }
                } else {
                    chain.clear()
                }
                up.consume()
                break
            }
            if (pressed.size >= 2 && mode != Mode.MULTI) {
                mode = Mode.MULTI
                chain.clear()
                handler.onTouch()
            }
            when (mode) {
                Mode.MULTI -> {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    if (centroid != Offset.Unspecified) viewport.transform(centroid.x, centroid.y, pan.x, pan.y, zoom)
                }
                Mode.PENDING -> {
                    val pointer = pressed.first()
                    if ((pointer.position - down.position).getDistance() > slop) {
                        chain.clear()
                        handler.onTouch()
                        if (paints) {
                            mode = Mode.PAINT
                            paintSlot = session.selectedSlot
                            handler.onPaintStart()
                            paintAlong(down.position, pointer.position)
                        } else {
                            mode = Mode.PAN
                            val delta = pointer.position - last
                            viewport.transform(pointer.position.x, pointer.position.y, delta.x, delta.y, 1f)
                        }
                    }
                }
                Mode.PAINT -> paintAlong(last, pressed.first().position)
                Mode.PAN -> {
                    val pointer = pressed.first()
                    val delta = pointer.position - pointer.previousPosition
                    viewport.transform(pointer.position.x, pointer.position.y, delta.x, delta.y, 1f)
                }
            }
            if (mode != Mode.PENDING) last = pressed.first().position
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

private val DOUBLE_TAP_SLOP = 48.dp
