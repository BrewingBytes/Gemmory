package com.brewingbytes.gemmory.billing

import android.content.Context
import com.brewingbytes.gemmory.telemetry.NonFatalReporter

/** Release builds always talk to Google Play. The fake backend exists only in debug builds. */
object BillingBackends {
    fun create(context: Context, reporter: NonFatalReporter): BillingBackend = PlayBillingBackend(context, reporter)
}
