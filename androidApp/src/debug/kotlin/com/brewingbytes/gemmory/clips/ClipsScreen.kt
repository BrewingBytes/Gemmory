package com.brewingbytes.gemmory.clips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brewingbytes.gemmory.timelapse.ClipOrder
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.Chip
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround

/**
 * Clips (T47, design system decision row 2026-10-22): a plain list on felt in Figtree. Each picture row has three
 * order chips (one of them), three format chips (any of them, all on: 4 files) and Render. One render at a time: the
 * row reads "Rendering… 45%", then "Saved 4 files" with the folder; a failure adds one line under the row.
 */
@Composable
fun ClipsScreen(onBack: () -> Unit, model: ClipsViewModel = viewModel()) {
    val ui by model.ui.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar("Clips", onBack)
        val pictures = ui.pictures ?: return@Column
        if (pictures.isEmpty()) {
            Text(
                "No pictures on this phone yet",
                style = GemType.body,
                color = GemColors.Bone,
                modifier = Modifier.padding(16.dp).testTag(EMPTY_TAG),
            )
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            items(pictures, key = { it.ref.id }) { picture ->
                ClipRowView(
                    picture = picture,
                    row = ui.row(picture.ref.id),
                    busy = ui.rendering != null,
                    folder = model.renderer.dir.path,
                    onOrder = { model.pickOrder(picture.ref.id, it) },
                    onFormat = { model.toggleFormat(picture.ref.id, it) },
                    onRender = { model.render(picture) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipRowView(
    picture: ClipPicture,
    row: ClipRow,
    busy: Boolean,
    folder: String,
    onOrder: (ClipOrder) -> Unit,
    onFormat: (ClipFormat) -> Unit,
    onRender: () -> Unit,
) {
    val id = picture.ref.id
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("$ROW_TAG$id"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(picture.title, style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
            if (picture.size != null) {
                Spacer(Modifier.width(16.dp))
                Text(picture.size, style = GemType.label, color = GemColors.Bone)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (order in ClipOrder.entries) Chip(order.label, selected = row.order == order, onClick = { onOrder(order) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (format in ClipFormat.entries) FormatChip(format.label, checked = format in row.formats, onChange = { onFormat(format) })
        }
        QuietButton(
            "Render",
            onClick = onRender,
            enabled = !busy && row.formats.isNotEmpty(),
            modifier = Modifier.testTag("$RENDER_TAG$id"),
        )
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when (val status = row.status) {
                ClipStatus.Idle -> Unit
                is ClipStatus.Rendering -> StatusLine("Rendering… ${status.percent}%", id)
                is ClipStatus.Done -> {
                    if (status.saved > 0) {
                        StatusLine(if (status.saved == 1) "Saved 1 file" else "Saved ${status.saved} files", id)
                        SelectionContainer { Text(folder, style = GemType.mono.copy(fontSize = 14.sp), color = GemColors.Bone) }
                    }
                    if (status.error != null) {
                        Text(status.error, style = GemType.body, color = GemColors.Bone, modifier = Modifier.testTag("$ERROR_TAG$id"))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, id: String) {
    Text(text, style = GemType.body, color = GemColors.Bone, modifier = Modifier.testTag("$STATUS_TAG$id"))
}

/** [Chip]'s look as a checkbox: the formats are any of three, not one. */
@Composable
private fun FormatChip(text: String, checked: Boolean, onChange: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clip(shape)
            .background(LocalFelt.current.raised)
            .border(if (checked) BorderStroke(2.dp, GemColors.Lamp) else BorderStroke(0.dp, Color.Transparent), shape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onChange() })
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = GemType.button, color = GemColors.Bone) }
}

const val EMPTY_TAG = "clips-empty"
const val ROW_TAG = "clips-row-"
const val RENDER_TAG = "clips-render-"
const val STATUS_TAG = "clips-status-"
const val ERROR_TAG = "clips-error-"
