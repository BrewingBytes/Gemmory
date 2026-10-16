package com.brewingbytes.gemmory.ads

import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.UnavailableReason
import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.policy.RewardedRules
import com.brewingbytes.gemmory.policy.Unavailable
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a rewarded video unlocks: a locked bundled picture, or one more photo canvas today. */
sealed interface RewardTarget {
    val type: RewardType

    data class Picture(val id: String) : RewardTarget {
        override val type get() = RewardType.PICTURE
    }

    data object PhotoCanvas : RewardTarget {
        override val type get() = RewardType.PHOTO_CANVAS
    }
}

/** The rows of the Rewarded states table (design system). */
sealed interface RewardPhase {
    /** "Loading video…", at most 5 s. */
    data object Loading : RewardPhase

    /** The video is loaded: "Watch video". */
    data object Ready : RewardPhase

    /** The video is on screen. */
    data object Showing : RewardPhase

    data class NotAvailable(val kind: Unavailable) : RewardPhase

    /** Locked picture: "Unlocked" for 600 ms, then the canvas opens (design D11). */
    data object Unlocked : RewardPhase
}

/** [closedEarly]: the last video was closed before its reward, "Watch to the end to unlock." (design D11). */
data class RewardedUi(val target: RewardTarget, val phase: RewardPhase, val closedEarly: Boolean = false)

/** What the app does after the sheet closes on a reward. */
sealed interface RewardedEvent {
    data class OpenPicture(val id: String) : RewardedEvent

    /** The extra-canvas grace: Start the photo canvas on Preview right away. */
    data object StartPhotoCanvas : RewardedEvent
}

/**
 * The rewarded sheet's state (design system, Rewarded states; CEO D7; eng D15). Activity-scoped, so it survives
 * rotation. The activity is passed per call for the consent form and the ad and is never stored.
 */
class RewardedViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GemmoryApp
    private val monetization get() = app.monetization
    private val gateway get() = app.ads

    var ui by mutableStateOf<RewardedUi?>(null)
        private set

    var event by mutableStateOf<RewardedEvent?>(null)
        private set

    private var job: Job? = null

    fun open(target: RewardTarget, activity: Activity) {
        if (ui != null) return
        ui = RewardedUi(target, RewardPhase.Loading)
        app.analytics.log(AnalyticsEvent.rewardedOffered(target.type))
        load(activity)
    }

    /** Swipe, back or the scrim. Ignored while the video is up or "Unlocked" is showing. */
    fun dismiss() {
        val phase = ui?.phase
        if (phase == RewardPhase.Showing || phase == RewardPhase.Unlocked) return
        job?.cancel()
        ui = null
    }

    fun tryAgain(activity: Activity) = load(activity)

    fun onEventHandled() {
        event = null
    }

    private fun load(activity: Activity) {
        val current = ui ?: return
        job?.cancel()
        ui = current.copy(phase = RewardPhase.Loading)
        job = viewModelScope.launch {
            // Consent comes before any ad request; a player with no consent yet sees the form here (founder call #7).
            val allowed = gateway.ensureConsent(activity)
            if (allowed) gateway.start()
            val result = if (allowed) gateway.loadRewarded() else RewardedLoad.Failed(LoadFailure.OTHER)
            val phase = when (result) {
                RewardedLoad.Loaded -> RewardPhase.Ready
                is RewardedLoad.Failed -> {
                    val online = gateway.online()
                    app.analytics.log(AnalyticsEvent.rewardedUnavailable(current.target.type, UnavailableReason.of(result.failure, online)))
                    RewardPhase.NotAvailable(
                        RewardedRules.unavailable(
                            failure = result.failure,
                            online = online,
                            sdkReady = gateway.sdkReady,
                            graceAvailable = monetization.graceAvailable(current.target.type),
                        ),
                    )
                }
            }
            ui = ui?.copy(phase = phase)
        }
    }

    fun watch(activity: Activity) {
        val current = ui?.takeIf { it.phase == RewardPhase.Ready } ?: return
        var earned = false
        val shown = gateway.showRewarded(
            activity,
            onEarned = { earned = true },
            onClosed = { shown ->
                if (!shown) {
                    // The player saw no video: not "closed early", no 2-min interstitial quiet; just load another.
                    load(activity)
                    return@showRewarded
                }
                monetization.onRewardedShown()
                if (earned) {
                    app.analytics.log(AnalyticsEvent.rewardedWatched(current.target.type))
                    grant(current.target)
                } else {
                    // Closed early: no reward, no grace, no penalty; a fresh video loads for another go.
                    ui = ui?.copy(closedEarly = true)
                    load(activity)
                }
            },
        )
        if (shown) ui = current.copy(phase = RewardPhase.Showing, closedEarly = false) else load(activity)
    }

    private fun grant(target: RewardTarget) {
        job = viewModelScope.launch {
            when (target) {
                is RewardTarget.Picture -> unlocked(target.id)
                RewardTarget.PhotoCanvas -> {
                    // Back to Preview, whose line now reads "Uses a bonus canvas from a video."
                    monetization.grantReward()
                    ui = null
                }
            }
        }
    }

    /** "Unlock picture" / "Start canvas" on the online no-fill grace offer. */
    fun useGrace() {
        val current = ui ?: return
        if ((current.phase as? RewardPhase.NotAvailable)?.kind != Unavailable.GRACE_OFFER) return
        job = viewModelScope.launch {
            if (!monetization.useGrace(current.target.type)) {
                ui = ui?.copy(phase = RewardPhase.NotAvailable(Unavailable.NO_VIDEO))
                return@launch
            }
            app.analytics.log(AnalyticsEvent.graceUnlockGranted(current.target.type))
            when (val target = current.target) {
                is RewardTarget.Picture -> unlocked(target.id)
                RewardTarget.PhotoCanvas -> {
                    ui = null
                    event = RewardedEvent.StartPhotoCanvas
                }
            }
        }
    }

    private suspend fun unlocked(pictureId: String) {
        monetization.unlocks.unlock(pictureId)
        ui = ui?.copy(phase = RewardPhase.Unlocked)
        delay(UNLOCKED_MS)
        ui = null
        event = RewardedEvent.OpenPicture(pictureId)
    }

    private companion object {
        const val UNLOCKED_MS = 600L
    }
}

