package com.brewingbytes.gemmory.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.app.A11yTestSupport
import com.brewingbytes.gemmory.goldens.ArtGoldens
import com.brewingbytes.gemmory.streak.StreakShareCardRenderer
import com.brewingbytes.gemmory.timelapse.Frame
import com.brewingbytes.gemmory.timelapse.FrameLayers
import com.brewingbytes.gemmory.ui.AppArtAssets
import java.io.File
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The renderer golden bar for the time-lapse frame and the streak card (iOS Stage 1a, T62, eng R10-3): the shared
 * `render.FrameArt` and `render.StreakCard`, through the app's own entry points ([FrameLayers],
 * [StreakShareCardRenderer]) with the app's fonts and words, against the old renderers' layers in
 * `goldens/renderer/` ([ArtGoldens]). Text layers take [LayerBar] (SSIM ≥ 0.98 over text and detail, the geometry bar
 * elsewhere); the canvas and the band the geometry bar. Japanese titles break as the old frame's did
 * ([JapaneseWrap]). The shared renders go to the test app's external files (`goldens-actual/`), figures to logcat
 * (`GemmoryGolden`).
 */
@RunWith(AndroidJUnit4::class)
class SharedArtGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.getExternalFilesDir(null), "goldens-actual").apply { mkdirs() }
    private val failures = mutableListOf<String>()

    @Test
    fun sharedFrameAndCardMatchTheGoldens() {
        assumeTrue("a SHARED_RENDERER=false build draws the old renderers", BuildConfig.SHARED_RENDERER)
        val patterns = ArtGoldens.patterns(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (case in ArtGoldens.FRAMES) {
                val localized = ArtGoldens.localized(context, case.language)
                val request = ArtGoldens.request(localized, patterns.getValue(case.picture), case.picture, case.frame, case.watermark, case.log, case.title)
                val art = FrameLayers.of(localized, request, WIDTH, HEIGHT)
                art.apply(Frame.Hold)
                check("${case.name}-canvas", art.canvas, text = false)
                if (case.band) check("${case.name}-band", art.background, text = false)
                checkLayer("${case.name}-overlay", art.overlay)
                checkLayer("${case.name}-card", art.card)
                art.source?.let { check("${case.name}-source", it, text = false) } ?: assertNoGolden("${case.name}-source")
                art.release()
            }
            for (case in ArtGoldens.BEFORE_AFTER) {
                val localized = ArtGoldens.localized(context, case.language)
                val request = ArtGoldens.request(localized, patterns.getValue(case.picture), case.picture, case.frame, case.watermark, case.log, null)
                check(case.name, FrameLayers.beforeAfter(localized, request), text = true)
            }
            for (case in ArtGoldens.STREAKS) {
                val localized = ArtGoldens.localized(context, case.language)
                val card = StreakShareCardRenderer(localized).render(
                    case.length, ArtGoldens.milestone(localized, case.length), ArtGoldens.WEEK, ArtGoldens.letters(localized), ArtGoldens.thumbnail(patterns),
                )
                check(case.name, card, text = true)
            }
        }
        assertTrue(failures.isEmpty(), "over the golden bar:\n" + failures.joinToString("\n"))
    }

    @Test
    fun japaneseTitlesBreakWhereTheyDid() {
        assumeTrue("phrase breaks need Android 13+, as ja-wrap.txt's capture had", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val rows = JapaneseWrap.parse(resource("renderer/ja-wrap.txt").decodeToString())
        assertTrue(rows.size >= 100, "ja-wrap.txt holds ${rows.size} rows: recapture it (README → iOS)")
        val ja = AppArtAssets(ArtGoldens.localized(context, "ja"))
        val wrong = JapaneseWrap.check(rows, FrameLayers.measurer(context), ja.faces.display.family)
        report("ja-wrap rows=${rows.size} wrong=${wrong.size}")
        assertTrue(wrong.isEmpty(), "Japanese titles break elsewhere:\n" + wrong.joinToString("\n"))
    }

    /**
     * The system's Bold text setting (Android 12+) leaves the public video and card as they are, as it left the old
     * `Paint` (T62 review): Compose adds it to a variable font's `wght` axis when the font goes through its
     * `variationSettings`, which `AppArtAssets` therefore avoids. Sets the setting through the shell and back.
     */
    @Test
    fun boldTextLeavesTheArtAlone() {
        assumeTrue("a SHARED_RENDERER=false build draws the old renderers", BuildConfig.SHARED_RENDERER)
        assumeTrue("Bold text is Android 12+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val patterns = ArtGoldens.patterns(context)
        // The device's own setting ("null" when it was never set), put back whatever happens.
        val before = A11yTestSupport.shell("settings get secure font_weight_adjustment").toIntOrNull() ?: 0
        try {
            boldText(BOLD)
            // Built fresh with Bold text on: typefaces an earlier test built are cached for the process.
            AppArtAssets.clearFontsForTest()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val case = ArtGoldens.FRAMES.first { it.name == "frame-en-player" }
                val localized = ArtGoldens.localized(context, case.language)
                val request = ArtGoldens.request(localized, patterns.getValue(case.picture), case.picture, case.frame, case.watermark, case.log, case.title)
                val art = FrameLayers.of(localized, request, WIDTH, HEIGHT)
                art.apply(Frame.Hold)
                checkLayer("${case.name}-overlay", art.overlay)
                art.release()
                val streak = ArtGoldens.STREAKS.first { it.name == "streak-en-100" }
                val en = ArtGoldens.localized(context, streak.language)
                val card = StreakShareCardRenderer(en).render(
                    streak.length, ArtGoldens.milestone(en, streak.length), ArtGoldens.WEEK, ArtGoldens.letters(en), ArtGoldens.thumbnail(patterns),
                )
                check(streak.name, card, text = true)
            }
        } finally {
            // A restore that can't confirm must not hide the failure above.
            runCatching { boldText(before) }
            AppArtAssets.clearFontsForTest()
        }
        assertTrue(failures.isEmpty(), "Bold text changed the art:\n" + failures.joinToString("\n"))
    }

    /** Sets the Bold text weight adjustment and waits until this process's configuration has it. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun boldText(adjustment: Int) {
        A11yTestSupport.shell("settings put secure font_weight_adjustment $adjustment")
        val until = SystemClock.uptimeMillis() + 10_000
        while (context.applicationContext.resources.configuration.fontWeightAdjustment != adjustment) {
            check(SystemClock.uptimeMillis() < until) { "the configuration never got font weight adjustment $adjustment" }
            Thread.sleep(100)
        }
    }

    private fun checkLayer(name: String, layer: Bitmap?) {
        if (layer == null) assertNoGolden(name) else check(name, layer, text = true)
    }

    /** A layer the shared frame doesn't draw must have no golden either: the old one didn't draw it. */
    private fun assertNoGolden(name: String) {
        if (javaClass.classLoader!!.getResource("renderer/$name.png") != null) failures += "$name: golden exists, nothing drawn"
    }

    private fun check(name: String, actual: Bitmap, text: Boolean) {
        File(out, "$name.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val png = resource("renderer/$name.png")
        val golden = BitmapFactory.decodeByteArray(png, 0, png.size)
        val w = golden.width
        val h = golden.height
        if (actual.width != w || actual.height != h) {
            failures += "$name: size ${actual.width}x${actual.height} vs ${w}x$h"
            return
        }
        val a = IntArray(w * h).also { golden.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(w * h).also { actual.getPixels(it, 0, w, 0, 0, w, h) }
        val (passes, figures) = if (text) {
            LayerBar.compare(a, b, w).let { it.passes to it.figures }
        } else {
            val flatA = LayerBar.flatten(a)
            val flatB = LayerBar.flatten(b)
            GoldenMath.diff(flatA, flatB).let { it.geometryPasses to "differing=${GoldenMath.pct(it.differingShare)} maxDiff=${it.maxDiff}" }
        }
        report("$name $figures ${if (passes) "pass" else "FAIL"}")
        if (!passes) failures += "$name $figures"
    }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing golden $path" }.use { it.readBytes() }

    private fun report(line: String) {
        android.util.Log.i("GemmoryGolden", line)
        println("GemmoryGolden $line")
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920

        /** Android's Bold text setting: every weight + 300. */
        const val BOLD = 300
    }
}
