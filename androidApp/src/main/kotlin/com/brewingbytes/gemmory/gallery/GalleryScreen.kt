package com.brewingbytes.gemmory.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.ads.isLocked
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.photo.allowanceLine
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.DailyStreak
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.WheelPrize
import com.brewingbytes.gemmory.share.ProgressShareHost
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.NOTE_MAX_WIDTH
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.galleryColumns
import com.brewingbytes.gemmory.ui.noteFitsBeside

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

/** The gallery's "VIP" action, also where TalkBack focus returns when the paywall closes. */
const val VIP_ACTION_TAG = "gallery-vip"
const val STREAK_TAG = "gallery-streak"
const val TODAY_TAG = "gallery-today"
private const val SHARE_PROGRESS = "Share progress"

/** The tile's test tag, also where TalkBack focus returns when its sheet closes. */
fun tileTag(ref: CanvasRef): String = "tile-${ref.kind.name.lowercase()}-${ref.id}"

/**
 * Whether [this] tile is a locked picture the player hasn't unlocked (design D14). [todayFree], today's free picture
 * (#34), opens without a video today.
 */
internal fun Tile.locked(unlocked: Set<String>, entitlements: Entitlements, todayFree: String? = null): Boolean =
    ref.kind == CanvasKind.BUNDLED && ref.id != todayFree &&
        BundledPictures.byId(ref.id)?.let { isLocked(it, unlocked, entitlements, started) } == true

/**
 * Gallery (design system, Layout and Gallery tile): tabs Pictures / My Photos, a 2-column staggered grid of tiles
 * showing real progress, and the "New from photo" button floating bottom-right on both tabs.
 */
