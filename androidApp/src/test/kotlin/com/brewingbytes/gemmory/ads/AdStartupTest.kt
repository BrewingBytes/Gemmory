package com.brewingbytes.gemmory.ads

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.field.UsNatField
import com.iab.gpp.encoder.section.UsNat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T34: Unity's privacy.consent is written before MobileAds.initialize, once per process. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdStartupTest {
    @Test
    fun theUnityWriteComesBeforeInitialize() = runTest(UnconfinedTestDispatcher()) {
        val calls = mutableListOf<String>()
        val writing = CompletableDeferred<Unit>()
        val startup = AdStartup(
            this,
            beforeInitialize = { writing.await(); calls += "unity privacy.consent" },
            initialize = { done -> calls += "MobileAds.initialize"; done() },
        )
        val first = launch { startup.start() }
        val second = launch { startup.start() }
        // The write hasn't finished: nothing initializes yet.
        assertEquals(emptyList(), calls)
        assertFalse(startup.ready)
        writing.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("unity privacy.consent", "MobileAds.initialize"), calls)
        assertTrue(startup.ready)
        startup.start()
        assertEquals(2, calls.size)
    }

    /** The privacy options form closing rewrites privacy.consent (a withdrawal reaches Unity). */
    @Test
    fun eachUpdateRewritesPrivacyConsent() {
        val written = mutableListOf<Boolean>()
        var gpp: String? = null
        val optedOut = GppModel().apply {
            setFieldValue(UsNat.NAME, UsNatField.SALE_OPT_OUT, 1)
        }.encode()
        val unity = UnityConsent({ gpp }, { written += it }, { })
        unity.update()
        gpp = optedOut
        unity.update()
        assertEquals(listOf(true, false), written)
    }
}
