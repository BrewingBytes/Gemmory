package com.brewingbytes.gemmory.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.brewingbytes.gemmory.ads.isLocked
import com.brewingbytes.gemmory.game.Frost
import com.brewingbytes.gemmory.photo.allowanceLine
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Inventory
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.NudgeTarget
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.StreakNudge
import com.brewingbytes.gemmory.share.ProgressShareHost
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.streak.Flame
import com.brewingbytes.gemmory.streak.StreakText
import com.brewingbytes.gemmory.telemetry.CanvasEntry
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.ConfirmSheet
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.withFullMotion
import com.brewingbytes.gemmory.game.MusicRules
import com.brewingbytes.gemmory.settings.MusicCopy
import com.brewingbytes.gemmory.ui.GemToast
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.NOTE_MAX_WIDTH
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.galleryColumns
import com.brewingbytes.gemmory.ui.noteFitsBeside
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import kotlinx.coroutines.delay
import android.view.accessibility.AccessibilityManager
import com.brewingbytes.gemmory.policy.ShareLanding
import com.brewingbytes.gemmory.share.LandingUi
import com.brewingbytes.gemmory.share.SharedLandingCopy
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

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

/** The streak nudge (#102), in the Today card or on its own card. */
const val STREAK_NUDGE_TAG = "gallery-streak-nudge"

/** The Tomorrow line (T36), in the Today card or on its own. */
const val TOMORROW_TAG = "gallery-tomorrow"

/** The "New today" chip on the Today card (T36). */
const val NEW_TODAY_TAG = "gallery-new-today"

/** The Finished section's heading (#89), at the end of each tab. */
const val FINISHED_TAG = "gallery-finished"

/** The "Opens tomorrow" toast (T36). */
const val TOAST_TAG = "gallery-toast"
private const val SHARE_PROGRESS = "Share progress"

/** The tile's test tag, also where TalkBack focus returns when its sheet closes. */
fun tileTag(ref: CanvasRef): String = "tile-${ref.kind.name.lowercase()}-${ref.id}"

/** The one-time "Music is playing · Turn off" chip (T42) and its "Music off" line. */
const val MUSIC_CHIP_TAG = "gallery-music-chip"
const val MUSIC_OFF_TAG = "gallery-music-off"

/** Connected tests only (T42): TalkBack's touch exploration as the music chip reads it. */
@VisibleForTesting
internal var touchExplorationForTest: Boolean? = null

/** The "New" label on a remote picture's tile (#43). */
const val NEW_LABEL_TAG = "new-label"
const val MYSTERY_LABEL_TAG = "mystery-label"

/**
 * Whether [this] tile is a locked picture the player hasn't unlocked (design D14). [todayFree], today's free picture
 * (#34) and today's drop (T36), open without a video today.
 */
