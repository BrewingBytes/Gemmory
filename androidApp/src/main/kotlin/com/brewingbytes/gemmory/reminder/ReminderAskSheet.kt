package com.brewingbytes.gemmory.reminder

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton

/** The ask's copy (design system, Evening reminder; plan-design-review D10, D12). */
object ReminderAskCopy {
    const val TITLE = "Remind me in the evening?"
    const val SETTINGS_LINE = "Change the time or turn it off in Settings."
    const val YES = "Yes, remind me"
    const val NOT_NOW = "Not now"

    fun body(time: String) =
        "One quiet note a day around $time, only when there's something to play: a new picture, today's picture, a " +
            "streak to keep, or one you're close to finishing."

    /** The gallery's one line once reminders are on. */
    fun onLine(time: String) = "Reminders on, around $time. Change it in Settings."
}

const val REMINDER_ASK_YES_TAG = "reminder-ask-yes"
const val REMINDER_ASK_NOT_NOW_TAG = "reminder-ask-not-now"

/**
 * The reminder ask (T35): a sheet on the gallery after leaving a fresh finish's completion screen, never over the
 * canvas, the completion screen or Share. Swipe, back or the scrim count as "Not now".
 */
@Composable
fun ReminderAskSheet(time: String, onYes: () -> Unit, onNotNow: () -> Unit) {
    GemSheet(ReminderAskCopy.TITLE, onDismiss = onNotNow) {
        Text(ReminderAskCopy.body(time), style = GemType.body, color = GemColors.Bone)
        Spacer(Modifier.height(8.dp))
        Text(ReminderAskCopy.SETTINGS_LINE, style = GemType.label, color = GemColors.TextMuted)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(ReminderAskCopy.YES, onYes, Modifier.fillMaxWidth().testTag(REMINDER_ASK_YES_TAG))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GemTextButton(ReminderAskCopy.NOT_NOW, onNotNow, Modifier.testTag(REMINDER_ASK_NOT_NOW_TAG))
        }
    }
}

/** Android 13+'s notification permission (T35). Older versions need none: notifications are on unless turned off. */
object NotificationPermission {
    /** Android 13+ and not granted yet: the ask's Yes shows the system dialog. */
    fun needed(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /**
     * Whether a request can still show Android's dialog: never asked, or asked once and Android wants a rationale.
     * After a second "no" Android answers by itself, so Settings opens the app's notification page instead.
     */
    fun canAsk(context: Context, activity: Activity?, askedBefore: Boolean): Boolean =
        needed(context) && (!askedBefore || activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true)

    /** The app's page in the system's notification settings. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }
}
