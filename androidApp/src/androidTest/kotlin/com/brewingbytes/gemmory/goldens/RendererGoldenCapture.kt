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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The renderer golden baseline (iOS Stage 0, T59, R3-1): today's play canvas renderer drawn into software bitmaps for
 * fixed inputs, written as `goldens/renderer/<case>.png` plus `cases.txt`, and one time-lapse end card's two layers
 * (`endcard-band.png`, `endcard-card.png`). `:composeApp`'s device test draws the same
 * cases with the shared renderer and compares (design doc, Success Criteria → Renderer golden bar).
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
        val pattern = (GridCodec.decode(context.assets.open(PICTURE).use { it.readBytes() }) as Decoded.Ok).value.pattern
        val out = File(context.getExternalFilesDir(null), "goldens/renderer").apply {
            deleteRecursively()
            mkdirs()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lines = mutableListOf("# name zoomDp style log (T59 renderer goldens: $PICTURE, ${WIDTH}x$HEIGHT px, density $DENSITY)")
        for (case in GoldenCases.ALL) {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                draw(context, pattern, case, Canvas(bitmap), scope)
            }
            File(out, "${case.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            lines += "${case.name} ${case.zoomDp} ${case.style} ${case.log}"
        }
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
        CanvasRenderer(context, pattern).draw(canvas, session, DENSITY, HighlightStyle.fromKey(case.style), CanvasEffects(), 0L)
    }

    private companion object {
        const val PICTURE = "pictures/deco-peacock.gmrg"
        const val WIDTH = GoldenCases.WIDTH
        const val HEIGHT = GoldenCases.HEIGHT
        const val DENSITY = GoldenCases.DENSITY
        const val END_WIDTH = 1080
        const val END_HEIGHT = 1920
    }
}

/** The fixed inputs (Stage 0 subset): each zoom band × each highlight style on a checkerboard save, plus empty and full. */
object GoldenCases {
    const val WIDTH = 720
    const val HEIGHT = 1280
    const val DENSITY = 2f
    const val CENTER_X = 0.45f
    const val CENTER_Y = 0.5f
    const val SELECTED_SLOT = 0
    const val FELT = 0xFF4A544D.toInt()

    data class Case(val name: String, val zoomDp: Float, val style: String, val log: String)

    /** 0 = fit; then one zoom in each band of the zoom table (raised from 8, numbers from 16, facets from 20). */
    private val ZOOMS = listOf("fit" to 0f, "z10" to 10f, "z18" to 18f, "z28" to 28f)

    val ALL: List<Case> = ZOOMS.flatMap { (zoomName, zoom) ->
        listOf("tint", "outline", "dim").map { Case("$zoomName-$it-checker", zoom, it, "checker") }
    } + listOf(Case("z18-tint-empty", 18f, "tint", "empty"), Case("z18-tint-full", 18f, "tint", "full"))

    /** empty, full, or checker: the cells whose column + row is even, in index order. */
    fun log(pattern: Pattern, kind: String): IntArray = when (kind) {
        "empty" -> IntArray(0)
        "full" -> IntArray(pattern.cells.size) { it }
        else -> pattern.cells.indices.filter { (it % pattern.width + it / pattern.width) % 2 == 0 }.toIntArray()
    }
}
