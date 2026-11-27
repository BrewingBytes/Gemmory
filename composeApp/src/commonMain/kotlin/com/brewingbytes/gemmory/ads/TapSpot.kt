package com.brewingbytes.gemmory.ads

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.copy.LocalWords
import com.brewingbytes.gemmory.gallery.result
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.QuietButton
import kotlinx.coroutines.launch

/** How a spot looks: the completion's centered text button (DR-D4), or a full-width quiet button (sheets, DR-D12). */
enum class TapLook { TEXT, QUIET }

/**
 * One tap spot and its states (ad-spots DR-D9, shared so the three spots can't drift apart): Ready shows [label];
 * a tap plays the video at once ([play]); then "No video right now." (no retry, no grace, no VIP line), the offline
 * line (bone with a 6dp `fault` square, valid on every ground), "Watch to the end to get it." under the offer after an
 * early close, or the reward line in a polite live region, which replaces the offer. [enabled] false is the 55% look
 * (an encode or share running, DR-D9), read as [disabledReason]. The caller hides it when its video isn't ready.
 */
@Composable
fun TapSpot(
    label: String,
    look: TapLook,
    play: suspend () -> SpotOutcome,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onGranted: (SpotOutcome.Granted) -> Unit = {},
    tag: String = "tap-spot",
    // False when the caller already shows the reward in its own words (the wheel adds it to its result line).
    grantedLine: (Spin) -> Boolean = { true },
) {
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<SpotOutcome?>(null) }
    var playing by remember { mutableStateOf(false) }
    val granted = outcome as? SpotOutcome.Granted
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            granted != null && !grantedLine(granted.spin) -> Box(Modifier.testTag("$tag-granted"))
            granted != null -> Text(
                granted.spin.result(words),
                style = GemType.body,
                color = GemColors.Bone,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("$tag-granted").semantics { liveRegion = LiveRegionMode.Polite },
            )
            outcome == SpotOutcome.NoVideo || outcome == SpotOutcome.Gone -> Text(
                words.text("spot_no_video"),
                style = GemType.body,
                color = GemColors.Bone,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("$tag-none").semantics { liveRegion = LiveRegionMode.Polite },
            )
            outcome == SpotOutcome.Offline -> Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("$tag-offline").semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(GemColors.Fault))
                Spacer(Modifier.width(8.dp))
                Text(words.text("video_offline"), style = GemType.body, color = GemColors.Bone)
            }
            else -> {
                val tap = {
                    if (!playing && enabled) {
                        playing = true
                        scope.launch {
                            val result = play()
                            outcome = result
                            playing = false
                            if (result is SpotOutcome.Granted) onGranted(result)
                        }
                    }
                }
                val reason = disabledReason
                val a11y = Modifier.semantics {
                    if (!enabled) {
                        disabled()
                        if (reason != null) contentDescription = "$label, $reason"
                    }
                }
                when (look) {
                    TapLook.TEXT -> PlayTextButton(label, tap, Modifier.alpha(if (enabled) 1f else 0.55f).testTag(tag).then(a11y))
                    TapLook.QUIET -> QuietButton(label, tap, Modifier.fillMaxWidth().testTag(tag).then(a11y), enabled = enabled && !playing)
                }
                if (outcome == SpotOutcome.ClosedEarly) {
                    Text(
                        words.text("spot_watch_to_end"),
                        style = GemType.label,
                        color = GemColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag("$tag-closed-early").semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/** The completion's offer (DR-D4): a centered underlined text button with the 12dp play mark, 48dp tall. */
@Composable
private fun PlayTextButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                },
                GemColors.Bone,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = GemType.button, color = GemColors.Bone, textDecoration = TextDecoration.Underline)
    }
}

/**
 * Calls [onSeen] once, the first time this element is at least half visible in its window (DR-D20: `rewarded_offer_seen`
 * counts only an offer the player could see, as T56 counts its row once its text is fully shown).
 */
fun Modifier.onHalfVisible(onSeen: () -> Unit): Modifier = composed {
    var done by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onSeen)
    onGloballyPositioned { coordinates ->
        if (done || coordinates.size.height == 0) return@onGloballyPositioned
        val shown = coordinates.boundsInWindow().height / coordinates.size.height
        if (shown >= 0.5f) {
            done = true
            latest()
        }
    }
}
