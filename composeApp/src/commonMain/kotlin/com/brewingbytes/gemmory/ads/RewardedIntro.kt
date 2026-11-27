package com.brewingbytes.gemmory.ads

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.copy.LocalWords
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.rememberTouchExploration
import kotlinx.coroutines.delay

/** The intro's countdown before the video starts on its own (DR-D13). */
const val INTRO_SECONDS = 5

const val INTRO_SKIP_TAG = "intro-skip"
const val INTRO_WATCH_TAG = "intro-watch"

/**
 * The rewarded interstitial's intro (1.0.7, ad-spots DR-D13; Google requires it: the reward, a skip, time to use it).
 * A sheet over the gallery: "A short video for 1 fill", "It starts in 5 seconds. Skip if you'd rather not.", quiet
 * Skip first, then primary Watch now. Back, a swipe or the scrim are Skip. The countdown starts once the sheet is up
 * and ticks as plain text; at 0 the video starts ([onWatch]). With a screen reader exploring by touch there is no
 * countdown at all: the video starts only on Watch now (WCAG 2.2.1, the design system's no-timeout rule under TalkBack).
 */
@Composable
fun RewardedIntroSheet(fills: Int, onWatch: () -> Unit, onSkip: () -> Unit, touchExplorationForTest: Boolean? = null) {
    val words = LocalWords.current
    val exploring = rememberTouchExploration()
    val timed = remember { !(touchExplorationForTest ?: exploring()) }
    var left by remember { mutableIntStateOf(INTRO_SECONDS) }
    val watch by rememberUpdatedState(onWatch)
    LaunchedEffect(timed) {
        if (!timed) return@LaunchedEffect
        // The sheet slides up over ~300ms: the count starts once it's there, so Skip is up for the whole 5 s.
        delay(SHEET_SETTLE_MS)
        while (left > 0) {
            delay(1_000)
            left--
        }
        watch()
    }
    GemSheet(words.plural("intro_title", fills, fills), onSkip) {
        Text(
            if (timed) words.plural("intro_body", left, left) else words.text("intro_body_untimed"),
            style = GemType.body,
            color = GemColors.TextMuted,
            modifier = Modifier.fillMaxWidth().testTag("intro-body"),
        )
        Spacer(Modifier.height(16.dp))
        QuietButton(words.text("intro_skip"), onSkip, Modifier.fillMaxWidth().testTag(INTRO_SKIP_TAG))
        Spacer(Modifier.height(8.dp))
        PrimaryButton(words.text("intro_watch"), onWatch, Modifier.fillMaxWidth().testTag(INTRO_WATCH_TAG))
    }
}

private const val SHEET_SETTLE_MS = 350L
