package com.brewingbytes.gemmory.telemetry

import platform.Foundation.NSLog

internal actual fun logNonFatal(error: Throwable) {
    NSLog("Gemmory non-fatal: %@", error.toString())
}

internal actual fun logKey(key: String, value: String) {
    NSLog("Gemmory key %@=%@", key, value)
}

internal actual fun logEvent(line: String) {
    NSLog("Gemmory %@", line)
}