internal fun Tile.locked(unlocked: Set<String>, entitlements: Entitlements, todayFree: Set<String> = emptySet()): Boolean =
    ref.kind != CanvasKind.PHOTO && ref.id !in todayFree && isLocked(lockable, ref.id, unlocked, entitlements, started)

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
    /** A tile or the Today card (T36: [CanvasEntry.TODAY_CARD]) opens a picture. */
    onOpen: (Tile, CanvasEntry) -> Unit,
    onLocked: (Tile) -> Unit,
    onSettings: () -> Unit,
    showVip: Boolean = false,
    onVip: () -> Unit = {},
    notice: String? = null,
    /** What the player holds (#38); null for VIP, who has everything unlimited and no inventory. */
    inventory: InventoryState? = null,
    onWatchFills: () -> Unit = {},
    onSpin: suspend () -> Spin? = { null },
    /** The first-music chip (T42, D24) is up; it goes on a tap, a tile tap or after 8s (no timeout under TalkBack). */
    musicChip: Boolean = false,
    onMusicChipGone: () -> Unit = {},
    onMusicOff: () -> Unit = {},
    /** The streak chip (#101) and the nudge's broken variant (#102) open the streak screen, from `chip` or `nudge`. */
    onStreak: (source: String) -> Unit = {},
    /** A share link's picture that arrived after the gallery drew (T50, DR2): "Shared with you: <title> · Open". */
    sharedOffer: LandingUi.Offer? = null,
    onSharedOpen: (LandingUi.Offer) -> Unit = {},
    /** The offer's 8 s ran out (it has no timeout under TalkBack, D7). */
    onSharedOfferGone: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    // Drops open at local midnight (T36): a gallery left showing reloads then, and one that comes back on a new day
    // (the phone locked overnight) reloads as it resumes.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.resumed()
            while (true) {
                delay(vm.millisUntilNextDay())
                vm.refresh()
            }
        }
    }
    // "Opens tomorrow" (T36): a new tap restarts its 4s.
    var toast by remember { mutableIntStateOf(0) }
    // "Music off. Turn it back on in Settings." after the chip's Turn off (T42).
    var musicOffLine by remember { mutableStateOf(false) }
    val chipGone by rememberUpdatedState(onMusicChipGone)
    val context = LocalContext.current
    LaunchedEffect(musicChip) {
        if (!musicChip) return@LaunchedEffect
        val touchExploration = touchExplorationForTest
            ?: context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled ?: false
        val timeout = MusicRules.chipTimeoutMs(touchExploration) ?: return@LaunchedEffect
        delay(timeout)
        chipGone()
    }
    val offerGone by rememberUpdatedState(onSharedOfferGone)
    LaunchedEffect(sharedOffer) {
        if (sharedOffer == null) return@LaunchedEffect
        val touchExploration = touchExplorationForTest
            ?: context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled ?: false
        val timeout = ShareLanding.offerTimeoutMs(touchExploration) ?: return@LaunchedEffect
        delay(timeout)
        offerGone()
    }
    val tab = vm.tab
    var sheet by rememberSaveable(stateSaver = GallerySheet.saver) { mutableStateOf<GallerySheet?>(null) }
    // The Inventory sheet (#38) and its gift wheel: 0 none, 1 inventory, 2 wheel.
    var inventorySheet by rememberSaveable { mutableIntStateOf(0) }
    // The All collections sheet (T37).
    var collectionsSheet by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
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
            Tabs(tab, onTab = vm::selectTab, streak = state.streak, streakDone = state.streakDone, repairable = state.repairable, ignite = vm.streakIgnite, onIgnited = vm::streakIgnited) { onStreak("chip") }
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
            val finished = if (tab == 0) state.finishedPictures else state.finishedPhotos
            if (tab == 1 && state.loaded && tiles.isEmpty() && finished.isEmpty()) {
                MyPhotosEmpty(onNewFromPhoto)
            } else {
                // Today's free picture (#34) and today's drop (T36) open without a video; the tap re-checks them, so a
                // gallery left open past midnight no longer grants them.
                val todayFree = state.todayFree
                val open: (Tile, CanvasEntry) -> Unit = { tile, entry ->
                    if (musicChip) onMusicChipGone()
                    vm.opened(tile)
                    when {
                        tile.damaged -> sheet = GallerySheet.Damaged(tile.ref)
                        tile.locked(unlocked, entitlements, setOfNotNull(tile.ref.id.takeIf { vm.isFreeToday(tile) })) -> onLocked(tile)
                        else -> onOpen(tile, entry)
                    }
                }
                val tomorrowTapped: () -> Unit = {
                    // A music chip still showing goes first (T36, T42).
                    if (musicChip) onMusicChipGone()
                    vm.teaserTapped()
                    toast++
                }
                val today = state.today
                val tomorrow = state.tomorrow
                // The keep-it-alive nudge (#102): its canvas, or the streak screen for the broken variant or no target.
                val nudge = state.nudge?.takeIf { tab == 0 }
                val nudgeTapped: () -> Unit = {
                    nudge?.let { n ->
                        if (musicChip) onMusicChipGone()
                        vm.nudgeTapped(n)
                        val tile = n.tile
                        if (n.broken == null && tile != null) open(tile, CanvasEntry.STREAK_NUDGE) else onStreak("nudge")
                    }
                }
                if (nudge != null) LaunchedEffect(nudge.broken != null) { vm.nudgeShown(nudge) }
                val header: (@Composable () -> Unit)? = when {
                    tab != 0 -> null
                    today != null -> { { TodayCardView(today, nudge, nudgeTapped, tomorrow, tomorrowTapped) { open(today.tile, CanvasEntry.TODAY_CARD) } } }
                    nudge != null || tomorrow != null -> { { NudgeCard(nudge, nudgeTapped, tomorrow, tomorrowTapped) } }
                    else -> null
                }
                // Collection and event rows (T37/T38): two at most, one above 1.5× font (D4), then the All collections link (D3).
                val largeFont = LocalDensity.current.fontScale > COLLECTION_LARGE_FONT
                val ui = state.collections
                val rows = if (tab != 0) emptyList() else if (largeFont) ui.rowsLarge else ui.rows
                val showAll = tab == 0 && (if (largeFont) ui.showAllLarge else ui.showAll)
                // A glint plays only with nothing over the gallery (D7).
                val calm = sheet == null && inventorySheet == 0 && !collectionsSheet
                val isLocked: (Tile) -> Boolean = { it.locked(unlocked, entitlements, todayFree) }
                val extras = rows.map { row ->
                    collectionTag(row.id) to @Composable {
                        CollectionRowView(row, isLocked, { open(it, row.entry) }, vm::thumbnail, glint = row.glint && calm, onGlintDone = vm::glintShown)
                    }
                } + listOfNotNull((ALL_COLLECTIONS_TAG to @Composable { AllCollectionsLink { collectionsSheet = true } }).takeIf { showAll })
                // Rows arriving above the grid (the first finish, D5; a new collection): the grid keeps its first tile in
                // place by key, which would leave them scrolled off the top. A grid sitting at its top shows them.
                val above = (if (header != null) 1 else 0) + extras.size
                LaunchedEffect(above) {
                    if (gridState.firstVisibleItemIndex == above && gridState.firstVisibleItemScrollOffset == 0) gridState.scrollToItem(0)
                }
                // The reward return (D7): its row comes into view, then its glint plays.
                val glintRow = rows.indexOfFirst { it.glint }
                LaunchedEffect(glintRow, header != null) {
                    if (glintRow < 0) return@LaunchedEffect
                    val index = glintRow + if (header != null) 1 else 0
                    if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) gridState.animateScrollToItem(index)
                }
                // Collapsed by default, remembered per tab (#89).
                var finishedOpen by remember(tab) { mutableStateOf(vm.finishedOpen(tab)) }
                TileGrid(
                    tiles = tiles,
                    finished = finished,
                    finishedOpen = finishedOpen,
                    onFinished = {
                        finishedOpen = !finishedOpen
                        vm.setFinishedOpen(tab, finishedOpen)
                    },
                    photos = tab == 1,
                    bottomPadding = gridBottom,
                    header = header,
                    extras = extras,
                    gridState = gridState,
                    isLocked = isLocked,
                    onOpen = { open(it, CanvasEntry.GALLERY) },
                    thumbnail = vm::thumbnail,
                    onShareProgress = { vm.shareProgress(it.ref) },
                    onRename = { sheet = GallerySheet.Rename(it.ref, it.title) },
                    onDelete = { sheet = GallerySheet.Delete(it.ref) },
                    onStartOver = { sheet = GallerySheet.StartOver(it.ref) },
                )
            }
        }
        if (!(tab == 1 && state.loaded && state.photos.isEmpty() && state.finishedPhotos.isEmpty())) {
            // Allowance note (design D5): once today's free photo canvas is used, the Preview line's copy.
            val note = allowanceLine(allowance, inventory?.canvases ?: 0).takeIf { allowance != Allowance.Free }
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onSizeChanged { overlayHeight = it.height },
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The first-music chip (T42, D24) sits above New from photo, right-aligned with it.
                if (musicChip) {
                    MusicChip(onTurnOff = {
                        onMusicOff()
                        onMusicChipGone()
                        musicOffLine = true
                    })
                }
                if (musicOffLine) {
                    GemToast(MusicCopy.OFF_LINE, key = Unit, onGone = { musicOffLine = false }, modifier = Modifier.testTag(MUSIC_OFF_TAG))
                }
                NoteAndButton(
                    note = note?.let { { AllowanceChip(it) } },
                    button = { PrimaryButton("New from photo", onNewFromPhoto) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (toast > 0) {
            // Centred 16dp above New from photo (plan-design-review D8); the overlay's own 16dp padding is below it.
            GemToast(
                "Opens tomorrow",
                key = toast,
                onGone = { toast = 0 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = with(LocalDensity.current) { overlayHeight.toDp() } + 32.dp)
                    .testTag(TOAST_TAG),
            )
        }
        if (sharedOffer != null) {
            // In the "Opens tomorrow" spot (plan-design-review D8), centred 16dp above New from photo (T50, DR2).
            SharedOfferChip(
                SharedLandingCopy.shared(sharedOffer.title),
                onOpen = { onSharedOpen(sharedOffer) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = with(LocalDensity.current) { overlayHeight.toDp() } + 32.dp),
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
        2 -> WheelSheet(inventory.spinsWaiting, inventory.freezes >= Inventory.MAX_FREEZES, onSpin, onDismiss = { inventorySheet = 0 })
        }
    }
    if (collectionsSheet) {
        AllCollectionsSheet(
            rows = state.collections.all,
            isLocked = { it.locked(unlocked, entitlements, state.todayFree) },
            onOpen = { tile, entry ->
                collectionsSheet = false
                vm.opened(tile)
                when {
                    tile.damaged -> sheet = GallerySheet.Damaged(tile.ref)
                    tile.locked(unlocked, entitlements, setOfNotNull(tile.ref.id.takeIf { vm.isFreeToday(tile) })) -> onLocked(tile)
                    else -> onOpen(tile, entry)
                }
            },
            thumbnail = vm::thumbnail,
            onDismiss = { collectionsSheet = false },
        )
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
 * "Music is playing" + "Turn off" on a felt-deep chip (T42, D24; the allowance chip's ground, 16sp body per D14),
 * fading in over 150ms like every chip.
 */
@Composable
private fun MusicChip(onTurnOff: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { withFullMotion { alpha.animateTo(1f, tween(MUSIC_CHIP_FADE_MS)) } }
    Row(
        Modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.deep)
            .padding(start = 12.dp)
            .testTag(MUSIC_CHIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(MusicCopy.CHIP, style = GemType.body, color = GemColors.Bone)
        GemTextButton(MusicCopy.TURN_OFF, onTurnOff, Modifier.testTag("gallery-music-off-button"))
    }
}

private const val MUSIC_CHIP_FADE_MS = 150

/** The late share-link offer (T50). */
const val SHARED_OFFER_TAG = "gallery-shared-offer"

/**
 * "Shared with you: <title> · Open" (T50, DR2): the music chip's look, read once by TalkBack (a polite live region),
 * fading in like every chip. Its text wraps; Open never ellipsizes.
 */
@Composable
private fun SharedOfferChip(text: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { withFullMotion { alpha.animateTo(1f, tween(MUSIC_CHIP_FADE_MS)) } }
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.deep)
            .padding(start = 12.dp)
            .testTag(SHARED_OFFER_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = GemType.body,
            color = GemColors.Bone,
            modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
        )
        GemTextButton(SharedLandingCopy.OPEN, onOpen, Modifier.testTag("gallery-shared-open"))
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
 * Selected tab on felt-deep; both are 48dp targets. The daily play streak's chip (#33, #101) sits at the end of the row
 * from the first day, and wraps under the tabs when they leave no room (200% font).
 */
@Composable
private fun Tabs(
    selected: Int,
    onTab: (Int) -> Unit,
    streak: Int,
    streakDone: Boolean,
    repairable: Int?,
    ignite: Boolean,
    onIgnited: () -> Unit,
    onStreak: () -> Unit,
) {
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
        // From day 1 now that it opens the streak screen (#101, G18); hidden while there's no streak, except for a grey
        // "0 ›" while a broken one can be repaired (#102, G4).
        if (streak > 0 || repairable != null) {
            Spacer(Modifier.weight(1f))
            StreakChip(streak, streakDone, repairable.takeIf { streak == 0 }, ignite, onIgnited, onStreak)
        }
    }
}

/**
 * Today's picture (#34): a felt-deep card above the Pictures grid with the finished picture (its real progress once
 * started), "Today's picture" ("Today's pick" for VIP), the title, the size and "free today", and a quiet Start
 * (Continue once started; none when done). The whole card opens it; TalkBack reads it as one node. Today's drop (T36)
 * swaps the label line for the "New today" chip. The streak [nudge] (#102) and [tomorrow]'s Tomorrow line follow at the
 * bottom, each under a hairline, each its own node.
 */
@Composable
private fun TodayCardView(card: TodayCard, nudge: Nudge?, onNudge: () -> Unit, tomorrow: ImageBitmap?, onTomorrow: () -> Unit, onOpen: () -> Unit) {
    val tile = card.tile
    val action = when {
        tile.done -> "Open"
        tile.started -> "Continue"
        else -> "Start"
    }
    val description = A11yText.today(tile.title, card.size, card.vip, card.free, tile.started, tile.percent, tile.done, card.drop, tile.mystery)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalFelt.current.deep),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(TODAY_TAG)
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
                    if (tile.mystery) FrostCover()
                }
            }
            // Large screens (plan-design-review D19): the card spans the grid, its text at most 480dp wide.
            Column(Modifier.weight(1f, fill = false).widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // text-muted and bone on felt-deep (6.30 and 7.58:1); lamp stays "the selected one" only.
                if (card.drop) {
                    NewTodayChip()
                } else {
                    Text(if (card.vip) "Today's pick" else "Today's picture", style = GemType.label, color = GemColors.TextMuted)
                }
                Text(tile.title, style = GemType.button, color = GemColors.Bone, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(todayLine(card), style = GemType.label, color = GemColors.TextMuted)
                if (!tile.done) {
                    Spacer(Modifier.height(6.dp))
                    QuietButton(action, onOpen)
                }
            }
        }
        if (nudge != null) {
            Hairline()
            NudgeLine(nudge, onNudge)
        }
        if (tomorrow != null) {
            Hairline()
            TomorrowLine(tomorrow, onTomorrow)
        }
    }
}

