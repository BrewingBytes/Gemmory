package com.brewingbytes.gemmory.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.DeviceCheck
import com.brewingbytes.gemmory.play.STREAK_PREFS
import com.brewingbytes.gemmory.storage.StorageLayout
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The ScreenParity gate of iOS Stage 1c (eng D14, T64): each screen that moves to shared UI must look as it did.
 * Every [StoreScreenshots] state, in English and Japanese at font scale 1.0 and 2.0, is compared with its baseline in
 * `goldens/screens/`, and fails when more than [MAX_SHARE] of its pixels differ by more than [TOLERANCE] in a channel.
 * The photo-canvas states need the store photos pushed by hand, so the gate drives `capture()` only.
 *
 * Runs on the pinned emulator image (`gemmory_pixel`) with
 * `am instrument -w -e class com.brewingbytes.gemmory.app.ScreenParityTest -e deviceChecks true …`. Baselines come
 * from main: add `-e recordScreens true`, then `adb pull /data/local/tmp/screens/. goldens/screens/`. A failing state
 * leaves `fail-<config>-<state>.webp` (the screen now) in the same folder.
 */
@DeviceCheck
@RunWith(Parameterized::class)
class ScreenParityTest(private val config: Config) : StoreScreenshots() {
    data class Config(val language: String, val fontScale: Float) {
        val tag: String get() = "$language-${fontScale.toString().replace('.', '_')}"

        override fun toString() = tag
    }

    override val language: String get() = config.language

    private val record = InstrumentationRegistry.getArguments().getString("recordScreens") == "true"

    /**
     * Before [StoreScreenshots]' own setup (a rule runs before every `@Before`): the font scale first, so the demo
     * status bar it pins comes after the system redraws for the new scale; and no streak, so a picture finished by the
     * previous configuration or run doesn't put a streak pill in this one's gallery.
     */
    @get:Rule
    val fresh = object : ExternalResource() {
        override fun before() {
            A11yTestSupport.shell("settings put system font_scale ${config.fontScale}")
            A11yTestSupport.shell("mkdir -p $OUT")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences(STREAK_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            File(context.filesDir, StorageLayout.STREAK_HISTORY).delete()
            SystemClock.sleep(1_000)
        }

        override fun after() {
            A11yTestSupport.shell("settings put system font_scale 1.0")
        }
    }

    override fun photoCanvases() = Unit

    override fun shot(name: String) {
        settle()
        val now = assertNotNull(A11yTestSupport.automation.takeScreenshot(), "no screenshot")
        val file = "${config.tag}-$name.webp"
        if (record) {
            save(now, file)
            return
        }
        val stream = javaClass.classLoader!!.getResourceAsStream("screens/$file")
            ?: fail("no baseline screens/$file: record one from main with -e recordScreens true")
        val baseline = stream.use { BitmapFactory.decodeStream(it) }
        val share = differing(baseline, now)
        if (share > MAX_SHARE) save(now, "fail-$file")
        assertTrue(share <= MAX_SHARE, "$file: ${"%.3f".format(share * 100)}% of pixels differ by more than $TOLERANCE/255 (limit ${MAX_SHARE * 100}%)")
    }

    private fun save(bitmap: Bitmap, name: String) {
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }.toByteArray()
        val (out, input) = A11yTestSupport.automation.executeShellCommandRw("dd of=$OUT/$name")
        ParcelFileDescriptor.AutoCloseOutputStream(input).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    }

    companion object {
        /** Where baselines are recorded and failures left, for `adb pull`. */
        const val OUT = "/data/local/tmp/screens"

        /** A channel may differ by this much (anti-aliasing, gradients) before a pixel counts as changed. */
        const val TOLERANCE = 8

        /** The share of changed pixels a screen may have. */
        const val MAX_SHARE = 0.005

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun configs(): List<Config> = listOf("en", "ja").flatMap { language -> listOf(1.0f, 2.0f).map { Config(language, it) } }

        /** The share of pixels where any channel differs by more than [TOLERANCE]; a size change is all of them. */
        fun differing(a: Bitmap, b: Bitmap): Double {
            if (a.width != b.width || a.height != b.height) return 1.0
            val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
            val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
            var count = 0
            for (i in pa.indices) {
                val x = pa[i]
                val y = pb[i]
                if (x == y) continue
                for (shift in intArrayOf(0, 8, 16, 24)) {
                    if (kotlin.math.abs((x shr shift and 0xFF) - (y shr shift and 0xFF)) > TOLERANCE) {
                        count++
                        break
                    }
                }
            }
            return count.toDouble() / pa.size
        }
    }
}
