package com.brewingbytes.gemmory.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.game.MusicRules
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSwitch
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt

/** Music copy (design system, Settings → Music, T42). */
object MusicCopy {
    const val TITLE = "Music"
    const val VOLUME = "Music volume"
    const val CHIP = "Music is playing"
    const val TURN_OFF = "Turn off"
    const val OFF_LINE = "Music off. Turn it back on in Settings."

    fun percent(value: Int) = "$value%"
}

const val MUSIC_TAG = "settings-music"
const val MUSIC_VOLUME_TAG = "settings-music-volume"

/**
 * The Music switch and, while it's on, the Music volume row (T42): the label, a slider in 10% steps (4dp felt-raised
 * track, bone active part, 20dp bone thumb) and the value in mono 13sp. TalkBack: "Music, on, switch"; "Music volume,
 * 30 percent, slider".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicRows(settings: AppSettings) {
    val on by settings.music.collectAsStateWithLifecycle()
    val volume by settings.musicVolume.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = settings::setMusic)
            .padding(horizontal = 16.dp)
            .testTag(MUSIC_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(MusicCopy.TITLE, style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
        GemSwitch(on, onCheckedChange = null)
    }
    if (!on) return
    val raised = LocalFelt.current.raised
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The slider reads its own label and value; these two would read them twice.
        Text(MusicCopy.VOLUME, style = GemType.body, color = GemColors.Bone, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(16.dp))
        Slider(
            value = volume.toFloat(),
            onValueChange = { settings.setMusicVolume(it.toInt()) },
            valueRange = MusicRules.MIN_VOLUME.toFloat()..MusicRules.MAX_VOLUME.toFloat(),
            steps = (MusicRules.MAX_VOLUME - MusicRules.MIN_VOLUME) / MusicRules.VOLUME_STEP - 1,
            thumb = { Box(Modifier.size(20.dp).clip(CircleShape).background(GemColors.Bone)) },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val range = state.valueRange
                    val fraction = (state.value - range.start) / (range.endInclusive - range.start)
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(raised, cornerRadius = radius)
                    drawRoundRect(GemColors.Bone, topLeft = Offset.Zero, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                }
            },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = MusicCopy.VOLUME
                    stateDescription = A11yText.musicVolume(volume)
                }
                .testTag(MUSIC_VOLUME_TAG),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            MusicCopy.percent(volume),
            style = GemType.mono.copy(fontSize = 13.sp),
            color = GemColors.Bone,
            modifier = Modifier.testTag("settings-music-volume-value").clearAndSetSemantics {},
        )
    }
}
