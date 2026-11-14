package com.brewingbytes.gemmory.render

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.TextMeasurer
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.play.CanvasEffects
import com.brewingbytes.gemmory.play.CanvasViewport
import com.brewingbytes.gemmory.play.HighlightStyle

/**
 * Draws the `goldens/renderer/cases.txt` cases with the shared renderers, on either platform (iOS Stage 1a, T62): the
 * same fixed inputs as androidApp's `GoldenCases`, which draws them with the old renderers. A canvas case is 720×1280 px
 * at density 2, centered at 45% / 50%, color 1 selected, felt behind; a gems case is every palette color of a picture
 * as a placed gem (top row) and an unplaced cell (bottom row) at one cell size.
 */
object GoldenRender {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val DENSITY = 2f
    private const val CENTER_X = 0.45f
    private const val CENTER_Y = 0.5f
    private const val FELT = 0xFF4A544D.toInt()

    /** The effects case's clock: glints and the pulse start at 0 and are drawn half way through. */
    private const val GLINT_AT = CanvasEffects.GLINT_NANOS / 2
    private const val PULSE_START = GLINT_AT - CanvasEffects.PULSE_NANOS / 2

    sealed class Case(val name: String, val picture: String)

    /** [log]: empty, half (cells whose column + row is even) or full. [flag]: -, mystery, or effects. */
    class CanvasCase(name: String, picture: String, val zoomDp: Float, val style: String, val log: String, val flag: String) : Case(name, picture)

    class GemsCase(name: String, picture: String, val cellPx: Int) : Case(name, picture)

    fun cases(text: String): List<Case> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map {
        val f = it.split(" ")
        when (f[0]) {
            "canvas" -> CanvasCase(f[1], f[2], f[3].toFloat(), f[4], f[5], f[6])
            "gems" -> GemsCase(f[1], f[2], f[3].toInt())
            else -> error("unknown golden case: $it")
        }
    }

    fun log(pattern: Pattern, kind: String): IntArray = when (kind) {
        "empty" -> IntArray(0)
        "full" -> IntArray(pattern.cells.size) { it }
        else -> pattern.cells.indices.filter { (it % pattern.width + it / pattern.width) % 2 == 0 }.toIntArray()
    }

    /** The rendered case and the boxes where numbers are drawn (masked out of the geometry bar). */
    fun render(pattern: Pattern, measurer: TextMeasurer, assets: RenderAssets, case: CanvasCase): Pair<ImageBitmap, List<IntArray>> {
        val image = ImageBitmap(WIDTH, HEIGHT)
        return image to draw(Canvas(image), pattern, measurer, assets, case)
    }

    /**
     * Draws [case] onto [canvas], [WIDTH]×[HEIGHT] px: a software bitmap ([render]), or the hardware canvas a Compose
     * `Canvas` draws on (androidApp's `HardwareCanvasGoldenTest`, eng D13). Returns the boxes where numbers are drawn.
     */
    fun draw(canvas: Canvas, pattern: Pattern, measurer: TextMeasurer, assets: RenderAssets, case: CanvasCase): List<IntArray> {
        val state = CanvasState.restore(pattern, log(pattern, case.log))!!
        val viewport = CanvasViewport(pattern.width, pattern.height)
        viewport.onLayout(WIDTH, HEIGHT, DENSITY)
        viewport.set(if (case.zoomDp == 0f) viewport.minZoomDp else case.zoomDp, pattern.width * CENTER_X, pattern.height * CENTER_Y)
        val game = object : CanvasGame {
            override val selectedSlot = 0
            override val placedCount = state.placedCount

            override fun placedAt(index: Int) = state.placedAt(index)

            override fun isPlaced(cell: Int) = state.isPlaced(cell)
        }
        val effects = CanvasEffects()
        val now = if (case.flag == "effects") effects(pattern, effects) else 0L
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = Color(FELT) })
        CanvasRenderer(pattern, measurer, assets, mystery = case.flag == "mystery")
            .draw(canvas, game, viewport, DENSITY, HighlightStyle.fromKey(case.style), effects, now)
        val boxes = numberBoxes(pattern, viewport, state)
        if (case.flag == "effects") boxes += effectBoxes(pattern, viewport)
        return boxes
    }

    /**
     * The effects case, the same as androidApp's `GoldenCases.effects`: a glint on the center cell and the Find pulse on
     * the cell two to its right, both half way, and a running reveal that still hides the four cells below the center.
     * Returns the time to draw at.
     */
    fun effects(pattern: Pattern, effects: CanvasEffects): Long {
        val center = (pattern.height * CENTER_Y).toInt() * pattern.width + (pattern.width * CENTER_X).toInt()
        effects.glint(center, 0L)
        effects.pulse(center + 2, PULSE_START)
        val hidden = IntArray(4) { center + pattern.width + it }
        effects.reveal(pattern.cells.size, hidden, IntArray(4) { 1 }, GLINT_AT, 1_000_000_000L)
        return GLINT_AT
    }

    /** Every palette color of [pattern] once placed (top row) and once unplaced (bottom row). */
    fun swatch(pattern: Pattern): Pattern {
        val n = pattern.palette.size
        return Pattern(n, 2, pattern.palette, IntArray(2 * n) { it % n })
    }

    fun gems(pattern: Pattern, case: GemsCase): ImageBitmap {
        val swatch = swatch(pattern)
        return GemRenderer.render(swatch, case.cellPx) { it < swatch.width }
    }

    /** The middle 70% of every visible unplaced cell once numbers show (from 16dp). */
    private fun numberBoxes(pattern: Pattern, viewport: CanvasViewport, state: CanvasState): MutableList<IntArray> {
        if (viewport.zoomDp < 16f) return mutableListOf()
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

    /** The effects case's text: the hidden cells show the selected look, numbered, and the pulse grows its number. */
    private fun effectBoxes(pattern: Pattern, viewport: CanvasViewport): List<IntArray> {
        val center = (pattern.height * CENTER_Y).toInt() * pattern.width + (pattern.width * CENTER_X).toInt()
        val cell = viewport.cellPx
        return (listOf(center + 2) + List(4) { center + pattern.width + it }).map { index ->
            val left = viewport.offsetX + (index % pattern.width) * cell
            val top = viewport.offsetY + (index / pattern.width) * cell
            val grow = cell * 0.2f
            intArrayOf((left - grow).toInt(), (top - grow).toInt(), (left + cell + grow).toInt() + 1, (top + cell + grow).toInt() + 1)
        }
    }
}