/** A 1dp hairline at 12% bone between the Today card's lines (T36, #102). */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GemColors.Bone.copy(alpha = 0.12f)))
}

/**
 * The keep-it-alive nudge (#102, G2-G4): the grey flame, "Finish a picture today to keep your 6 days" in `bone`, its
 * hint ("Harbour is 82% done") and the awaiting-cover line in `text-muted` (valid on felt-deep), and "›". The broken
 * variant: "Your 12 days can still be restored" / "See your streak". At least 56dp, its text wrapping at large font;
 * the number in mono. TalkBack: one node, a button.
 */
@Composable
private fun NudgeLine(nudge: Nudge, onTap: () -> Unit) {
    val line = nudge.broken?.let(StreakText::stillRestorable) ?: StreakNudge.line(nudge.days)
    val description = listOfNotNull(line, nudge.hint, nudge.awaiting).joinToString(". ")
    val figure = (nudge.broken ?: nudge.days).toString()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(STREAK_NUDGE_TAG)
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onTap(); true }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Flame(lit = false, length = nudge.broken ?: nudge.days, size = 20.dp, still = true)
        Column(Modifier.weight(1f)) {
            Text(withFigure(line, figure, 15), style = GemType.body, color = GemColors.Bone, modifier = Modifier.widthIn(max = 480.dp))
            val percent = (nudge.target as? NudgeTarget.Canvas)?.canvas?.percent?.let { "$it%" }
            nudge.hint?.let { Text(withFigure(it, percent, 13), style = GemType.label, color = GemColors.TextMuted, modifier = Modifier.widthIn(max = 480.dp)) }
            nudge.awaiting?.let { Text(it, style = GemType.label, color = GemColors.TextMuted, modifier = Modifier.widthIn(max = 480.dp)) }
        }
        Text("›", style = GemType.button, color = GemColors.Bone)
    }
}

