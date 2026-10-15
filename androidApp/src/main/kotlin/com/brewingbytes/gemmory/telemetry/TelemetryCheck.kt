package com.brewingbytes.gemmory.telemetry

import com.brewingbytes.gemmory.timelapse.EncodeBackend
import com.brewingbytes.gemmory.timelapse.EncodeFailure
import com.brewingbytes.gemmory.timelapse.EncodeStage
import com.brewingbytes.gemmory.timelapse.EncodeTarget

/**
 * The hidden Settings check (#12; remove with its follow-up issue): seven taps on the Version line open a sheet that
 * arms one encoder failure and offers a crash, so a Play-installed release build can prove both reach Crashlytics.
 * Armed, the next encode's 1080×1920 attempt fails at CONFIGURE through the real fallback chain (one non-fatal), and
 * the chain drops to 720×1280 as it would on a phone without that size.
 */
object TelemetryCheck {
    const val TAPS = 7
    const val WINDOW_MS = 3_000L

    @Volatile
    private var failNextFullHd = false

    fun armEncoderFailure() {
        failNextFullHd = true
    }

    fun crash(): Nothing = throw TelemetryCheckCrash()

    /** [backend] with the armed failure applied once. */
    fun wrap(backend: EncodeBackend): EncodeBackend = object : EncodeBackend by backend {
        override fun encode(target: EncodeTarget, codec: String, onFrame: (Int, Int) -> Unit, checkCancelled: () -> Unit) {
            if (target == EncodeTarget.FULL_HD && failNextFullHd) {
                failNextFullHd = false
                throw EncodeFailure(EncodeStage.CONFIGURE, IllegalStateException("telemetry check"))
            }
            backend.encode(target, codec, onFrame, checkCancelled)
        }
    }
}

class TelemetryCheckCrash : RuntimeException("telemetry check crash")

/** [count] taps, each within [windowMs] of the first, complete a streak; a slower tap starts a new one. */
class TapStreak(private val count: Int, private val windowMs: Long) {
    private var first = 0L
    private var taps = 0

    /** True on the tap that completes a streak. */
    fun tap(nowMillis: Long): Boolean {
        if (taps == 0 || nowMillis - first > windowMs) {
            first = nowMillis
            taps = 0
        }
        taps++
        if (taps < count) return false
        taps = 0
        return true
    }
}
