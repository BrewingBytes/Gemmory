package com.brewingbytes.gemmory.billing

import android.content.Context

/** Release builds always talk to Google Play. The fake backend exists only in debug builds. */
object BillingBackends {
    fun create(context: Context): BillingBackend = PlayBillingBackend(context)
}
