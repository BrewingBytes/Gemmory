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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton

const val REMINDER_ASK_YES_TAG = "reminder-ask-yes"
const val REMINDER_ASK_NOT_NOW_TAG = "reminder-ask-not-now"

/**
 * The reminder ask (T35): a sheet on the gallery after leaving a fresh finish's completion screen, never over the
 * canvas, the completion screen or Share. Swipe, back or the scrim count as "Not now".
 */
@Composable
fun ReminderAskSheet(time: String, onYes: () -> Unit, onNotNow: () -> Unit) {
    GemSheet(stringResource(R.string.reminder_ask_title), onDismiss = onNotNow) {
        Text(stringResource(R.string.reminder_ask_body, time), style = GemType.body, color = GemColors.Bone)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reminder_ask_settings_line), style = GemType.label, color = GemColors.TextMuted)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(R.string.reminder_ask_yes), onYes, Modifier.fillMaxWidth().testTag(REMINDER_ASK_YES_TAG))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GemTextButton(stringResource(R.string.reminder_ask_not_now), onNotNow, Modifier.testTag(REMINDER_ASK_NOT_NOW_TAG))
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
