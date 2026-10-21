package com.brewingbytes.gemmory.review

import android.app.Activity
import com.brewingbytes.gemmory.policy.ReviewPrompt
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Play's in-app review sheet, behind a port so the gate and the gallery return are tested without Play. */
fun interface ReviewGateway {
    /** Asks Play for its sheet over [activity] and returns once Play's flow has ended. Never throws: Play says nothing. */
    suspend fun request(activity: Activity)
}

/**
 * The Play In-App Review API. Play draws and rate-limits the sheet and never says whether it showed; a failure (no
 * Play Store, offline, quota) is ignored like a sheet that didn't show (eng failure table: never blocks anything).
 */
class PlayReviewGateway : ReviewGateway {
    override suspend fun request(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, info).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Counted as an attempt all the same.
        }
    }
}

/** A Play task's result, without review-ktx (its R8 references a class the app doesn't ship). */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) cont.resumeWithException(error) else cont.resume(task.result)
    }
}

/**
 * The in-app review prompt (T39) as the gallery sees it: whether an attempt is due on this return from a fresh finish
 * ([ReviewPrompt]), and making it. [flagOn] is `features.review` from the verified catalog, [finishes] every finish
 * counted on this phone, [today] the local epoch day.
 */
class Reviews(
    private val prefs: ReviewPrefs,
    private val gateway: ReviewGateway,
    private val analytics: Analytics,
    private val flagOn: () -> Boolean,
    private val finishes: () -> Int,
    private val today: () -> Long,
) {
    /** The attempt (1-3) due now, or null. [firstFinishDay]: the player's first finish, null when none has a day. */
    fun due(firstFinishDay: Long?): Int? =
        ReviewPrompt.attemptDue(prefs.made, finishes(), flagOn(), today(), prefs.lastAttemptDay, firstFinishDay)

    /**
     * Makes [attempt]: it's counted before Play is asked, so an error, a sheet Play didn't show or a process killed
     * meanwhile still uses it up.
     */
    suspend fun request(activity: Activity, attempt: Int) {
        counted(attempt)
        gateway.request(activity)
    }

    /** [attempt] used up today, and `review_requested` logged. */
    fun counted(attempt: Int) {
        prefs.made = attempt
        prefs.lastAttemptDay = today()
        analytics.log(AnalyticsEvent.reviewRequested(attempt))
    }
}
