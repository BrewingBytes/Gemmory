package com.brewingbytes.gemmory

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Long device checks run with the emulator window visible (TalkBack finishing the starter, the screen-size
 * walkthrough). They skip themselves in connectedDebugAndroidTest; run them with
 * `-Pandroid.testInstrumentationRunnerArguments.deviceChecks=true` (or am instrument -e deviceChecks true).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class DeviceCheck

/** Call first in a @DeviceCheck test's setup. */
fun assumeDeviceChecks() =
    assumeTrue("device check: run with -e deviceChecks true", InstrumentationRegistry.getArguments().getString("deviceChecks") == "true")
