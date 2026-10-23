package com.brewingbytes.gemmory.telemetry

import android.content.Context
import android.os.RemoteException
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.brewingbytes.gemmory.policy.ReferrerParse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What Play's install referrer service answered: the raw referrer, or an `InstallReferrerResponse` code. */
sealed interface ReferrerAnswer {
    data class Ok(val raw: String?) : ReferrerAnswer

    data class Failed(val code: Int) : ReferrerAnswer
}

/** Asks for the install referrer once; the real one is [PlayReferrerClient], a test or `-Pgemmory.fakeReferrer` fakes it. */
fun interface ReferrerClient {
    suspend fun read(): ReferrerAnswer
}

/**
 * The install referrer read (T46, eng O-E3): once, at a fresh install's first launch, started at process start (it's
 * read on the phone, so it needs no consent), within [timeoutMillis]. Anything but an answer in time is no referrer;
 * only DEVELOPER_ERROR, a bug of ours, is reported. Nothing is logged to Analytics (eng O-E5): Firebase's `first_open`
 * already carries the install's source.
 */
class InstallReferrerReader(
    private val client: ReferrerClient,
    private val reporter: NonFatalReporter,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) {
    /** The raw referrer, capped at [ReferrerParse.MAX_INPUT] chars; null when there's none. */
    suspend fun read(): String? = when (val answer = withTimeoutOrNull(timeoutMillis) { client.read() }) {
        is ReferrerAnswer.Ok -> answer.raw?.take(ReferrerParse.MAX_INPUT)?.ifEmpty { null }
        is ReferrerAnswer.Failed -> {
            if (answer.code == InstallReferrerResponse.DEVELOPER_ERROR) reporter.report(IllegalStateException("install referrer: developer error"))
            null
        }
        null -> null
    }

    companion object {
        const val TIMEOUT_MILLIS = 2_000L
    }
}

/** Play's install referrer service. Its callbacks come on the main thread; the connection ends after one answer. */
class PlayReferrerClient(context: Context) : ReferrerClient {
    private val app = context.applicationContext

    override suspend fun read(): ReferrerAnswer {
        val client = InstallReferrerClient.newBuilder(app).build()
        try {
            return suspendCancellableCoroutine { cont ->
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(code: Int) {
                        if (!cont.isActive) return
                        val answer = if (code == InstallReferrerResponse.OK) {
                            try {
                                ReferrerAnswer.Ok(client.installReferrer.installReferrer)
                            } catch (e: RemoteException) {
                                ReferrerAnswer.Failed(InstallReferrerResponse.SERVICE_DISCONNECTED)
                            }
                        } else {
                            ReferrerAnswer.Failed(code)
                        }
                        cont.resume(answer)
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        if (cont.isActive) cont.resume(ReferrerAnswer.Failed(InstallReferrerResponse.SERVICE_DISCONNECTED))
                    }
                })
            }
        } finally {
            runCatching { client.endConnection() }
        }
    }
}
