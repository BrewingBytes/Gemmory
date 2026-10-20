package com.brewingbytes.gemmory.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.collections.CollectionCopy
import com.brewingbytes.gemmory.telemetry.CanvasEntry
import com.brewingbytes.gemmory.ui.A11yText
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion

/** Above it, one row shows (D4) and the "more coming" well shows only "+2", its words joining the count line (D18). */
internal const val COLLECTION_LARGE_FONT = 1.5f

/**
 * A collection row (T37, design system "Collection and event rows"): the name, "3 of 7 finished", one line, then a
 * sideways strip of 64dp member thumbnails with real progress and the "more coming" well. A thumbnail's tap is its
 * tile's tap. [glint] plays the completion glint once (D7: only while nothing is over the gallery), then [onGlintDone].
 * No timer, progress bar or badge.
 */
@Composable
internal fun CollectionRowView(
    row: CollectionRow,
    isLocked: (Tile) -> Boolean,
    onOpen: (Tile) -> Unit,
    thumbnail: suspend (Tile) -> ImageBitmap?,
    glint: Boolean,
    onGlintDone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeFont = LocalDensity.current.fontScale > COLLECTION_LARGE_FONT
    val reducedMotion = rememberReducedMotion()
    val sweep = remember { Animatable(-1f) }
    val edge = remember { Animatable(0f) }
    val done by rememberUpdatedState(onGlintDone)
    LaunchedEffect(row.id, glint) {
        if (!glint) return@LaunchedEffect
        // Reduced motion: no sweep and no edge; the line just shows.
        if (!reducedMotion) {
            withFullMotion {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(GLINT_SWEEP_MS, easing = FastOutSlowInEasing))
                sweep.snapTo(-1f)
                edge.animateTo(1f, tween(GLINT_EDGE_MS / 2))
                edge.animateTo(0f, tween(GLINT_EDGE_MS / 2))
            }
        }
        done(row.id)
    }
    Column(
        modifier
            .fillMaxWidth()
            .testTag(collectionTag(row.id))
            .drawWithContent {
                drawContent()
                val at = sweep.value
                if (at >= 0f) {
                    // A lamp band (55%) crossing the row once (D15: lamp's reward-moment meaning).
                    val band = size.width * 0.25f
                    val x = -band + (size.width + band) * at
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, GemColors.Lamp.copy(alpha = 0.55f), Color.Transparent),
                            startX = x,
                            endX = x + band,
                        ),
                    )
                }
                if (edge.value > 0f) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        GemColors.Lamp.copy(alpha = edge.value),
                        topLeft = Offset(w / 2, w / 2),
                        size = size.copy(width = size.width - w, height = size.height - w),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(w),
                    )
                }
            }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Large screens (plan-design-review D19): the text column at most 480dp; the strip spans the row.
        Column(Modifier.widthIn(max = 480.dp)) {
            // TalkBack: the name is a heading that folds in the count and the well; the line is read after it.
            val heading = A11yText.collection(row.title, row.finished, row.size, row.coming)
            Column(Modifier.clearAndSetSemantics { contentDescription = heading; heading() }) {
                Text(row.title, style = GemType.button, color = GemColors.Bone, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(countLine(row, largeFont), style = GemType.label, color = GemColors.Bone)
            }
            Text(
                row.line,
                style = GemType.label,
                color = GemColors.Bone,
                modifier = Modifier.testTag(COLLECTION_LINE_TAG),
            )
            if (glint && (row.complete || row.earned)) {
                // A polite announcement as the completed (or event-earned, T38) row first shows; invisible, read once.
                val announcement = if (row.earned && !row.complete) {
                    A11yText.eventEarned(row.title, row.keepsakeTitle != null)
                } else {
                    A11yText.collectionComplete(row.title, row.keepsakeTitle != null)
                }
                Box(Modifier.size(1.dp).semantics { contentDescription = announcement; liveRegion = LiveRegionMode.Polite })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(row.members, key = { "${it.ref.kind}/${it.ref.id}" }) { tile -> MemberThumb(tile, isLocked(tile), onOpen, thumbnail) }
            if (row.coming > 0) item(key = "coming") { ComingWell(row.coming, largeFont, oneADay = row.inWindow) }
        }
    }
}

/**
 * "3 of 7 finished" ([CollectionCopy.count]) with its numbers in mono 13sp, as every number is; above 1.5× font
 * " · 2 more coming" joins it (D18).
 */
private fun countLine(row: CollectionRow, largeFont: Boolean) = buildAnnotatedString {
    val mono = GemType.mono.copy(fontSize = 13.sp).toSpanStyle()
    withStyle(mono) { append("${row.finished}") }
    append(" of ")
    withStyle(mono) { append("${row.size}") }
    append(" finished")
    if (largeFont && row.coming > 0) append(" · ${CollectionCopy.coming(row.coming, oneADay = row.inWindow)}")
}

