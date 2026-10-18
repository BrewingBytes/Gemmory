package com.brewingbytes.gemmory.achievements

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The Play Games Services v2 SDK (#49). Sign-in v2 is automatic: [PlayGamesSdk.initialize] at app start signs the
 * player in when it can, with nothing for the app to show. Every failure is silent (null / false): the ledger keeps
 * what wasn't confirmed for the next time.
 */
class PlayGamesBackend(context: Context, projectId: String, private val reporter: NonFatalReporter) : GamesBackend {
    override val available: Boolean =
        projectId.isNotBlank() &&
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    init {
        if (available) PlayGamesSdk.initialize(context)
    }

    override fun session(activity: Activity): GamesSession = if (available) Session(activity) else GamesSession.SignedOut

    private inner class Session(private val activity: Activity) : GamesSession {
        override suspend fun player(): String? {
            val auth = PlayGames.getGamesSignInClient(activity).isAuthenticated.awaitOrNull() ?: return null
            if (!auth.isAuthenticated) return null
            return PlayGames.getPlayersClient(activity).currentPlayerId.awaitOrNull()
        }

        override suspend fun signIn(): SignIn {
            val auth = PlayGames.getGamesSignInClient(activity).signIn().awaitOrNull(SIGN_IN_TIMEOUT_MS) ?: return SignIn.Failed
            if (!auth.isAuthenticated) return SignIn.Declined
            return PlayGames.getPlayersClient(activity).currentPlayerId.awaitOrNull()?.let { SignIn.SignedIn(it) } ?: SignIn.Failed
        }

        override suspend fun unlock(id: String): Boolean =
            PlayGames.getAchievementsClient(activity).unlockImmediate(id).awaitOrNull() != null

        override suspend fun setSteps(id: String, steps: Int): Boolean =
            PlayGames.getAchievementsClient(activity).setStepsImmediate(id, steps).awaitOrNull() != null

        override suspend fun achievementsIntent(): Intent? = PlayGames.getAchievementsClient(activity).achievementsIntent.awaitOrNull()
    }

    /**
     * The task's result, or null when it failed, was cancelled or took longer than [timeoutMs]: without Play Games'
     * module in Google Play services a task never completes (lessons, 2026-10-18). A Task<Void> succeeds with null,
     * so it maps to [Unit]. Unexpected failures are reported as non-fatals.
     */
    private suspend fun <T> Task<T>.awaitOrNull(timeoutMs: Long = CALL_TIMEOUT_MS): T? = withTimeoutOrNull(timeoutMs) { await() }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            when {
                task.isSuccessful -> cont.resume(task.result ?: Unit as T)
                else -> {
                    task.exception?.takeIf(::worthReporting)?.let { reporter.report(PlayGamesException(it.message.orEmpty(), it)) }
                    cont.resume(null)
                }
            }
        }
    }
}

/** A silent call (signed in?, a send, the achievements screen). */
private const val CALL_TIMEOUT_MS = 10_000L

/** The sign-in the player asked for may show Play Games' own sheet, so it gets time to answer it. */
private const val SIGN_IN_TIMEOUT_MS = 120_000L

/** Offline, signed out and cancelled are everyday outcomes, not errors worth a report. */
private fun worthReporting(e: Exception): Boolean =
    (e as? ApiException)?.statusCode !in setOf(CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.CANCELED)

/** A Play Games call that failed (#49), titled by this class in Crashlytics. */
class PlayGamesException(message: String, cause: Throwable) : Exception(message, cause)
