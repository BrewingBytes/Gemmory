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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * while it runs is a Cancel.
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
) {
    when (state) {
        is EncodeState.Running -> GemSheet("Making your video", onCancel) {
            Text("About 15 seconds of sparkle. Keep the app open.", style = GemType.body, color = GemColors.Bone)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(state.percent, Modifier.weight(1f))
                Text(
                    "${state.percent}%",
                    style = GemType.mono.copy(fontSize = 16.sp),
                    color = GemColors.Bone,
                    modifier = Modifier.padding(start = 12.dp).widthIn(min = 48.dp).testTag("share-percent"),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GemTextButton("Cancel", onCancel, Modifier.testTag("share-cancel"))
            }
        }
        is EncodeState.Picture -> GemSheet("Here's a picture instead", onClose) {
            Text(
                "Video isn't supported on this phone, so here's a before/after picture instead.",
                style = GemType.body,
                color = GemColors.Bone,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(before, "Before", aspect, Modifier.weight(1f))
                Thumb(after, "After", aspect, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Share picture", onSharePicture, Modifier.fillMaxWidth().testTag("share-picture"))
        }
        EncodeState.Failed -> GemSheet("Making your video", onClose) {
            Text("Couldn't make the video. Try again.", style = GemType.body, color = GemColors.Bone)
            Spacer(Modifier.height(20.dp))
            QuietButton("Try again", onTryAgain, Modifier.fillMaxWidth().testTag("share-try-again"))
        }
        EncodeState.DiskFull -> GemSheet("Making your video", onClose) {
            Text("Not enough space on this phone to make the video.", style = GemType.body, color = GemColors.Bone)
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