/**
 * A member thumbnail: 64dp, center-cropped, `sm` radius, real progress like its tile. Its labels shrink to the corner
 * mark only (New, Watch to unlock, Can't open), and a finished one gets a check. One TalkBack node read like its tile.
 */
@Composable
private fun MemberThumb(tile: Tile, locked: Boolean, onOpen: (Tile) -> Unit, thumbnail: suspend (Tile) -> ImageBitmap?) {
    val description = A11yText.tile(tile.title, tile.percent, tile.done, locked, tile.damaged, tile.isNew)
    Box(
        Modifier
            .size(THUMB)
            .clip(RoundedCornerShape(4.dp))
            .background(LocalFelt.current.deep)
            .testTag(memberTag(tile.ref.id))
            .clickable { onOpen(tile) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onOpen(tile); true }
            },
    ) {
        val image = if (tile.lazyImage && tile.image == null) {
            produceState<ImageBitmap?>(null, tile.ref) { value = thumbnail(tile) }.value
        } else {
            tile.image
        }
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        }
        when {
            tile.damaged -> CornerMark(GemIcons.Close, Alignment.BottomStart)
            locked -> CornerMark(GemIcons.Play, Alignment.BottomStart)
        }
        if (tile.isNew && !tile.damaged) CornerMark(GemIcons.Spark, Alignment.TopStart)
        if (tile.done) {
            Box(
                Modifier.align(Alignment.TopEnd).size(16.dp).background(LocalFelt.current.deep),
                contentAlignment = Alignment.Center,
            ) {
                Image(GemIcons.Check, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone), modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CornerMark(icon: ImageVector, alignment: Alignment) {
    Box(
        Modifier.align(alignment).padding(2.dp).clip(RoundedCornerShape(4.dp)).background(LocalFelt.current.deep).padding(2.dp),
    ) {
        Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone), modifier = Modifier.size(12.dp))
    }
}

/**
 * Members not yet here (D14): one recessed well at the strip's end, `felt-deep` with the trough's inset shadow,
 * "2 more coming" (13sp, wraps); above 1.5× font only "+2" in mono (D18). Folded into the heading for TalkBack.
 */
@Composable
private fun ComingWell(coming: Int, largeFont: Boolean, oneADay: Boolean) {
    val felt = LocalFelt.current
    Box(Modifier.size(THUMB).testTag(COMING_TAG).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(4.dp.toPx())
            drawRoundRect(felt.deep, cornerRadius = corner)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endY = 6.dp.toPx()),
                cornerRadius = corner,
            )
        }
        if (largeFont) {
            Text(CollectionCopy.comingShort(coming), style = GemType.mono.copy(fontSize = 13.sp), color = GemColors.Bone)
        } else {
            BasicText(
                CollectionCopy.coming(coming, oneADay),
                style = GemType.label.copy(color = GemColors.Bone, textAlign = TextAlign.Center),
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/** "All collections" (D3): a text button right after the rows, before the grid. */
@Composable
internal fun AllCollectionsLink(onClick: () -> Unit) {
    GemTextButton("All collections", onClick, Modifier.testTag(ALL_COLLECTIONS_TAG))
}

/**
 * The All collections sheet: every row, newest first, complete ones last, the same component. The strips scroll
 * sideways inside the vertically scrolling sheet; a sideways drag never drags it (the sheet only drags vertically).
 * No empty state: the link shows only when there's something to list. No glint here.
 */
@Composable
internal fun AllCollectionsSheet(
    rows: List<CollectionRow>,
    isLocked: (Tile) -> Boolean,
    onOpen: (Tile, CanvasEntry) -> Unit,
    thumbnail: suspend (Tile) -> ImageBitmap?,
    onDismiss: () -> Unit,
) {
    GemSheet("Collections", onDismiss, returnFocusTo = ALL_COLLECTIONS_TAG) {
        rows.forEach { row ->
            CollectionRowView(row, isLocked, { onOpen(it, row.entry) }, thumbnail, glint = false, onGlintDone = {})
        }
    }
}

private val THUMB = 64.dp

/** The completion glint (D7): the band's sweep, then the edge fading in and out. */
private const val GLINT_SWEEP_MS = 900
private const val GLINT_EDGE_MS = 400

const val ALL_COLLECTIONS_TAG = "all-collections"
const val COLLECTION_LINE_TAG = "collection-line"
const val COMING_TAG = "collection-coming"

fun collectionTag(id: String) = "collection-$id"

fun memberTag(id: String) = "collection-member-$id"
