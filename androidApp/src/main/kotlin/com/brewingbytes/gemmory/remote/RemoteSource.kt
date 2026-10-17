package com.brewingbytes.gemmory.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.UnknownHostException

/** What one GET of the remote catalog folder (#43) gave. */
sealed interface Fetched {
    class Body(val bytes: ByteArray, val etag: String?) : Fetched

    /** 304: the cached copy is still current. */
    data object NotModified : Fetched

    data class HttpError(val code: Int) : Fetched

    /** Bigger than the caller allows: refused unread. */
    data object TooLarge : Fetched

    /**
     * The request didn't complete. [offline]: no network, no route or a timeout, which is normal on a phone and
     * isn't reported; anything else (a cut-off body, say) is.
     */
    data class Failed(val error: IOException, val offline: Boolean) : Fetched
}

/** The remote catalog folder: `catalog.json`, `catalog.json.sig`, `grids/<id>.gmrg`. Blocking; call off the main thread. */
fun interface RemoteSource {
    fun fetch(path: String, ifNoneMatch: String?, maxBytes: Int): Fetched
}

/**
 * [RemoteSource] over plain `HttpURLConnection` (no new HTTP dependency, #43). Sends no cookies, identifiers or user
 * data: GitHub sees the IP address and the platform's default user agent (privacy policy, Who receives data).
 */
class HttpRemoteSource(private val baseUrl: String) : RemoteSource {
    override fun fetch(path: String, ifNoneMatch: String?, maxBytes: Int): Fetched {
        val connection = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Fetched.Failed(e, offline = false)
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            if (ifNoneMatch != null) connection.setRequestProperty("If-None-Match", ifNoneMatch)
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Fetched.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val declared = connection.contentLengthLong
                    if (declared > maxBytes) return Fetched.TooLarge
                    val body = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    connection.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (body.size() + n > maxBytes) return Fetched.TooLarge
                            body.write(buffer, 0, n)
                        }
                    }
                    // A body cut short by a dropped connection: refused here, and the hash would refuse it anyway.
                    if (declared >= 0 && body.size().toLong() != declared) {
                        return Fetched.Failed(IOException("short body: ${body.size()} of $declared"), offline = false)
                    }
                    Fetched.Body(body.toByteArray(), connection.getHeaderField("ETag"))
                }
                else -> Fetched.HttpError(code)
            }
        } catch (e: IOException) {
            Fetched.Failed(e, offline = isOffline(e))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000

        fun isOffline(e: IOException): Boolean =
            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException || e is InterruptedIOException
    }
}
