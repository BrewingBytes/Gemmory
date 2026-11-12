package com.brewingbytes.gemmory.goldens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.assumeDeviceChecks
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.play.CanvasEffects
import com.brewingbytes.gemmory.play.CanvasRenderer
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.play.PlayClock
import com.brewingbytes.gemmory.play.PlaySession
import com.brewingbytes.gemmory.play.SaveLoop
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.FrameArt
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.GemRenderer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The renderer golden baseline (iOS Stage 0, T59, R3-1; the full set since Stage 1a, T62): the old play canvas renderer
 * and `GemRenderer` drawn into software bitmaps for fixed inputs, written as `goldens/renderer/<case>.png` plus
 * `cases.txt` and the input pictures (`pictures/`), and one time-lapse end card's two layers (`endcard-band.png`,
 * `endcard-card.png`). `:composeApp`'s device test draws the same cases with the shared renderers and compares
 * (design doc, Success Criteria → Renderer golden bar).
 *
 * A capture tool, not a check: run it on the pinned emulator image (workflow.md) with `-e deviceChecks true`, then
 * pull `files/goldens/renderer/` from the app's external files into the repo's `goldens/renderer/` (README → iOS).
 * Only test code: nothing here ships.
 */
@DeviceCheck
@RunWith(AndroidJUnit4::class)
class RendererGoldenCapture {
    @Test
    fun capture() {
        assumeDeviceChecks()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.getExternalFilesDir(null), "goldens/renderer").apply {
            deleteRecursively()
            mkdirs()
        }
        val pictures = File(out, "pictures").apply { mkdirs() }
        val patterns = GoldenCases.PICTURES.associateWith { name ->
            val bytes = context.assets.open("pictures/$name.gmrg").use { it.readBytes() }
            File(pictures, "$name.gmrg").writeBytes(bytes)
            (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lines = mutableListOf(
            "# canvas name picture zoomDp style log flag (${WIDTH}x$HEIGHT px, density $DENSITY; log: empty, half or full; flag: -, mystery or effects)",
            "# gems name picture cellPx (every palette color placed, top row, and unplaced, bottom row)",
            "# T62 renderer goldens from the old renderers; pictures in pictures/",
        )
        for (case in GoldenCases.ALL) {
            val pattern = patterns.getValue(case.picture)
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                draw(context, pattern, case, Canvas(bitmap), scope)
            }
            File(out, "${case.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            lines += "canvas ${case.name} ${case.picture} ${case.zoomDp} ${case.style} ${case.log} ${case.flag}"
        }
        for (case in GoldenCases.GEMS) {
            val swatch = GoldenCases.swatch(patterns.getValue(case.picture))
            val bitmap = GemRenderer.renderOld(swatch, case.cellPx) { it < swatch.width }
            File(out, "${case.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            lines += "gems ${case.name} ${case.picture} ${case.cellPx}"
        }
        val pattern = patterns.getValue("deco-peacock")
        // One FrameArt end card (Stage 0): the band and the card layer of a finished, non-VIP 1080×1920 share.
        val request = EncodeRequest(
            ref = CanvasRef(CanvasKind.BUNDLED, "deco-peacock"),
            pattern = pattern,
            log = IntArray(pattern.cells.size) { it },
            title = "Art Deco Peacock",
            source = null,
            watermark = true,
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val art = FrameArt(context, request, END_WIDTH, END_HEIGHT)
            File(out, "endcard-band.png").outputStream().use { art.background.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(out, "endcard-card.png").outputStream().use { art.card!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            art.release()
        }
        lines += "# endcard ${END_WIDTH}x$END_HEIGHT en non-VIP player share, finished: endcard-band.png, endcard-card.png (card layer, Play badge included)"
        File(out, "cases.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        scope.cancel()
    }

    private fun draw(context: android.content.Context, pattern: Pattern, case: GoldenCases.Case, canvas: Canvas, scope: CoroutineScope) {
        val log = GoldenCases.log(pattern, case.log)
        val state = CanvasState.restore(pattern, log)!!
        val session = PlaySession(
            ref = CanvasRef(CanvasKind.BUNDLED, "deco-peacock"),
            title = "",
            a11y = A11yText(context.strings),
            pattern = pattern,
            state = state,
            view = null,
            saver = SaveLoop(scope, log, { 0L }, { WriteResult.Written }, { WriteResult.Written }),
            clock = PlayClock(0L) { 0L },
            onStopped = {},
            onClosed = { _, _ -> },
        )
        session.viewport.onLayout(WIDTH, HEIGHT, DENSITY)
        val zoom = if (case.zoomDp == 0f) session.viewport.minZoomDp else case.zoomDp
        session.viewport.set(zoom, pattern.width * GoldenCases.CENTER_X, pattern.height * GoldenCases.CENTER_Y)
        session.select(GoldenCases.SELECTED_SLOT)
        canvas.drawColor(GoldenCases.FELT)
        val effects = CanvasEffects()
        val now = if (case.flag == "effects") GoldenCases.effects(pattern, effects) else 0L
        CanvasRenderer(context, pattern, mystery = case.flag == "mystery")
            .draw(canvas, session, DENSITY, HighlightStyle.fromKey(case.style), effects, now)
    }

    private companion object {
        const val WIDTH = GoldenCases.WIDTH
        const val HEIGHT = GoldenCases.HEIGHT
        const val DENSITY = GoldenCases.DENSITY
        const val END_WIDTH = 1080
        const val END_HEIGHT = 1920
    }
}

/**
 * The fixed inputs (design doc, Success Criteria; T62): one bundled picture per crop ratio at the largest size, each
 * at every zoom band × highlight style on the empty and half saves, the full save at every zoom band, a mystery look
 * and an effects frame; and every gem color as tiles at each table-cut band. composeApp's `GoldenRender` reads the
 * same inputs from `cases.txt`; [effects] and [swatch] are mirrored there.
 */
object GoldenCases {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val DENSITY = 2f
    const val CENTER_X = 0.45f
    const val CENTER_Y = 0.5f
    const val SELECTED_SLOT = 0
    const val FELT = 0xFF4A544D.toInt()

    /** Big 1:1 (122×122), big 3:4 (106×142) and big 4:3 (142×106), 36 colors each (tools/bundled/PROVENANCE.md). */
    val PICTURES = listOf("puppy", "deco-peacock", "japanese-garden")

    data class Case(val name: String, val picture: String, val zoomDp: Float, val style: String, val log: String, val flag: String = "-")

    data class Gems(val name: String, val picture: String, val cellPx: Int)

    /** 0 = fit; then one zoom in each band of the zoom table (raised from 8, numbers from 16, facets from 20). */
    private val ZOOMS = listOf("fit" to 0f, "z10" to 10f, "z18" to 18f, "z28" to 28f)
    private val STYLES = listOf("tint", "outline", "dim")

    val ALL: List<Case> = PICTURES.flatMap { picture ->
        ZOOMS.flatMap { (zoomName, zoom) ->
            listOf("empty", "half").flatMap { log -> STYLES.map { Case("$picture-$zoomName-$it-$log", picture, zoom, it, log) } } +
                Case("$picture-$zoomName-full", picture, zoom, "tint", "full")
        }
    } + listOf(
        Case("deco-peacock-fit-mystery", "deco-peacock", 0f, "tint", "half", "mystery"),
        Case("deco-peacock-z18-mystery", "deco-peacock", 18f, "tint", "half", "mystery"),
        Case("deco-peacock-z28-effects", "deco-peacock", 28f, "tint", "half", "effects"),
    )

    /** Flat below 8px, the 24% bevel, the 20% bevel from 16px, and the catchlight from 20px. */
    val GEMS: List<Gems> = PICTURES.flatMap { picture -> listOf(6, 12, 18, 24).map { Gems("gems-$picture-$it", picture, it) } }

    /** empty, full, or half: the cells whose column + row is even, in index order. */
    fun log(pattern: Pattern, kind: String): IntArray = when (kind) {
        "empty" -> IntArray(0)
        "full" -> IntArray(pattern.cells.size) { it }
        else -> pattern.cells.indices.filter { (it % pattern.width + it / pattern.width) % 2 == 0 }.toIntArray()
    }

    private const val GLINT_AT = CanvasEffects.GLINT_NANOS / 2
    private const val PULSE_START = GLINT_AT - CanvasEffects.PULSE_NANOS / 2

    /**
     * A glint on the center cell and the Find pulse on the cell two to its right, both half way, and a running reveal
     * that still hides the four cells below the center. Returns the time to draw at.
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
}
