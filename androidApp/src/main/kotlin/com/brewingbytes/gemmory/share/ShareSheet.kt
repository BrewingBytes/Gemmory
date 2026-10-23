package com.brewingbytes.gemmory.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.timelapse.EncodeState
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton

/**
 * Share progress sheet (design system, Share progress sheet; design D8). Shown while an encode runs and for the
 * results that need words; a finished video goes straight to the share sheet, and Idle shows nothing. Dismissing it
 * while it runs is a Cancel. [progress]: a share before the last gem (#36), whose picture shows "So far", not "After".
 */
@Composable
fun ShareSheet(
    state: EncodeState,
    before: ImageBitmap?,
    after: ImageBitmap?,
    aspect: Float,
    onCancel: () -> Unit,
    onSharePicture: () -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    returnFocusTo: String? = null,
    progress: Boolean = false,
) {
    when (state) {
        is EncodeState.Running -> GemSheet(stringResource(R.string.share_making), onCancel, returnFocusTo) {
            Text(stringResource(R.string.share_making_line), style = GemType.body, color = GemColors.Bone)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(state.percent, Modifier.weight(1f))
                Text(
                    strings().text(R.string.percent, state.percent),
                    style = GemType.mono.copy(fontSize = 16.sp),
                    color = GemColors.Bone,
                    modifier = Modifier.padding(start = 12.dp).widthIn(min = 48.dp).testTag("share-percent"),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GemTextButton(stringResource(R.string.action_cancel), onCancel, Modifier.testTag("share-cancel"))
            }
        }
        is EncodeState.Picture -> GemSheet(stringResource(R.string.share_picture_title), onClose, returnFocusTo) {
            Text(
                stringResource(if (progress) R.string.share_picture_progress else R.string.share_picture_before_after),
                style = GemType.body,
                color = GemColors.Bone,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(before, stringResource(R.string.frame_before), aspect, Modifier.weight(1f))
                Thumb(after, stringResource(if (progress) R.string.frame_so_far else R.string.frame_after), aspect, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.share_picture), onSharePicture, Modifier.fillMaxWidth().testTag("share-picture"))
        }
        EncodeState.Failed -> GemSheet(stringResource(R.string.share_making), onClose, returnFocusTo) {
            Text(stringResource(R.string.share_failed), style = GemType.body, color = GemColors.Bone)
            Spacer(Modifier.height(20.dp))
            QuietButton(stringResource(R.string.action_try_again), onTryAgain, Modifier.fillMaxWidth().testTag("share-try-again"))
        }
        EncodeState.DiskFull -> GemSheet(stringResource(R.string.share_making), onClose, returnFocusTo) {
            Text(stringResource(R.string.share_disk_full), style = GemType.body, color = GemColors.Bone)
        }
        EncodeState.Idle, is EncodeState.Video -> Unit
    }
}

/** 6dp bone bar on a felt-raised track. */
@Composable
private fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .height(6.dp)
            .clip(shape)
            .background(LocalFelt.current.raised)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f) },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).clip(shape).background(GemColors.Bone))
    }
}

@Composable
private fun Thumb(image: ImageBitmap?, label: String, aspect: Float, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = GemType.label, color = GemColors.TextMuted)
        Spacer(Modifier.height(4.dp))
        if (image != null) {
            Image(
                image,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}
