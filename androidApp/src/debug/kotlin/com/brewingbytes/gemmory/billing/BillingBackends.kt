package com.brewingbytes.gemmory.billing

import android.content.Context
import com.brewingbytes.gemmory.BuildConfig

/**
 * Debug builds use [FakeBillingBackend] (device checks on an emulator without a Play Store, every paywall state)
 * unless built with `-Pgemmory.fakeBilling=false`, which the license-tester run on the Play Store AVD needs.
 */
object BillingBackends {
    /** The one fake of this process, so instrumented tests can script it. */
    val fake: FakeBillingBackend by lazy { FakeBillingBackend() }

    fun create(context: Context): BillingBackend = if (BuildConfig.FAKE_BILLING) fake else PlayBillingBackend(context)
}
