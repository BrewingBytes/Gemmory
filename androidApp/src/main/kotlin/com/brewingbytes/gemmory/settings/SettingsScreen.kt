package com.brewingbytes.gemmory.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.billing.BillingCopy
import com.brewingbytes.gemmory.billing.RestoreUi
import com.brewingbytes.gemmory.billing.subscriptionPage
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSwitch
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround

/** docs/privacy-policy.md on main (interim policy from #8; #5 completes it). */
const val PRIVACY_POLICY_URL = "https://github.com/BrewingBytes/Gemmory/blob/main/docs/privacy-policy.md"

/**
 * Settings (design system, Settings): Sound, Haptics, Selected color (#19, opens its sheet), Membership, Manage subscription (VIP only, opens Play), Restore
 * purchases, Privacy choices (only when UMP says privacy options are required, design D3), Delete all photo canvases,
 * Privacy policy, Version. [onMembership] opens the paywall, or Play's subscription page for VIP.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onMembership: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GemmoryApp
    val sound by app.settings.sound.collectAsStateWithLifecycle()
    val haptics by app.settings.haptics.collectAsStateWithLifecycle()
    val highlight by app.settings.highlight.collectAsStateWithLifecycle()
    val privacyChoices by app.consent.privacyOptionsRequired.collectAsStateWithLifecycle()
    val membership by app.billing.membership.collectAsStateWithLifecycle()
    val entitlements by app.billing.entitlements.collectAsStateWithLifecycle()
    val restore by app.billing.restore.collectAsStateWithLifecycle()
    // A result line is for this visit only.
    DisposableEffect(Unit) { onDispose { app.billing.restoreHandled() } }
    val activity = LocalActivity.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickHighlight by rememberSaveable { mutableStateOf(false) }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { pickHighlight = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(HIGHLIGHT_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(HighlightCopy.TITLE, style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Text(
                    HighlightCopy.name(highlight),
                    style = GemType.body,
                    color = GemColors.Bone,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).testTag("settings-highlight-value"),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onMembership)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(MEMBERSHIP_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Membership", style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Text(
                    BillingCopy.membership(membership),
                    style = GemType.body,
                    color = GemColors.Bone,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).testTag("settings-membership-value"),
                )
            }
            // VIP only (D6). A VIP Play couldn't confirm for a week is still offered it: they may want to cancel.
            if (entitlements.vip || membership == Membership.VIP_UNCONFIRMED) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, subscriptionPage(context.packageName, entitlements.plan)))
                            } catch (_: ActivityNotFoundException) {
                            }
                        }
                        .padding(horizontal = 16.dp)
                        .testTag("settings-manage-subscription"),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("Manage subscription", style = GemType.body, color = GemColors.Bone) }
            }
            // A live region, so the result is read out when it appears without hunting for it.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                QuietButton(
                    if (restore == RestoreUi.Checking) BillingCopy.CHECKING else "Restore purchases",
                    onClick = app.billing::restore,
                    enabled = restore != RestoreUi.Checking,
                    modifier = Modifier.testTag("settings-restore"),
                )
                val line = when (val r = restore) {
                    is RestoreUi.Done -> BillingCopy.restored(r.found)
                    RestoreUi.Failed -> BillingCopy.RESTORE_FAILED
                    else -> null
                }
                if (line != null) {
                    Text(line, style = GemType.body, color = GemColors.Bone, modifier = Modifier.padding(top = 8.dp).testTag("settings-restore-result"))
                }
            }
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
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Version", style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
                Text(version, style = GemType.mono.copy(fontSize = 14.sp), color = GemColors.Bone)
            }
        }
    }

    if (pickHighlight) {
        HighlightSheet(
            current = highlight,
            onPick = {
                app.settings.setHighlight(it)
                pickHighlight = false
            },
            onDismiss = { pickHighlight = false },
            returnFocusTo = HIGHLIGHT_TAG,
        )
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