@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    decodeFailed: Boolean,
    allowance: Allowance,
    unlocked: Set<String>,
    entitlements: Entitlements,
    onNewFromPhoto: () -> Unit,
    onOpen: (Tile) -> Unit,
    onLocked: (Tile) -> Unit,
    onSettings: () -> Unit,
    showVip: Boolean = false,
    onVip: () -> Unit = {},
    notice: String? = null,
    /** What the player holds (#38); null for VIP, who has everything unlimited and no inventory. */
    inventory: InventoryState? = null,
    onWatchFills: () -> Unit = {},
    onSpin: suspend () -> WheelPrize? = { null },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val tab = vm.tab
    var sheet by rememberSaveable(stateSaver = GallerySheet.saver) { mutableStateOf<GallerySheet?>(null) }
    // The Inventory sheet (#38) and its gift wheel: 0 none, 1 inventory, 2 wheel.
    var inventorySheet by rememberSaveable { mutableIntStateOf(0) }
    // The floating button (and the allowance chip) never cover a tile's %: the grid scrolls clear of them (D10).
    // The measured height excludes the overlay's 16dp padding; another 16dp keeps a gap (56dp button → 88dp).
    var overlayHeight by remember { mutableIntStateOf(0) }
    val gridBottom = with(LocalDensity.current) { overlayHeight.toDp() } + 32.dp

    Box(
        Modifier
            .fillMaxSize()
            .feltGround(LocalFelt.current.ground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Gemmory", onBack = null) {
                // Quiet, from the 2nd session on (design D3): never on the first launch.
                if (inventory != null) InventoryButton(inventory.spinsWaiting > 0) { inventorySheet = 1 }
                if (showVip) GemTextButton("VIP", onVip, Modifier.testTag(VIP_ACTION_TAG))
                IconAction(GemIcons.Settings, "Settings", onSettings)
            }
            Tabs(tab, onTab = vm::selectTab, streak = state.streak)
            if (notice != null) {
                // Billing's one-time line (design D6, D7), read out when it appears.
                Text(
                    notice,
                    style = GemType.body,
                    color = GemColors.Bone,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("gallery-notice")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
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
                // Today's free picture (#34) opens without a video; the tap re-checks it, so a gallery left open past
                // midnight shows yesterday's card until the next visit but no longer grants it.
                val todayFree = state.today?.takeIf { !it.vip }?.tile?.ref?.id
                val open: (Tile) -> Unit = { tile ->
                    when {
                        tile.damaged -> sheet = GallerySheet.Damaged(tile.ref)
                        tile.locked(unlocked, entitlements, tile.ref.id.takeIf { vm.isFreeToday(tile) }) -> onLocked(tile)
                        else -> onOpen(tile)
                    }
                }
                TileGrid(
                    tiles = tiles,
                    bottomPadding = gridBottom,
                    header = state.today?.takeIf { tab == 0 }?.let { card -> { TodayCardView(card) { open(card.tile) } } },
                    isLocked = { it.locked(unlocked, entitlements, todayFree) },
                    onOpen = open,
                    onShareProgress = { vm.shareProgress(it.ref) },
                    onRename = { sheet = GallerySheet.Rename(it.ref, it.title) },
                    onDelete = { sheet = GallerySheet.Delete(it.ref) },
                    onStartOver = { sheet = GallerySheet.StartOver(it.ref) },
                )
            }
        }
        if (!(tab == 1 && state.loaded && state.photos.isEmpty())) {
            // Allowance note (design D5): once today's free photo canvas is used, the Preview line's copy.
            val note = allowanceLine(allowance, inventory?.canvases ?: 0).takeIf { allowance != Allowance.Free }
            NoteAndButton(
                note = note?.let { { AllowanceChip(it) } },
                button = { PrimaryButton("New from photo", onNewFromPhoto) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onSizeChanged { overlayHeight = it.height },
            )
        }
    }

    ProgressShareHost(vm.sharing, returnFocusTo = vm.sharing.request?.ref?.let(::tileTag))
    // VIP starting while a sheet is up closes it: VIP has no inventory.
    if (inventory != null) {
        when (inventorySheet) {
        1 -> InventorySheet(
            state = inventory,
            streak = state.streak,
            onWatchFills = {
                inventorySheet = 0
                onWatchFills()
            },
            onSpin = { inventorySheet = 2 },
            onDismiss = { inventorySheet = 0 },
        )
        2 -> WheelSheet(inventory.spinsWaiting, onSpin, onDismiss = { inventorySheet = 0 })
        }
    }
    when (val s = sheet) {
        null -> Unit
        is GallerySheet.Rename -> RenameSheet(
            s.title,
            onSave = { vm.rename(s.ref, it); sheet = null },
            onDismiss = { sheet = null },
            returnFocusTo = tileTag(s.ref),
        )
        is GallerySheet.Delete -> ConfirmSheet(
            title = "Delete this photo canvas?",
            body = "The photo, grid and progress are removed from this phone.",
            confirm = "Delete",
            onConfirm = { vm.delete(s.ref); sheet = null },
            onDismiss = { sheet = null },
            returnFocusTo = tileTag(s.ref),
        )
        is GallerySheet.StartOver -> ConfirmSheet(
            title = "Start this picture over?",
            body = "Your progress on it is cleared.",
            confirm = "Start over",
            onConfirm = { vm.startOver(s.ref); sheet = null },
            onDismiss = { sheet = null },
            returnFocusTo = tileTag(s.ref),
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
                returnFocusTo = tileTag(s.ref),
            )
        }
    }
}

/**
 * The allowance note on a felt-deep chip, so tile art scrolling underneath never meets its text (founder call #14;
 * the hint chip and locked label use the same ground).
 */
@Composable
private fun AllowanceChip(note: String) {
    Text(
        note,
        style = GemType.label,
        color = GemColors.Bone,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.deep)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("gallery-allowance-note"),
    )
}

/**
 * The floating button with the allowance chip beside it, right-aligned (design D5). The chip takes what the button
 * leaves, up to [NOTE_MAX_WIDTH]; when that's under [NOTE_MIN_WIDTH] (200% font on a phone) it goes on its own line
 * above the button.
 */
