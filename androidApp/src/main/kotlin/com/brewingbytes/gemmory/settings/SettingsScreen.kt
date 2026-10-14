package com.brewingbytes.gemmory.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSwitch
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.feltGround

/** Hosted by #5 (docs/privacy-policy.md); replace with the published URL when it's live. */
const val PRIVACY_POLICY_URL = "https://github.com/BrewingBytes/Gemmory/blob/main/docs/privacy-policy.md"

/**
 * Settings (design system, Settings): Sound, Haptics, Privacy choices (only when UMP says privacy options are
 * required, design D3), Delete all photo canvases, Privacy policy, Version. Membership, Manage subscription and
 * Restore purchases come with billing (#8).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GemmoryApp
    val sound by app.settings.sound.collectAsStateWithLifecycle()
    val haptics by app.settings.haptics.collectAsStateWithLifecycle()
    val privacyChoices by app.consent.privacyOptionsRequired.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar("Settings", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ToggleRow("Sound", sound, app.settings::setSound)
            ToggleRow("Haptics", haptics, app.settings::setHaptics)
            if (privacyChoices && activity != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { app.consent.showPrivacyOptions(activity) }
                        .padding(horizontal = 16.dp)
                        .testTag("settings-privacy-choices"),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("Privacy choices", style = GemType.body, color = GemColors.Bone) }
            }
            // A plain bone row, not a red button (design D27).
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { confirmDelete = true }
                    .padding(horizontal = 16.dp)
                    .testTag(DELETE_ALL_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text("Delete all photo canvases", style = GemType.body, color = GemColors.Bone) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text("Privacy policy", style = GemType.body, color = GemColors.Bone) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Version", style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
                Text(version, style = GemType.mono.copy(fontSize = 14.sp), color = GemColors.Bone)
            }
        }
    }

    if (confirmDelete) {
        ConfirmSheet(
            title = "Delete all photo canvases?",
            body = "Every photo, grid and its progress is removed from this phone. Pictures and purchases stay.",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                app.store.deleteAllPhotosInBackground()
            },
            onDismiss = { confirmDelete = false },
            returnFocusTo = DELETE_ALL_TAG,
        )
    }
}

private const val DELETE_ALL_TAG = "settings-delete-all"

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
