package com.brewingbytes.gemmory.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.policy.ReminderSchedule
import com.brewingbytes.gemmory.reminder.NotificationPermission
import com.brewingbytes.gemmory.reminder.Reminders
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemSwitch
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.QuietButton

/** Reminder copy for Settings (design system, Settings → Reminders, T35). */
object ReminderSettingsCopy {
    const val TITLE = "Reminders"
    const val TIME = "Reminder time"
    const val BLOCKED = "Notifications are off for Gemmory"
    const val OPEN_SETTINGS = "Open settings"

    fun around(time: String) = "Around $time"

    fun sheetLine(time: String) = "We'll remind you around $time."
}

const val REMINDERS_TAG = "settings-reminders"
const val REMINDER_TIME_TAG = "settings-reminder-time"

fun reminderTimeTag(minutes: Int) = "reminder-time-$minutes"

/**
 * The Reminders switch and, while on, the Reminder time row (T35). The switch shows on only while reminders are on
 * and Android lets Gemmory post; blocked by the system or with the channel off, it shows off with the problem line
 * and "Open settings". Turning it on without permission asks (Android 13+, while Android still shows the dialog) or
 * opens the app's notification page.
 */
@Composable
internal fun ReminderRows(app: GemmoryApp) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val reminders = app.reminders
    val on by reminders.on.collectAsStateWithLifecycle()
    val minutes by reminders.minutes.collectAsStateWithLifecycle()
    // Re-read on every resume: the player may be back from the system's notification page.
    var allowed by remember { mutableStateOf(reminders.notificationsAllowed()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = reminders.notificationsAllowed() }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = reminders.notificationsAllowed()
        if (granted) reminders.setOn(true)
    }
    val checked = on && allowed
    val time = Reminders.timeLabel(context, minutes)
    // Off at the system level for a player who wants reminders, or who answered Android's dialog already. A fresh
    // install that was never asked isn't "off" yet.
    val blocked = !allowed && (on || reminders.permissionAsked)

    fun turnOn() {
        when {
            allowed -> reminders.setOn(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && NotificationPermission.canAsk(context, activity, reminders.permissionAsked) -> {
                reminders.permissionRequested()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                reminders.setOn(true)
                NotificationPermission.openSettings(context)
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch) { if (it) turnOn() else reminders.setOn(false) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(REMINDERS_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ReminderSettingsCopy.TITLE, style = GemType.body, color = GemColors.Bone)
            if (checked) Text(ReminderSettingsCopy.around(time), style = GemType.label, color = GemColors.Bone)
            if (blocked) {
                // Offline copy on plain felt: bone with a 6dp fault square before it (design system, Colors).
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(6.dp).background(GemColors.Fault).clearAndSetSemantics {})
                    Spacer(Modifier.width(8.dp))
                    Text(ReminderSettingsCopy.BLOCKED, style = GemType.body, color = GemColors.Bone)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        GemSwitch(checked, onCheckedChange = null)
    }
    if (blocked) {
        QuietButton(
            ReminderSettingsCopy.OPEN_SETTINGS,
            onClick = { NotificationPermission.openSettings(context) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).testTag("settings-reminders-open"),
        )
    }
    if (checked) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { pickTime = true }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(REMINDER_TIME_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ReminderSettingsCopy.TIME, style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Text(time, style = GemType.body, color = GemColors.Bone, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
    if (pickTime) {
        ReminderTimeSheet(
            current = minutes,
            label = { Reminders.timeLabel(context, it) },
            onPick = {
                reminders.setMinutes(it)
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

/**
 * The Reminder time sheet: 17:00 to 22:00 in 15-minute steps (21 radio rows, the Selected color sheet's mark), opened
 * scrolled to the current one. A tap saves and closes; swipe, back or the scrim close it without a change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderTimeSheet(current: Int, label: (Int) -> String, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    GemSheet(ReminderSettingsCopy.TIME, onDismiss, returnFocusTo = REMINDER_TIME_TAG) {
        Text(ReminderSettingsCopy.sheetLine(label(current)), style = GemType.body, color = GemColors.TextMuted)
        Spacer(Modifier.size(8.dp))
        val currentRow = remember { BringIntoViewRequester() }
        LaunchedEffect(Unit) { currentRow.bringIntoView() }
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            for (minutes in ReminderSchedule.CHOICES) {
                val selected = minutes == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .then(if (selected) Modifier.bringIntoViewRequester(currentRow) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton) { onPick(minutes) }
                        .testTag(reminderTimeTag(minutes)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioMark(selected)
                    Spacer(Modifier.width(16.dp))
                    Text(label(minutes), style = GemType.body, color = GemColors.Bone)
                }
            }
        }
    }
}