/** [text] with its [figure] (a count, a percent) in mono at [size]sp, like every figure. */
private fun withFigure(text: String, figure: String?, size: Int) = buildAnnotatedString {
    val at = figure?.let(text::indexOf) ?: -1
    if (figure == null || at < 0) {
        append(text)
    } else {
        append(text.substring(0, at))
        withStyle(GemType.mono.copy(fontSize = size.sp).toSpanStyle()) { append(figure) }
        append(text.substring(at + figure.length))
    }
}

/** The nudge on its own `felt-deep` card when there's no Today card (#102), the Tomorrow line under it (T36). */
@Composable
private fun NudgeCard(nudge: Nudge?, onNudge: () -> Unit, tomorrow: ImageBitmap?, onTomorrow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalFelt.current.deep),
    ) {
        if (nudge != null) NudgeLine(nudge, onNudge)
        if (tomorrow != null) {
            if (nudge != null) Hairline()
            TomorrowLine(tomorrow, onTomorrow)
        }
    }
}

/**
 * "New today" (T36): today's drop on the Today card, in place of its label line. A `felt` chip with the spark and
 * Figtree 13sp `bone` (5.68:1 on felt), one line at any font scale.
 */
@Composable
private fun NewTodayChip() {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.ground)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(NEW_TODAY_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(GemIcons.Spark, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("New today", style = GemType.label, color = GemColors.Bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The Tomorrow line (T36, founder pick T2): tomorrow's drop as a 40dp frost, "Tomorrow" in the card's label style over
 * "A new picture opens at midnight". No name, no countdown. 56dp at least, growing with its text at large font (D18).
 * A tap says "Opens tomorrow" and opens nothing. TalkBack: one node, a button.
 */
@Composable
private fun TomorrowLine(frost: ImageBitmap, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(TOMORROW_TAG)
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = A11yText.TOMORROW
                role = Role.Button
                onClick { onTap(); true }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))) {
            Image(frost, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            FrostCover()
        }
        Column(Modifier.widthIn(max = 480.dp)) {
            Text("Tomorrow", style = GemType.label, color = GemColors.TextMuted)
            Text("A new picture opens at midnight", style = GemType.label, color = GemColors.Bone)
        }
    }
}

