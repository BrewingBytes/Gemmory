package com.brewingbytes.gemmory.spike

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState

/**
 * Draws one `goldens/renderer/cases.txt` case with the spike renderer, on either platform: the same fixed inputs as
 * androidApp's `GoldenCases` (720×1280 px at density 2, centered at 45% / 50%, color 1 selected, felt behind).
 */
object GoldenRender {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val DENSITY = 2f
    private const val CENTER_X = 0.45f
    private const val CENTER_Y = 0.5f
    private const val FELT = 0xFF4A544D.toInt()

    class Case(val name: String, val zoomDp: Float, val style: String, val log: String)

    fun cases(text: String): List<Case> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map {
        val (name, zoom, style, log) = it.split(" ")
        Case(name, zoom.toFloat(), style, log)
    }

    /** The rendered case and the boxes where numbers are drawn (masked out of the geometry bar). */
    internal fun render(pattern: Pattern, measurer: TextMeasurer, mono: FontFamily, case: Case): Pair<ImageBitmap, List<IntArray>> {
        val cells = when (case.log) {
            "empty" -> IntArray(0)
            "full" -> IntArray(pattern.cells.size) { it }
            else -> pattern.cells.indices.filter { (it % pattern.width + it / pattern.width) % 2 == 0 }.toIntArray()
        }
        val state = CanvasState.restore(pattern, cells)!!
        val viewport = CanvasViewport(pattern.width, pattern.height)
        viewport.onLayout(WIDTH, HEIGHT, DENSITY)
        viewport.set(if (case.zoomDp == 0f) viewport.minZoomDp else case.zoomDp, pattern.width * CENTER_X, pattern.height * CENTER_Y)
        val game = object : SpikeRenderer.Game {
            override val selectedSlot = 0
            override val placedCount = state.placedCount

            override fun placedAt(index: Int) = state.placedAt(index)

            override fun isPlaced(cell: Int) = state.isPlaced(cell)
        }
        val image = ImageBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(image)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = Color(FELT) })
        SpikeRenderer(pattern, measurer, mono).draw(canvas, game, viewport, DENSITY, HighlightStyle.fromKey(case.style))
        return image to numberBoxes(pattern, viewport, state)
    }

    /** The middle 70% of every visible unplaced cell once numbers show (from 16dp). */
    private fun numberBoxes(pattern: Pattern, viewport: CanvasViewport, state: CanvasState): List<IntArray> {
        if (viewport.zoomDp < 16f) return emptyList()
        val cell = viewport.cellPx
        val boxes = mutableListOf<IntArray>()
        for (index in pattern.cells.indices) {
            if (state.isPlaced(index)) continue
            val left = viewport.offsetX + (index % pattern.width) * cell
            val top = viewport.offsetY + (index / pattern.width) * cell
            if (left > WIDTH || top > HEIGHT || left + cell < 0 || top + cell < 0) continue
            val inset = cell * 0.15f
            boxes += intArrayOf((left + inset).toInt(), (top + inset).toInt(), (left + cell - inset).toInt() + 1, (top + cell - inset).toInt() + 1)
        }
        return boxes
    }
}
