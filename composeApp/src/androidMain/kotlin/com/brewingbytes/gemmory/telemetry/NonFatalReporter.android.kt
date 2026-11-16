package com.brewingbytes.gemmory.telemetry

import android.util.Log

internal actual fun logNonFatal(error: Throwable) {
    Log.w("Gemmory", "non-fatal", error)
}

internal actual fun logKey(key: String, value: String) {
    Log.i("Gemmory", "key $key=$value")
}

internal actual fun logEvent(line: String) {
    Log.i("Gemmory", line)
}