/** The frost's `felt-deep` cover at 45% (design system, Hidden pictures), from the theme: T36's teaser, T43's mystery. */
@Composable
internal fun BoxScope.FrostCover() {
    Box(Modifier.matchParentSize().background(LocalFelt.current.deep.copy(alpha = Frost.OVERLAY_ALPHA)))
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

/**
 * The streak chip (#101, G18): a 48dp button with the flame, grey until today's picture is finished and lit after
 * ([ignite]: it lights up once, after a celebration skipped on a cold start, G9), and the count in mono; it opens the
 * streak screen. One TalkBack node. While a [broken] streak can be repaired (#102, G4) it reads a grey "0 ›".
 */
@Composable
private fun StreakChip(days: Int, done: Boolean, broken: Int?, ignite: Boolean, onIgnited: () -> Unit, onClick: () -> Unit) {
    val description = broken?.let(StreakText::chipBroken) ?: StreakText.chip(days, done)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.deep)
            .testTag(STREAK_TAG)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Flame(lit = done, length = days, size = 16.dp, still = true, ignite = ignite, onIgnited = onIgnited)
        Text("$days", style = GemType.mono.copy(fontSize = 15.sp), color = GemColors.Bone)
        Text("›", style = GemType.button, color = GemColors.Bone)
    }
}

