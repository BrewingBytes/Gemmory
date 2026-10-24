package com.brewingbytes.gemmory.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import java.util.Locale

/**
 * "Language · Deutsch" (T49 DR5): Android 13+ only, where the system keeps a language per app. The end text is the
 * language the app shows, in its own name; a tap opens the system's language page for Gemmory. Hidden below 13, where
 * the app follows the phone.
 */
@Composable
fun LanguageRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val shown = strings().locale
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { openLanguagePage(context) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(LANGUAGE_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_language), style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(
            ownName(shown),
            style = GemType.body,
            color = GemColors.Bone,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("settings-language-value"),
        )
    }
}

/** A language in its own words, first letter up: "Deutsch", "Português (Brasil)", "日本語". */
fun ownName(locale: Locale): String =
    locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun openLanguagePage(context: Context) {
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A phone without the page: the app keeps following the phone's language.
    }
}

const val LANGUAGE_TAG = "settings-language"
