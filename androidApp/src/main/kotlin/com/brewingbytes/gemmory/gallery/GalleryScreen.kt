package com.brewingbytes.gemmory.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.feltGround

/** Which sheet the gallery shows; saved as a string so it survives rotation. */
private sealed interface GallerySheet {
    val ref: CanvasRef

    data class Rename(override val ref: CanvasRef, val title: String) : GallerySheet

    data class Delete(override val ref: CanvasRef) : GallerySheet

    data class StartOver(override val ref: CanvasRef) : GallerySheet

    data class Damaged(override val ref: CanvasRef) : GallerySheet

    companion object {
        val saver = androidx.compose.runtime.saveable.Saver<GallerySheet?, String>(
            save = { s ->
                when (s) {
                    null -> ""
                    is Rename -> "rename\u0000${s.ref.kind}\u0000${s.ref.id}\u0000${s.title}"
                    is Delete -> "delete\u0000${s.ref.kind}\u0000${s.ref.id}"
                    is StartOver -> "startover\u0000${s.ref.kind}\u0000${s.ref.id}"
                    is Damaged -> "damaged\u0000${s.ref.kind}\u0000${s.ref.id}"
                }
            },
            restore = { saved ->
                val p = saved.split('\u0000')
                runCatching {
                    val ref = CanvasRef(CanvasKind.valueOf(p[1]), p[2])
                    when (p[0]) {
                        "rename" -> Rename(ref, p[3])
                        "delete" -> Delete(ref)
                        "startover" -> StartOver(ref)
                        "damaged" -> Damaged(ref)
                        else -> null
                    }
                }.getOrNull()
            },
        )
    }
}

/**
 * Gallery (design system, Layout and Gallery tile): tabs Pictures / My Photos, a 2-column staggered grid of tiles
 * showing real progress, and the "New from photo" button floating bottom-right on both tabs.
 */
