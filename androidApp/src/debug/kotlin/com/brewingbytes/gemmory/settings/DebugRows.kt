package com.brewingbytes.gemmory.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.BuildConfig
import com.brewingbytes.gemmory.clips.ClipsActivity
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType

/**
 * Debug builds' Settings rows: "Clips", the founder clip tool (T47). The `perf` build compiles this source set but not
 * its manifest, so the row shows only in debuggable builds.
 */
@Composable
fun DebugRows() {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { context.startActivity(Intent(context, ClipsActivity::class.java)) }
            .padding(horizontal = 16.dp)
            .testTag(CLIPS_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text("Clips", style = GemType.body, color = GemColors.Bone) }
}

const val CLIPS_ROW_TAG = "settings-clips"
