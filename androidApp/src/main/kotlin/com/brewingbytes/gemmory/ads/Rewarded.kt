package com.brewingbytes.gemmory.ads

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.play.RepairHow
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Inventory
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.LoadFailure
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.policy.RewardedRules
import com.brewingbytes.gemmory.policy.Unavailable
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.UnavailableReason
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a rewarded video unlocks: a locked bundled picture, one more photo canvas (kept, #38), 5 fills (#38), More
 * colors on the photo in the flow (#29), or a broken streak brought back (#102).
 */
sealed interface RewardTarget {
    val type: RewardType

    data class Picture(val id: String) : RewardTarget {
        override val type get() = RewardType.PICTURE
    }

    data object PhotoCanvas : RewardTarget {
        override val type get() = RewardType.PHOTO_CANVAS
    }

    /** From the gallery's Inventory sheet (#38), never the canvas. */
    data object Fills : RewardTarget {
        override val type get() = RewardType.FILLS
    }

    /** From Preview (#29): More colors for the photo in the flow, that draft only. */
    data object Colors : RewardTarget {
        override val type get() = RewardType.COLORS
    }

    /** From the streak screen's repair panel (#102), drawn inline there, never as the sheet. */
    data object StreakRepair : RewardTarget {
        override val type get() = RewardType.STREAK_REPAIR
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

    /**
     * Locked picture: "Unlocked" for 600 ms, then the canvas opens (design D11). Fills: "5 fills added" (#38). Colors:
     * "More colors unlocked" (#29). Streak repair: the streak is back, then the panel goes (#102).
     */
    data object Unlocked : RewardPhase
}

/** [closedEarly]: the last video was closed before its reward, "Watch to the end to unlock." (design D11). */
data class RewardedUi(val target: RewardTarget, val phase: RewardPhase, val closedEarly: Boolean = false)

/** What the app does after the sheet closes on a reward. */
sealed interface RewardedEvent {
    data class OpenPicture(val id: String) : RewardedEvent

    /** The extra-canvas grace: Start the photo canvas on Preview right away (with More colors unlocked, #29). */
    data object StartPhotoCanvas : RewardedEvent

    /** More colors for the photo on Preview (#29): from the colors video, or a canvas video (one video gets both). */
    data object MoreColorsUnlocked : RewardedEvent

    /** The broken streak is back at [length] days, with the missed [days] repaired (#102): the streak screen rolls it up. */
    data class StreakRestored(val length: Int, val days: List<Long>) : RewardedEvent
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

    /** The streak repair panel's one tap (#102): the video shows as soon as it's loaded. */
    private var watchWhenReady = false

    fun open(target: RewardTarget, activity: Activity) {
        if (ui != null) return
        ui = RewardedUi(target, RewardPhase.Loading)
        app.analytics.log(AnalyticsEvent.rewardedOffered(target.type))
        load(activity)
    }

    /**
     * "Watch a video" on the streak screen's repair panel (#102): one tap loads the video and shows it once it's
     * there; Try again does the same. The panel is the sheet's states drawn inline.
     */
    fun repairStreak(activity: Activity) {
        val current = ui
        if (current != null && current.target != RewardTarget.StreakRepair) return
        watchWhenReady = true
        when (current?.phase) {
            null -> open(RewardTarget.StreakRepair, activity)
            RewardPhase.Ready -> watch(activity)
            is RewardPhase.NotAvailable -> load(activity)
            RewardPhase.Loading, RewardPhase.Showing, RewardPhase.Unlocked -> Unit
        }
    }

    /** Swipe, back or the scrim. Ignored while the video is up or "Unlocked" is showing. */
    fun dismiss() {
        val phase = ui?.phase
        if (phase == RewardPhase.Showing || phase == RewardPhase.Unlocked) return
        job?.cancel()
        watchWhenReady = false
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
                            graceAvailable = graceAvailable(current.target),
                        ),
                    )
                }
            }
            ui = ui?.copy(phase = phase)
            if (phase == RewardPhase.Ready && watchWhenReady) watch(activity)
        }
    }

    /** Today's grace for [target]; a streak repair's also needs no grace repair in the last 30 days (#102). */
    private suspend fun graceAvailable(target: RewardTarget): Boolean =
        monetization.graceAvailable(target.type) && (target != RewardTarget.StreakRepair || app.streak.graceRepairAllowed())

    fun watch(activity: Activity) {
        val current = ui?.takeIf { it.phase == RewardPhase.Ready } ?: return
        // The repair panel's tap is used up here: a video closed early, or one that never showed, waits for a new tap.
        watchWhenReady = false
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
                    // Back to Preview, whose line now reads "Uses a kept photo canvas (1 left)."
                    monetization.grantReward()
                    app.analytics.log(AnalyticsEvent.inventoryEarned(Item.PHOTO_CANVAS, EarnSource.VIDEO, 1))
                    ui = null
                    event = RewardedEvent.MoreColorsUnlocked
                }
                RewardTarget.Fills -> fillsAdded(EarnSource.VIDEO)
                RewardTarget.Colors -> colorsUnlocked()
                RewardTarget.StreakRepair -> streakRestored(RepairHow.VIDEO)
            }
        }
    }

    /** "Use an unlock token" on a locked picture's sheet (#38): opens it for good, like a video. */
    fun useToken() {
        val target = ui?.target as? RewardTarget.Picture ?: return
        if (ui?.phase == RewardPhase.Showing || ui?.phase == RewardPhase.Unlocked) return
        job?.cancel()
        job = viewModelScope.launch {
            if (!monetization.useToken(target.id)) return@launch
            app.analytics.log(AnalyticsEvent.inventorySpent(Item.UNLOCK_TOKEN))
            unlocked(target.id)
        }
    }

    /** 5 fills (#38): "5 fills added" for 600 ms, then the sheet closes. */
    private suspend fun fillsAdded(source: EarnSource) {
        monetization.earnFills()
        app.analytics.log(AnalyticsEvent.inventoryEarned(Item.FILLS, source, Inventory.FILLS_PER_VIDEO))
        ui = ui?.copy(phase = RewardPhase.Unlocked)
        delay(UNLOCKED_MS)
        ui = null
    }

    /**
     * The broken streak comes back (#102): the panel shows it restored for 600 ms, then goes, and the streak screen rolls
     * the number up. Nothing left to repair (a restore elsewhere): the panel just goes.
     */
    private suspend fun streakRestored(how: RepairHow) {
        watchWhenReady = false
        val result = app.streak.repair(how)
        if (result == null) {
            ui = null
            return
        }
        ui = ui?.copy(phase = RewardPhase.Unlocked)
        delay(UNLOCKED_MS)
        ui = null
        event = RewardedEvent.StreakRestored(result.length, result.covered.map { it.day })
    }

    /** "More colors unlocked" for 600 ms, then back to Preview with More allowed (#29). */
    private suspend fun colorsUnlocked() {
        ui = ui?.copy(phase = RewardPhase.Unlocked)
        delay(UNLOCKED_MS)
        ui = null
        event = RewardedEvent.MoreColorsUnlocked
    }

    /** "Unlock picture" / "Start canvas" / "Use more colors" on the online no-fill grace offer. */
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
                    app.analytics.log(AnalyticsEvent.inventoryEarned(Item.PHOTO_CANVAS, EarnSource.GRACE, 1))
                    ui = null
                    event = RewardedEvent.StartPhotoCanvas
                }
                RewardTarget.Fills -> fillsAdded(EarnSource.GRACE)
                RewardTarget.Colors -> colorsUnlocked()
                RewardTarget.StreakRepair -> streakRestored(RepairHow.GRACE)
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
    /** Unlock tokens held (#38): a locked picture's sheet offers one under the video. */
    tokens: Int = 0,
    onToken: () -> Unit = {},
) {
    val picture = ui.target is RewardTarget.Picture
    val fills = ui.target is RewardTarget.Fills
    val colors = ui.target is RewardTarget.Colors
    val strings = strings()
    val title = stringResource(
        when {
            picture -> R.string.watch_to_unlock
            fills -> R.string.rewarded_fills_title
            colors -> R.string.rewarded_colors_title
            else -> R.string.rewarded_canvas_title
        },
    )
    GemSheet(title, onDismiss, returnFocusTo) {
        val phase = ui.phase
        when (phase) {
            RewardPhase.Unlocked -> {
                Text(
                    when {
                        fills -> strings.plural(R.plurals.rewarded_fills_added, Inventory.FILLS_PER_VIDEO, Inventory.FILLS_PER_VIDEO)
                        colors -> strings.text(R.string.rewarded_colors_unlocked)
                        else -> strings.text(R.string.rewarded_unlocked)
                    },
                    style = GemType.body,
                    color = GemColors.Bone,
                    modifier = Modifier.testTag("rewarded-unlocked"),
                )
                return@GemSheet
            }
            is RewardPhase.NotAvailable -> Text(
                strings.text(
                    when (phase.kind) {
                        Unavailable.GRACE_OFFER -> if (fills) R.string.rewarded_grace_fills else R.string.video_grace
                        Unavailable.NO_VIDEO -> R.string.video_none
                        Unavailable.OFFLINE -> R.string.rewarded_offline
                    },
                ),
                style = GemType.body,
                // `fault` is offline wording only, and valid on sheets (design system, valid grounds).
                color = if (phase.kind == Unavailable.OFFLINE) GemColors.Fault else GemColors.Bone,
                modifier = Modifier.testTag("rewarded-unavailable"),
            )
            else -> Text(
                when {
                    picture -> strings.text(R.string.rewarded_picture_body)
                    fills -> strings.plural(R.plurals.rewarded_fills_body, Inventory.FILLS_PER_VIDEO, Inventory.FILLS_PER_VIDEO)
                    colors -> strings.text(R.string.rewarded_colors_body)
                    else -> strings.text(R.string.rewarded_canvas_body)
                },
                style = GemType.body,
                color = GemColors.Bone,
            )
        }
        if (ui.closedEarly && (phase == RewardPhase.Loading || phase == RewardPhase.Ready)) {
            Text(
                stringResource(R.string.rewarded_watch_to_end),
                style = GemType.label,
                color = GemColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp).testTag("rewarded-closed-early"),
            )
        }
        Spacer(Modifier.height(20.dp))
        when (phase) {
            RewardPhase.Loading -> {
                PrimaryButton(stringResource(R.string.video_loading), {}, Modifier.fillMaxWidth().testTag("rewarded-primary"), enabled = false)
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
                    stringResource(R.string.rewarded_watch),
                    onWatch,
                    Modifier.fillMaxWidth().testTag("rewarded-primary"),
                    held = phase == RewardPhase.Showing,
                )
            is RewardPhase.NotAvailable -> when (phase.kind) {
                Unavailable.GRACE_OFFER -> {
                    PrimaryButton(
                        when {
                            picture -> strings.text(R.string.rewarded_grace_picture)
                            fills -> strings.plural(R.plurals.rewarded_grace_fills_button, Inventory.FILLS_PER_VIDEO, Inventory.FILLS_PER_VIDEO)
                            colors -> strings.text(R.string.rewarded_grace_colors)
                            else -> strings.text(R.string.rewarded_grace_canvas)
                        },
                        onGrace,
                        Modifier.fillMaxWidth().testTag("rewarded-primary"),
                    )
                    Spacer(Modifier.height(4.dp))
                    Centered {
                        GemTextButton(
                            strings.text(
                                when {
                                    picture -> R.string.rewarded_vip_picture
                                    fills -> R.string.rewarded_vip_fills
                                    colors -> R.string.rewarded_vip_colors
                                    else -> R.string.rewarded_vip_canvas
                                },
                            ),
                            onVip,
                        )
                    }
                }
                Unavailable.NO_VIDEO, Unavailable.OFFLINE -> {
                    QuietButton(stringResource(R.string.action_try_again), onTryAgain, Modifier.fillMaxWidth().testTag("rewarded-try-again"))
                    Spacer(Modifier.height(4.dp))
                    Centered { GemTextButton(stringResource(R.string.rewarded_vip_no_videos), onVip) }
                }
            }
            RewardPhase.Unlocked -> Unit
        }
        // An unlock token (#38) is another way in, offered under the video while nothing else is under way.
        if (picture && tokens > 0 && phase != RewardPhase.Showing) {
            Spacer(Modifier.height(8.dp))
            QuietButton(strings.plural(R.plurals.rewarded_token, tokens, tokens), onToken, Modifier.fillMaxWidth().testTag("rewarded-token"))
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }
}