@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    decodeFailed: Boolean,
    onNewFromPhoto: () -> Unit,
    onOpen: (Tile) -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val tab = vm.tab
    var sheet by rememberSaveable(stateSaver = GallerySheet.saver) { mutableStateOf<GallerySheet?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Gemmory", onBack = null) { IconAction(GemIcons.Settings, "Settings", onSettings) }
            Tabs(tab, onTab = vm::selectTab)
            if (decodeFailed) {
                Text(
                    "This photo couldn't be opened. Try another one.",
                    style = GemType.body,
                    color = GemColors.Bone,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val tiles = if (tab == 0) state.pictures else state.photos
            if (tab == 1 && state.loaded && tiles.isEmpty()) {
                MyPhotosEmpty(onNewFromPhoto)
            } else {
                TileGrid(
                    tiles = tiles,
                    onOpen = { tile -> if (tile.damaged) sheet = GallerySheet.Damaged(tile.ref) else onOpen(tile) },
                    onRename = { sheet = GallerySheet.Rename(it.ref, it.title) },
                    onDelete = { sheet = GallerySheet.Delete(it.ref) },
                    onStartOver = { sheet = GallerySheet.StartOver(it.ref) },
                )
            }
        }
        if (!(tab == 1 && state.loaded && state.photos.isEmpty())) {
            PrimaryButton(
                "New from photo",
                onNewFromPhoto,
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    when (val s = sheet) {
        null -> Unit
        is GallerySheet.Rename -> RenameSheet(s.title, onSave = { vm.rename(s.ref, it); sheet = null }, onDismiss = { sheet = null })
        is GallerySheet.Delete -> ConfirmSheet(
            title = "Delete this photo canvas?",
            body = "The photo, grid and progress are removed from this phone.",
            confirm = "Delete",
            onConfirm = { vm.delete(s.ref); sheet = null },
            onDismiss = { sheet = null },
        )
        is GallerySheet.StartOver -> ConfirmSheet(
            title = "Start this picture over?",
            body = "Your progress on it is cleared.",
            confirm = "Start over",
            onConfirm = { vm.startOver(s.ref); sheet = null },
            onDismiss = { sheet = null },
        )
        is GallerySheet.Damaged -> {
            val photo = s.ref.kind == CanvasKind.PHOTO
            ConfirmSheet(
                title = "Can't open",
                body = "This canvas's file is damaged, so it can't be opened. You can delete it, or start the picture over.",
                confirm = if (photo) "Delete" else "Start over",
                onConfirm = {
                    if (photo) vm.delete(s.ref) else vm.startOver(s.ref)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
    }
}

/** Selected tab on felt-deep; both are 48dp targets. */
@Composable
private fun Tabs(selected: Int, onTab: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Pictures", "My Photos").forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (isSelected) Modifier.background(LocalFelt.current.deep) else Modifier)
                    .semantics {
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .clickable { onTab(index) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = GemType.button, color = if (isSelected) GemColors.Bone else GemColors.TextMuted)
            }
        }
    }
}

@Composable
private fun TileGrid(
    tiles: List<Tile>,
    onOpen: (Tile) -> Unit,
    onRename: (Tile) -> Unit,
    onDelete: (Tile) -> Unit,
    onStartOver: (Tile) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().testTag("gallery-grid"),
        // 88dp at the bottom so the floating button never covers a tile's % (design D10).
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
    ) {
        items(tiles, key = { "${it.ref.kind}/${it.ref.id}" }) { tile ->
            TileView(tile, onOpen, onRename, onDelete, onStartOver)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    onOpen: (Tile) -> Unit,
    onRename: (Tile) -> Unit,
    onDelete: (Tile) -> Unit,
    onStartOver: (Tile) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val description = when {
        tile.damaged -> "${tile.title}, can't open"
        tile.done -> "${tile.title}, done"
        else -> "${tile.title}, ${tile.percent}% done"
    }
    Column(
        Modifier
            .testTag("tile-${tile.ref.kind.name.lowercase()}-${tile.ref.id}")
            .semantics(mergeDescendants = true) { contentDescription = description }
            .combinedClickable(onClick = { onOpen(tile) }, onLongClick = { menu = true }),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(tile.aspect)
                .shadow(2.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(LocalFelt.current.deep),
        ) {
            tile.image?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (tile.damaged) {
                Text(
                    "Can't open",
                    style = GemType.label,
                    color = GemColors.Bone,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LocalFelt.current.deep)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(tile.title, style = GemType.label, color = GemColors.Bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!tile.damaged) {
                    Text(tile.progressLabel, style = GemType.mono.copy(fontSize = 12.sp), color = GemColors.TextMuted)
                }
            }
            Box {
                IconAction(GemIcons.More, "More for ${tile.title}", onClick = { menu = true })
                TileMenu(
                    expanded = menu,
                    photo = tile.ref.kind == CanvasKind.PHOTO,
                    onDismiss = { menu = false },
                    onRename = { menu = false; onRename(tile) },
                    onDelete = { menu = false; onDelete(tile) },
                    onStartOver = { menu = false; onStartOver(tile) },
                )
            }
        }
    }
}

/** Tile ⋮ menu (design D3): photo canvases Rename / Delete, bundled pictures Start over. */
@Composable
private fun TileMenu(
    expanded: Boolean,
    photo: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onStartOver: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = LocalFelt.current.deep,
        shape = RoundedCornerShape(6.dp),
    ) {
        val colors = MenuDefaults.itemColors(textColor = GemColors.Bone)
        if (photo) {
            DropdownMenuItem(text = { Text("Rename", style = GemType.body) }, onClick = onRename, colors = colors)
            DropdownMenuItem(text = { Text("Delete", style = GemType.body) }, onClick = onDelete, colors = colors)
        } else {
            DropdownMenuItem(text = { Text("Start over", style = GemType.body) }, onClick = onStartOver, colors = colors)
        }
    }
}

/** My Photos empty (design D10): the floating button is hidden here in favor of the inline one. */
@Composable
private fun MyPhotosEmpty(onNewFromPhoto: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
        Text("Your photos, in gems", style = GemType.display, color = GemColors.Bone)
        Spacer(Modifier.height(12.dp))
        Text(
            "Turn a photo of your own into a canvas you can finish. It never leaves this phone.",
            style = GemType.body,
            color = GemColors.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("New from photo", onNewFromPhoto)
    }
}

/** Rename edits the title (design D26). A blank name keeps the old title. */
@Composable
private fun RenameSheet(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    GemSheet("Rename", onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = com.brewingbytes.gemmory.photo.PhotoTitles.capped(it) },
            singleLine = true,
            textStyle = GemType.body,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GemColors.Bone,
                unfocusedTextColor = GemColors.Bone,
                focusedBorderColor = GemColors.Lamp,
                unfocusedBorderColor = GemColors.TextMuted,
                cursorColor = GemColors.Bone,
            ),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", { onSave(text) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { GemTextButton("Keep", onDismiss) }
    }
}
