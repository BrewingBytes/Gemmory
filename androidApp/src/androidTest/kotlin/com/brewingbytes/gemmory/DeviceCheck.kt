package com.brewingbytes.gemmory

/**
 * Long device checks run with the emulator window visible (e.g. TalkBack finishing the starter): left out of
 * connectedDebugAndroidTest (see build.gradle.kts), run on request with am instrument -e annotation.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class DeviceCheck
