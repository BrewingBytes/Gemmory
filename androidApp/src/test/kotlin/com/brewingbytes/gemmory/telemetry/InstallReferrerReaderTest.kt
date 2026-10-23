package com.brewingbytes.gemmory.telemetry

import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.brewingbytes.gemmory.policy.ReferrerParse
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T46 (eng failure modes): the install referrer read. Anything but an answer in time is no referrer; only
 * DEVELOPER_ERROR is reported. A fake client stands in for Play's (no Robolectric here, lessons 2026-10-18).
 */
class InstallReferrerReaderTest {
    private val reports = mutableListOf<Throwable>()
    private val reporter = NonFatalReporter { reports += it }

    private fun reader(answer: suspend () -> ReferrerAnswer) = InstallReferrerReader({ answer() }, reporter)

    @Test
    fun anAnswerInTimeIsTheRawReferrer() = runTest {
        assertEquals("utm_source=share&utm_medium=bundled", reader { ReferrerAnswer.Ok("utm_source=share&utm_medium=bundled") }.read())
        assertNull(reader { ReferrerAnswer.Ok("") }.read())
        assertNull(reader { ReferrerAnswer.Ok(null) }.read())
        assertEquals(ReferrerParse.MAX_INPUT, reader { ReferrerAnswer.Ok("x".repeat(5_000)) }.read()?.length, "capped")
        assertTrue(reports.isEmpty())
    }

    @Test
    fun aSlowServiceIsNoReferrerAfterTwoSeconds() = runTest {
        val started = testScheduler.currentTime
        assertNull(reader { awaitCancellation() }.read())
        assertEquals(InstallReferrerReader.TIMEOUT_MILLIS, testScheduler.currentTime - started)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun unavailableUnsupportedAndDisconnectedAreNoReferrerUnreported() = runTest {
        for (code in listOf(
            InstallReferrerResponse.SERVICE_UNAVAILABLE,
            InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
            InstallReferrerResponse.SERVICE_DISCONNECTED,
            InstallReferrerResponse.PERMISSION_ERROR,
        )) {
            assertNull(reader { ReferrerAnswer.Failed(code) }.read(), "code $code")
        }
        assertTrue(reports.isEmpty())
    }

    @Test
    fun aDeveloperErrorIsNoReferrerAndReported() = runTest {
        assertNull(reader { ReferrerAnswer.Failed(InstallReferrerResponse.DEVELOPER_ERROR) }.read())
        assertEquals(listOf("install referrer: developer error"), reports.map { it.message })
    }
}
