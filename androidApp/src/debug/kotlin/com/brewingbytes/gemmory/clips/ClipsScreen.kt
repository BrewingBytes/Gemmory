package com.brewingbytes.gemmory.clips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.brewingbytes.gemmory.ui.GemRenderer
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clips (T47; one panel and a queue since #152, mockup `docs/designs/mockups/clips-screen/`, B): a picture dropdown
 * (thumbnail, title, size; its menu searchable), order chips (one of three), format chips and language chips (any of
 * them; the app's language on, "All" for every one), and "Add to queue". The queue under the panel renders one entry
 * at a time: "✓ … · Saved 7 files", "▸ … · 45% (Deutsch)", "· … · waiting", and a failure's line under its entry.
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
        val picture = ui.picture
        if (picture == null) {
            Text(
                "No pictures on this phone yet",
                style = GemType.body,
                color = GemColors.Bone,
                modifier = Modifier.padding(16.dp).testTag(EMPTY_TAG),
            )
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Panel(ui, picture, pictures, model)
            Queue(ui.queue, model.renderer.dir.path)
            // TalkBack hears each entry as it finishes, not every percent.
            Box(Modifier.size(1.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = ui.announcement.orEmpty()
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panel(ui: ClipsUi, picture: ClipPicture, pictures: List<ClipPicture>, model: ClipsViewModel) {
    Label("Picture")
    PictureDropdown(picture, pictures, onPick = model::select)
    Label("Order")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (order in ClipOrder.entries) Chip(order.label(), selected = ui.order == order, onClick = { model.pickOrder(order) })
    }
    Label("Formats")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (format in ClipFormat.entries) CheckChip(format.label, checked = format in ui.formats, onChange = { model.toggleFormat(format) })
    }
    Label("Languages")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val all = ClipLanguages.ALL.all { it.tag in ui.languages }
        CheckChip("All", checked = all, onChange = model::toggleAll, modifier = Modifier.testTag(ALL_TAG))
        for (language in ClipLanguages.ALL) {
            CheckChip(
                language.name,
                checked = language.tag in ui.languages,
                onChange = { model.toggleLanguage(language.tag) },
                modifier = Modifier.testTag("$LANGUAGE_TAG${language.tag}"),
            )
        }
    }
    QuietButton(
        "Add to queue",
        onClick = model::addToQueue,
        enabled = ui.canAdd,
        modifier = Modifier.fillMaxWidth().testTag(ADD_TAG),
    )
}

/** The picked picture as a field; tapping it opens every picture, filtered by what's typed in its search line. */
@Composable
private fun PictureDropdown(picture: ClipPicture, pictures: List<ClipPicture>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var fieldWidth by remember { mutableStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    fun close() {
        keyboard?.hide()
        focus.clearFocus()
        query = ""
        open = false
    }
    val shape = RoundedCornerShape(6.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { fieldWidth = it.size.width }
                .heightIn(min = 64.dp)
                .clip(shape)
                .background(LocalFelt.current.deep)
                .border(BorderStroke(1.dp, GemColors.Bone.copy(alpha = 0.25f)), shape)
                .clickable(role = Role.DropdownList, onClickLabel = "Pick a picture") { open = true }
                .padding(horizontal = 12.dp)
                .testTag(PICTURE_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PictureLine(picture)
            Text("▾", style = GemType.body, color = GemColors.Bone, modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = ::close,
            modifier = Modifier
                .width(with(density) { fieldWidth.toDp() })
                .background(LocalFelt.current.deep)
                .heightIn(max = 480.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = GemType.body.copy(color = GemColors.Bone),
                cursorBrush = SolidColor(GemColors.Lamp),
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalFelt.current.ground)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Search pictures" }
                    .testTag(SEARCH_TAG),
                decorationBox = { field ->
                    if (query.isEmpty()) Text("Search pictures", style = GemType.body, color = GemColors.Bone.copy(alpha = 0.7f))
                    field()
                },
            )
            for (item in pictures.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }) {
                val selected = item.ref.id == picture.ref.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(if (selected) GemColors.Lamp.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            onPick(item.ref.id)
                            close()
                        }
                        .padding(horizontal = 12.dp)
                        .testTag("$ITEM_TAG${item.ref.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) { PictureLine(item) }
            }
        }
    }
}

/** Thumbnail, title and size, on one line. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.PictureLine(picture: ClipPicture) {
    Thumbnail(picture)
    Spacer(Modifier.width(12.dp))
    Text(picture.title, style = GemType.body, color = GemColors.Bone, modifier = Modifier.weight(1f))
    if (picture.size != null) {
        Spacer(Modifier.width(8.dp))
        Text(picture.size, style = GemType.label, color = GemColors.Bone)
    }
}

/** The finished picture as flat 2px cells, made off the main thread. */
@Composable
private fun Thumbnail(picture: ClipPicture) {
    val bitmap by produceState<ImageBitmap?>(null, picture.ref.id) {
        value = withContext(Dispatchers.IO) { picture.pattern()?.let { GemRenderer.render(it, cellPx = 2).asImageBitmap() } }
    }
    val image = bitmap
    if (image == null) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(LocalFelt.current.raised))
    } else {
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}

/** The queue, oldest first, and the folder its files go to once one is saved. */
@Composable
private fun Queue(queue: List<ClipJob>, folder: String) {
    if (queue.isEmpty()) return
    Label("Queue")
    Column {
        for (job in queue) {
            val languages = if (job.languages.size == ClipLanguages.ALL.size) "all" else job.languages.joinToString(" ")
            val line = when (val status = job.status) {
                ClipStatus.Waiting -> "· ${job.picture.title} · $languages · waiting"
                is ClipStatus.Rendering -> "▸ ${job.picture.title} · ${status.percent}% (${nameOf(status.language)})"
                is ClipStatus.Done -> "${if (status.error == null) "✓" else "✕"} ${job.picture.title} · $languages · " +
                    if (status.saved == 1) "Saved 1 file" else "Saved ${status.saved} files"
            }
            Text(
                line,
                style = GemType.body,
                color = GemColors.Bone,
                modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 12.dp).testTag("$QUEUE_TAG${job.key}"),
            )
            val error = (job.status as? ClipStatus.Done)?.error
            if (error != null) Text(error, style = GemType.body, color = GemColors.Bone, modifier = Modifier.testTag("$ERROR_TAG${job.key}"))
        }
    }
    if (queue.any { (it.status as? ClipStatus.Done)?.saved ?: 0 > 0 }) {
        SelectionContainer { Text(folder, style = GemType.mono.copy(fontSize = 14.sp), color = GemColors.Bone) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = GemType.label, color = GemColors.Bone, modifier = Modifier.padding(top = 8.dp))
}

/** [Chip]'s look as a checkbox: the formats and languages are any of them, not one. */
@Composable
private fun CheckChip(text: String, checked: Boolean, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clip(shape)
            .background(LocalFelt.current.raised)
            .border(if (checked) BorderStroke(2.dp, GemColors.Lamp) else BorderStroke(0.dp, Color.Transparent), shape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onChange() })
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = GemType.button, color = GemColors.Bone) }
}

private fun nameOf(tag: String): String = ClipLanguages.ALL.firstOrNull { it.tag == tag }?.name ?: tag

const val EMPTY_TAG = "clips-empty"
const val PICTURE_TAG = "clips-picture"
const val SEARCH_TAG = "clips-search"
const val ITEM_TAG = "clips-item-"
const val ALL_TAG = "clips-language-all"
const val LANGUAGE_TAG = "clips-language-"
const val ADD_TAG = "clips-add"
const val QUEUE_TAG = "clips-queue-"
const val ERROR_TAG = "clips-error-"

/** The order chips' names: a debug-only founder tool, English only (T48 leaves `src/debug` as is). */
private fun ClipOrder.label(): String = when (this) {
    ClipOrder.BY_COLOR -> "By color"
    ClipOrder.RIPPLE -> "Ripple"
    ClipOrder.RANDOM -> "Random"
}