@Composable
private fun NoteAndButton(note: (@Composable () -> Unit)?, button: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOfNotNull(button, note), modifier = modifier) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = measurables[0].first().measure(loose)
        val chip = measurables.getOrNull(1)?.firstOrNull()
        val width = constraints.maxWidth
        if (chip == null) {
            return@Layout layout(width, buttonPlaceable.height) { buttonPlaceable.place(width - buttonPlaceable.width, 0) }
        }
        val beside = noteFitsBeside(width.toDp().value, buttonPlaceable.width.toDp().value, gap.toDp().value)
        val chipMax = if (beside) {
            minOf(NOTE_MAX_WIDTH.roundToPx(), width - buttonPlaceable.width - gap)
        } else {
            minOf(NOTE_MAX_WIDTH.roundToPx().coerceAtLeast(width * 3 / 4), width)
        }
        val chipPlaceable = chip.measure(loose.copy(maxWidth = chipMax))
        if (beside) {
            val height = maxOf(chipPlaceable.height, buttonPlaceable.height)
            layout(width, height) {
                buttonPlaceable.place(width - buttonPlaceable.width, (height - buttonPlaceable.height) / 2)
                chipPlaceable.place(width - buttonPlaceable.width - gap - chipPlaceable.width, (height - chipPlaceable.height) / 2)
            }
        } else {
            layout(width, chipPlaceable.height + gap + buttonPlaceable.height) {
                chipPlaceable.place(width - chipPlaceable.width, 0)
                buttonPlaceable.place(width - buttonPlaceable.width, chipPlaceable.height + gap)
            }
        }
    }
}

/**
 * Selected tab on felt-deep; both are 48dp targets. The daily play streak (#33) sits at the end of the row from
 * [DailyStreak.SHOWN_FROM] days, and wraps under the tabs when they leave no room (200% font).
 */
@Composable
private fun Tabs(selected: Int, onTab: (Int) -> Unit, streak: Int) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
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
                // Bone on both: text-muted fails 4.5:1 on the grained felt (valid grounds, #14).
                Text(label, style = GemType.button, color = GemColors.Bone)
            }
        }
        if (streak >= DailyStreak.SHOWN_FROM) {
            Spacer(Modifier.weight(1f))
            StreakChip(streak)
        }
    }
}

/**
 * Today's picture (#34): a felt-deep card above the Pictures grid with the finished picture (its real progress once
 * started), "Today's picture" ("Today's pick" for VIP), the title, the size and "free today", and a quiet Start
 * (Continue once started; none when done). The whole card opens it; TalkBack reads it as one node.
 */
@Composable
private fun TodayCardView(card: TodayCard, onOpen: () -> Unit) {
    val tile = card.tile
    val action = when {
        tile.done -> "Open"
        tile.started -> "Continue"
        else -> "Start"
    }
    val description = A11yText.today(tile.title, card.size, card.vip, card.free, tile.started, tile.percent, tile.done)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag(TODAY_TAG)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalFelt.current.deep)
            .clickable(onClickLabel = action, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = description
                onClick(label = action) { onOpen(); true }
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(TODAY_THUMB)
                .aspectRatio(tile.aspect)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            card.preview?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // text-muted and bone on felt-deep (6.30 and 7.58:1); lamp stays "the selected one" only.
            Text(if (card.vip) "Today's pick" else "Today's picture", style = GemType.label, color = GemColors.TextMuted)
            Text(tile.title, style = GemType.button, color = GemColors.Bone, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(todayLine(card), style = GemType.label, color = GemColors.TextMuted)
            if (!tile.done) {
                Spacer(Modifier.height(6.dp))
                QuietButton(action, onOpen)
            }
        }
    }
}

/** "Medium · free today" / "Medium · 12% done" / "Medium · done" / "Medium"; the percent in mono, like every figure. */
private fun todayLine(card: TodayCard) = buildAnnotatedString {
    append(card.size)
    val tile = card.tile
    when {
        tile.done -> append(" · done")
        tile.started -> {
            append(" · ")
            withStyle(GemType.mono.copy(fontSize = 13.sp).toSpanStyle()) { append("${tile.percent}%") }
            append(" done")
        }
        card.free -> append(" · free today")
    }
}

private val TODAY_THUMB = 72.dp

/** "3 days in a row" (#33): the count in mono, like every number; one TalkBack node. Calm: no flame, no warning. */
@Composable
private fun StreakChip(days: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.deep)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(STREAK_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$days", style = GemType.mono.copy(fontSize = 13.sp), color = GemColors.Bone)
        Text(" days in a row", style = GemType.label, color = GemColors.Bone)
    }
}

