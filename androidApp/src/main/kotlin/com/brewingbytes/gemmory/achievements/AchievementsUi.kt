package com.brewingbytes.gemmory.achievements

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import kotlinx.coroutines.launch

const val ACHIEVEMENTS_TAG = "settings-achievements"

/** Play Games' achievements screen; the result isn't used. */
private const val REQUEST_ACHIEVEMENTS = 4901

/**
 * Sends earned achievements while this screen shows and the app is in front (#49). Only the Gallery and Settings
 * call it, so Play Games' own pop-up never shows over the canvas or the completion screen.
 */
@Composable
fun SendAchievementsWhileShown(achievements: Achievements) {
    val activity = LocalActivity.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(achievements, activity) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            achievements.changes.collect { achievements.sync(activity) }
        }
    }
}

/**
 * Settings → "Achievements · Play Games" (#49), shown only when Play Games can run here. A tap signs in if needed and
 * opens Play Games' achievements screen; meanwhile its end text reads "Checking…". A declined sign-in shows nothing,
 * a failure one line for this visit.
 */
@Composable
fun AchievementsRow(achievements: Achievements) {
    if (!achievements.available) return
    val activity = LocalActivity.current ?: return
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = !opening, role = Role.Button) {
                    opening = true
                    failed = false
                    scope.launch {
                        failed = open(achievements, activity) == OpenResult.FAILED
                        opening = false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ACHIEVEMENTS_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.achievements_title), style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Text(stringResource(if (opening) R.string.achievements_checking else R.string.achievements_value), style = GemType.body, color = GemColors.Bone, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        if (failed) {
            // Offline copy on plain felt: bone with a 6dp fault square before it (design system, Colors).
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp).testTag("settings-achievements-failed"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(6.dp).background(GemColors.Fault).clearAndSetSemantics {})
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.achievements_failed), style = GemType.body, color = GemColors.Bone)
            }
        }
    }
}

private suspend fun open(achievements: Achievements, activity: Activity): OpenResult {
    val (result, intent) = achievements.open(activity)
    if (intent == null) return result
    @Suppress("DEPRECATION") // Play Games' screen must be started for a result (AchievementsClient.getAchievementsIntent).
    return runCatching { activity.startActivityForResult(intent, REQUEST_ACHIEVEMENTS) }
        .fold({ OpenResult.OPENED }, { OpenResult.FAILED })
}
