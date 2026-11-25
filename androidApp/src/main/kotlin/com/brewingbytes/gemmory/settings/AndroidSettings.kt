package com.brewingbytes.gemmory.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.app.LocalAppGraph
import com.brewingbytes.gemmory.achievements.AchievementsRow
import com.brewingbytes.gemmory.billing.BillingCopy
import com.brewingbytes.gemmory.billing.RestoreUi
import com.brewingbytes.gemmory.billing.subscriptionPage
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.policy.Membership
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.QuietButton

/**
 * Android's Settings: the shared [SettingsScreen] (T64) with the rows that stay in this shell, Reminders (T35),
 * Language (T49), Achievements (#49), Membership, Manage subscription (VIP only, opens Play), Restore purchases,
 * Privacy choices (only when UMP says privacy options are required, design D3) and Clips (debug builds only, T47).
 * [onMembership] opens the paywall, or Play's subscription page for VIP.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onMembership: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GemmoryApp
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val slots = remember(app, onMembership) {
        SettingsSlots(
            reminders = { ReminderRows(app) },
            account = { AccountRows(app, onMembership) },
            debug = { DebugRows() },
        )
    }
    // The app's one graph, also for a test that draws Settings on its own, outside MainActivity's provider.
    CompositionLocalProvider(LocalAppGraph provides app.graph) {
        SettingsScreen(onBack = onBack, version = version, slots = slots)
    }
}

/** Language, Achievements, Membership, Manage subscription, Restore purchases and Privacy choices, in that order. */
@Composable
private fun AccountRows(app: GemmoryApp, onMembership: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val privacyChoices by app.consent.privacyOptionsRequired.collectAsStateWithLifecycle()
    val membership by app.billing.membership.collectAsStateWithLifecycle()
    val entitlements by app.billing.entitlements.collectAsStateWithLifecycle()
    val restore by app.billing.restore.collectAsStateWithLifecycle()
    // A result line is for this visit only.
    DisposableEffect(Unit) { onDispose { app.billing.restoreHandled() } }
    val billingCopy = BillingCopy(strings())

    // The app's language (T49 DR5): Android 13+ only.
    LanguageRow()
    // Play Games achievements (#49): only where Play Games can run.
    AchievementsRow(app.achievements)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onMembership)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(MEMBERSHIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_membership), style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(
            billingCopy.membership(membership),
            style = GemType.body,
            color = GemColors.Bone,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        ) { Text(stringResource(R.string.settings_manage_subscription), style = GemType.body, color = GemColors.Bone) }
    }
    // A live region, so the result is read out when it appears without hunting for it.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        QuietButton(
            if (restore == RestoreUi.Checking) billingCopy.checking else stringResource(R.string.settings_restore),
            onClick = app.billing::restore,
            enabled = restore != RestoreUi.Checking,
            modifier = Modifier.testTag("settings-restore"),
        )
        val line = when (val r = restore) {
            is RestoreUi.Done -> billingCopy.restored(r.found)
            RestoreUi.Failed -> billingCopy.restoreFailed
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
        ) { Text(stringResource(R.string.settings_privacy_choices), style = GemType.body, color = GemColors.Bone) }
    }
}