@Composable
private fun TileGrid(
    tiles: List<Tile>,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)?,
    isLocked: (Tile) -> Boolean,
    onOpen: (Tile) -> Unit,
    onShareProgress: (Tile) -> Unit,
    onRename: (Tile) -> Unit,
    onDelete: (Tile) -> Unit,
    onStartOver: (Tile) -> Unit,
) {
    // Large screens (design D22): 2 columns under 600dp, 3 from 600dp, 4 from 840dp.
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(galleryColumns(widthDp)),
        modifier = Modifier.fillMaxSize().testTag("gallery-grid"),
        // Room at the bottom so the floating button never covers a tile's % (design D10).
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
    ) {
        // Today's picture (#34) spans the grid's width and scrolls with it, so it never holds space at 200% font.
        if (header != null) item(key = TODAY_TAG, span = StaggeredGridItemSpan.FullLine) { header() }
        items(tiles, key = { "${it.ref.kind}/${it.ref.id}" }) { tile ->
            TileView(tile, isLocked(tile), onOpen, onShareProgress, onRename, onDelete, onStartOver)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    locked: Boolean,
    onOpen: (Tile) -> Unit,
    onShareProgress: (Tile) -> Unit,
    onRename: (Tile) -> Unit,
    onDelete: (Tile) -> Unit,
    onStartOver: (Tile) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val photo = tile.ref.kind == CanvasKind.PHOTO
    val description = A11yText.tile(tile.title, tile.percent, tile.done, locked, tile.damaged)
    // TalkBack (design D19): one node per tile that reads exactly its state, with the ⋮ menu's items as its custom
    // actions (the ⋮ button itself is folded into them).
    val share = !locked && tile.shareable
    val actions = when {
        locked -> emptyList()
        photo -> listOf(
            CustomAccessibilityAction("Rename") { onRename(tile); true },
            CustomAccessibilityAction("Delete") { onDelete(tile); true },
        )
        else -> listOf(CustomAccessibilityAction("Start over") { onStartOver(tile); true })
    }.let { if (share) listOf(CustomAccessibilityAction(SHARE_PROGRESS) { onShareProgress(tile); true }) + it else it }
    Column(
        Modifier
            .testTag(tileTag(tile.ref))
            .combinedClickable(
                onClick = { onOpen(tile) },
                onLongClickLabel = if (locked) null else "More options",
                onLongClick = if (locked) null else ({ menu = true }),
            )
            // After the clickable: semantics set further in replace what follows, and the click must stay.
            .clearAndSetSemantics {
                contentDescription = description
                customActions = actions
            },
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
            if (locked) {
                // Locked art stays fully visible: a small label, never a padlock or a dim (design D14).
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LocalFelt.current.deep)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("locked-label"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(GemIcons.Play, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watch to unlock", style = GemType.label, color = GemColors.Bone)
                }
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
                    Text(tile.progressLabel, style = GemType.mono.copy(fontSize = 12.sp), color = GemColors.Bone)
                }
            }
            // A locked picture has nothing to start over.
            if (!locked) Box {
                IconAction(GemIcons.More, "More for ${tile.title}", onClick = { menu = true })
                TileMenu(
                    expanded = menu,
                    photo = photo,
                    share = share,
                    onDismiss = { menu = false },
                    onShareProgress = { menu = false; onShareProgress(tile) },
                    onRename = { menu = false; onRename(tile) },
                    onDelete = { menu = false; onDelete(tile) },
                    onStartOver = { menu = false; onStartOver(tile) },
                )
            }
        }
    }
}

/**
 * Tile ⋮ menu (design D3): photo canvases Rename / Delete, bundled pictures Start over; first Share progress (#36)
 * when [share] (unfinished, past the minimum).
 */
@Composable
private fun TileMenu(
    expanded: Boolean,
    photo: Boolean,
    share: Boolean,
    onDismiss: () -> Unit,
    onShareProgress: () -> Unit,
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
        if (share) {
            DropdownMenuItem(
                text = { Text(SHARE_PROGRESS, style = GemType.body) },
                onClick = onShareProgress,
                colors = colors,
                modifier = Modifier.testTag("tile-share-progress"),
            )
        }
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
            color = GemColors.Bone,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("New from photo", onNewFromPhoto)
    }
}

/** Rename edits the title (design D26). A blank name keeps the old title. */
@Composable
private fun RenameSheet(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit, returnFocusTo: String) {
    var text by rememberSaveable { mutableStateOf(current) }
    GemSheet("Rename", onDismiss, returnFocusTo) {
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
