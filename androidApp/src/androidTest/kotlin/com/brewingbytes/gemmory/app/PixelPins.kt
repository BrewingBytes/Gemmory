package com.brewingbytes.gemmory.app

import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.remote.Fetched
import com.brewingbytes.gemmory.remote.RemoteSource
import java.io.IOException

/**
 * What T48's R1 English pixel diff pins (#135): screenshots of two builds taken on different days or with a different
 * remote catalog would differ for reasons that aren't the build. A fixed wall clock (streak, Today, drops), no remote
 * catalog (offline) and a demo-mode status bar.
 */
object PixelPins {
    /** 2026-10-24 12:00 UTC: midday, so the local day is the same in any time zone a run is likely to use. */
    const val NOW_MILLIS = 1_792_843_200_000L

    private val offline = RemoteSource { _, _, _ -> Fetched.Failed(IOException("pixel pins: offline"), offline = true) }

    fun pin(app: GemmoryApp) {
        app.testNowMillis = { NOW_MILLIS }
        app.resetRemoteForTest(offline)
        A11yTestSupport.shell("settings put global sysui_demo_allowed 1")
        for (cmd in listOf(
            "-e command enter",
            "-e command clock -e hhmm 1000",
            "-e command battery -e level 100 -e plugged false",
            "-e command network -e wifi show -e level 4 -e fully true",
            "-e command network -e mobile hide",
            "-e command notifications -e visible false",
        )) {
            A11yTestSupport.shell("am broadcast -a com.android.systemui.demo $cmd")
        }
    }

    fun unpin(app: GemmoryApp) {
        app.testNowMillis = null
        app.resetRemoteForTest()
        A11yTestSupport.shell("am broadcast -a com.android.systemui.demo -e command exit")
    }
}