/** The rewarded sheet, every Rewarded-states row with its exact copy (design system). VIP texts open nothing until #8. */
@Composable
fun RewardedSheet(
    ui: RewardedUi,
    onWatch: () -> Unit,
    onGrace: () -> Unit,
    onTryAgain: () -> Unit,
    onVip: () -> Unit,
    onDismiss: () -> Unit,
    returnFocusTo: String? = null,
) {
    val picture = ui.target is RewardTarget.Picture
    GemSheet(if (picture) "Watch to unlock" else "Another photo canvas", onDismiss, returnFocusTo) {
        val phase = ui.phase
        when (phase) {
            RewardPhase.Unlocked -> {
                Text("Unlocked", style = GemType.body, color = GemColors.Bone, modifier = Modifier.testTag("rewarded-unlocked"))
                return@GemSheet
            }
            is RewardPhase.NotAvailable -> Text(
                when (phase.kind) {
                    Unavailable.GRACE_OFFER -> "No video right now. This one's on us today."
                    Unavailable.NO_VIDEO -> "No video right now. Try again in a bit."
                    Unavailable.OFFLINE -> "You're offline, connect to watch a video"
                },
                style = GemType.body,
                // `fault` is offline wording only, and valid on sheets (design system, valid grounds).
                color = if (phase.kind == Unavailable.OFFLINE) GemColors.Fault else GemColors.Bone,
                modifier = Modifier.testTag("rewarded-unavailable"),
            )
            else -> Text(
                if (picture) "Watch a short video to unlock this picture for good." else "Watch a short video for one more photo canvas today.",
                style = GemType.body,
                color = GemColors.Bone,
            )
        }
        if (ui.closedEarly && (phase == RewardPhase.Loading || phase == RewardPhase.Ready)) {
            Text(
                "Watch to the end to unlock.",
                style = GemType.label,
                color = GemColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp).testTag("rewarded-closed-early"),
            )
        }
        Spacer(Modifier.height(20.dp))
        when (phase) {
            RewardPhase.Loading -> {
                PrimaryButton("Loading video…", {}, Modifier.fillMaxWidth().testTag("rewarded-primary"), enabled = false)
                // Decoration: the button already says "Loading video…", and the line's own 48dp-tall semantics bounds
                // would overlap the button's touch target.
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp).clearAndSetSemantics {},
                    color = GemColors.Bone,
                    trackColor = Color.Transparent,
                )
            }
            RewardPhase.Ready, RewardPhase.Showing ->
                PrimaryButton(
                    "Watch video",
                    onWatch,
                    Modifier.fillMaxWidth().testTag("rewarded-primary"),
                    held = phase == RewardPhase.Showing,
                )
            is RewardPhase.NotAvailable -> when (phase.kind) {
                Unavailable.GRACE_OFFER -> {
                    PrimaryButton(if (picture) "Unlock picture" else "Start canvas", onGrace, Modifier.fillMaxWidth().testTag("rewarded-primary"))
                    Spacer(Modifier.height(4.dp))
                    Centered {
                        GemTextButton(if (picture) "Or get VIP: every picture unlocked" else "Or get VIP: unlimited photo canvases", onVip)
                    }
                }
                Unavailable.NO_VIDEO, Unavailable.OFFLINE -> {
                    QuietButton("Try again", onTryAgain, Modifier.fillMaxWidth().testTag("rewarded-try-again"))
                    Spacer(Modifier.height(4.dp))
                    Centered { GemTextButton("Get VIP: no videos needed", onVip) }
                }
            }
            RewardPhase.Unlocked -> Unit
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }
}