@Composable
private fun TileGrid(
    tiles: List<Tile>,
    /** The tab's Finished section (#89), after its tiles; no heading when empty. */
    finished: List<Tile>,
    finishedOpen: Boolean,
    onFinished: () -> Unit,
    /** My Photos, for the heading's TalkBack noun. */
    photos: Boolean,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)?,
    /** Full-width items after [header], each with its key: the collection rows and the All collections link (T37). */
    extras: List<Pair<String, @Composable () -> Unit>>,
    gridState: LazyStaggeredGridState,
    isLocked: (Tile) -> Boolean,
    onOpen: (Tile) -> Unit,
    /** An unstarted remote tile's thumbnail, drawn when it shows (T36, eng D12). */
    thumbnail: suspend (Tile) -> ImageBitmap?,
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
        state = gridState,
        // Room at the bottom so the floating button never covers a tile's % (design D10).
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
    ) {
        // Today's picture (#34) spans the grid's width and scrolls with it, so it never holds space at 200% font.
        if (header != null) item(key = TODAY_TAG, span = StaggeredGridItemSpan.FullLine) { header() }
        for ((key, content) in extras) item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }
        items(tiles, key = { "${it.ref.kind}/${it.ref.id}" }) { tile ->
            TileView(tile, isLocked(tile), onOpen, thumbnail, onShareProgress, onRename, onDelete, onStartOver)
        }
        // The Finished section (#89): its heading keeps one key, so TalkBack focus stays on it as it expands.
        if (finished.isNotEmpty()) {
            item(key = FINISHED_TAG, span = StaggeredGridItemSpan.FullLine) { FinishedHeading(finished.size, finishedOpen, photos, onFinished) }
            if (finishedOpen) {
                items(finished, key = { "${it.ref.kind}/${it.ref.id}" }) { tile ->
                    TileView(tile, isLocked(tile), onOpen, thumbnail, onShareProgress, onRename, onDelete, onStartOver)
                }
            }
        }
    }
}

