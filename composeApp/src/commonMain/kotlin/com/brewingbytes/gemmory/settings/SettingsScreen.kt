package com.brewingbytes.gemmory.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.app.LocalAppGraph
import com.brewingbytes.gemmory.copy.LocalWords
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSwitch
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.feltGround

/** docs/privacy-policy.md on main (interim policy from #8; #5 completes it). */
const val PRIVACY_POLICY_URL = "https://github.com/BrewingBytes/Gemmory/blob/main/docs/privacy-policy.md"

/**
 * The Settings rows that stay in the platform shell (iOS Stage 1c, T64, eng D21 "minimal Settings"), each drawn in its
 * place in the list. Android fills them today; iOS fills each in the stage that brings its service (reminders and
 * language in Stage 2, membership and restore in 3, Game Center in 4) and leaves the rest empty.
 */
class SettingsSlots(
    /** After Milestones: Reminders and, while on, Reminder time (T35). */
    val reminders: @Composable () -> Unit = {},
    /**
     * After Selected color: Language (T49), Achievements (#49), Membership, Manage subscription, Restore purchases and
     * Privacy choices (design D3).
     */
    val account: @Composable () -> Unit = {},
    /** After Privacy policy: debug builds' Clips (T47). */
    val debug: @Composable () -> Unit = {},
)

/**
 * Settings (design system, Settings): Sound, Haptics, Music and Music volume (T42), Milestones (#35), [SettingsSlots.reminders],
 * Selected color (#19, opens its sheet), [SettingsSlots.account], Delete all photo canvases, Privacy policy,
 * [SettingsSlots.debug], Version ([version], the app's own). Shared since T64 (iOS Stage 1c).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, version: String, slots: SettingsSlots = SettingsSlots()) {
    val graph = LocalAppGraph.current
    val settings = graph.settings
    val words = LocalWords.current
    val uris = LocalUriHandler.current
    val sound by settings.sound.collectAsStateWithLifecycle()
    val haptics by settings.haptics.collectAsStateWithLifecycle()
    val milestones by settings.milestones.collectAsStateWithLifecycle()
    val highlight by settings.highlight.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickHighlight by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar(words.text("settings_title"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ToggleRow(words.text("settings_sound"), sound, settings::setSound)
            ToggleRow(words.text("settings_haptics"), haptics, settings::setHaptics)
            // Ambient music (T42): its own switch and, while on, the volume.
            MusicRows(settings)
            ToggleRow(words.text("settings_milestones"), milestones, settings::setMilestones)
            // The evening reminder (T35): the switch and, while on, its time.
            slots.reminders()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { pickHighlight = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(HIGHLIGHT_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(words.text("highlight_title"), style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Text(
                    words.text(HighlightCopy.name(highlight)),
                    style = GemType.body,
                    color = GemColors.Bone,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("settings-highlight-value"),
                )
            }
            slots.account()
            // A plain bone row, not a red button (design D27).
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { confirmDelete = true }
                    .padding(horizontal = 16.dp)
                    .testTag(DELETE_ALL_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(words.text("settings_delete_all"), style = GemType.body, color = GemColors.Bone) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) {
                        // A phone with no browser: nothing opens, as before.
                        runCatching { uris.openUri(PRIVACY_POLICY_URL) }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(words.text("settings_privacy_policy"), style = GemType.body, color = GemColors.Bone) }
            slots.debug()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(words.text("settings_version"), style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
                Text(version, style = GemType.mono.copy(fontSize = 14.sp), color = GemColors.Bone)
            }
        }
    }

    if (pickHighlight) {
        HighlightSheet(
            current = highlight,
            onPick = {
                settings.setHighlight(it)
                pickHighlight = false
            },
            onDismiss = { pickHighlight = false },
            returnFocusTo = HIGHLIGHT_TAG,
        )
    }

    if (confirmDelete) {
        ConfirmSheet(
            title = words.text("settings_delete_all_title"),
            body = words.text("settings_delete_all_body"),
            confirm = words.text("action_delete"),
            onConfirm = {
                confirmDelete = false
                graph.store.deleteAllPhotosInBackground()
            },
            onDismiss = { confirmDelete = false },
            returnFocusTo = DELETE_ALL_TAG,
        )
    }
}

private const val DELETE_ALL_TAG = "settings-delete-all"

/** The Selected color row, where focus returns when its sheet closes. */
const val HIGHLIGHT_TAG = "settings-highlight"

/** Where TalkBack focus returns when the paywall opened from Membership closes. */
const val MEMBERSHIP_TAG = "settings-membership"

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
        GemSwitch(checked, onCheckedChange = null)
    }
}