/**
 * The Finished heading (#89): a quiet full-width row on the felt, "Finished · 12" in the label style (the count in mono,
 * like every number) and a chevron that turns up when open. At 200% font the count wraps under the label. TalkBack:
 * one node, a button, "Finished, 12 pictures", collapsed or expanded.
 */
@Composable
private fun FinishedHeading(count: Int, open: Boolean, photos: Boolean, onTap: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val turn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(CHEVRON_MS),
        label = "finished-chevron",
    )
    val wrap = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val description = A11yText.finished(count, photos)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .testTag(FINISHED_TAG)
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = if (open) "expanded" else "collapsed"
                role = Role.Button
                onClick { onTap(); true }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Large screens (plan-design-review D19): the text column at most 480dp wide.
        val label: @Composable () -> Unit = { Text("Finished", style = GemType.label, color = GemColors.TextMuted) }
        val number: @Composable () -> Unit = { Text("$count", style = GemType.mono.copy(fontSize = 13.sp), color = GemColors.TextMuted) }
        if (wrap) {
            Column(Modifier.widthIn(max = 480.dp)) {
                label()
                number()
            }
        } else {
            Row(Modifier.widthIn(max = 480.dp), verticalAlignment = Alignment.CenterVertically) {
                label()
                Text(" · ", style = GemType.label, color = GemColors.TextMuted)
                number()
            }
        }
        Image(
            GemIcons.Chevron,
            contentDescription = null,
            colorFilter = ColorFilter.tint(GemColors.TextMuted),
            modifier = Modifier.size(20.dp).rotate(turn),
        )
    }
}

/** The chevron's turn (#89); instant under reduced motion. */
private const val CHEVRON_MS = 150

/** Above it, the Finished heading's count wraps under its label (#89), as the canvas does at 200% font. */
private const val LARGE_FONT_SCALE = 1.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileView(
    tile: Tile,
    locked: Boolean,
    onOpen: (Tile) -> Unit,
    thumbnail: suspend (Tile) -> ImageBitmap?,
    onShareProgress: (Tile) -> Unit,
    onRename: (Tile) -> Unit,
    onDelete: (Tile) -> Unit,
    onStartOver: (Tile) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val photo = tile.ref.kind == CanvasKind.PHOTO
    val description = A11yText.tile(tile.title, tile.percent, tile.done, locked, tile.damaged, tile.isNew, tile.mystery)
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
            // An unstarted remote picture (T36, eng D12) shows plain felt-deep until its thumbnail is drawn.
            val image = if (tile.lazyImage && tile.image == null) {
                produceState<ImageBitmap?>(null, tile.ref) { value = thumbnail(tile) }.value
            } else {
                tile.image
            }
            image?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
                if (tile.mystery) FrostCover()
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
            if (tile.isNew && !tile.damaged) {
                // A remote picture that just arrived (#43): top-left, so it never meets "Watch to unlock" below. One
                // line at any font scale; on a narrow tile at 2× it may ellipsize, never wrap.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LocalFelt.current.deep)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag(NEW_LABEL_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(GemIcons.Spark, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", style = GemType.label, color = GemColors.Bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (tile.mystery && !tile.damaged) {
                // An unfinished mystery picture (T43): New's shape and place, with the gem outline.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LocalFelt.current.deep)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag(MYSTERY_LABEL_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(GemIcons.GemOutline, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mystery", style = GemType.label, color = GemColors.Bone, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
