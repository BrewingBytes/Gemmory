package com.brewingbytes.gemmory.gallery

import com.brewingbytes.gemmory.app.LocalShellUi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.brewingbytes.gemmory.copy.LocalWords
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.policy.Inventory
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.GiftPrize
import com.brewingbytes.gemmory.policy.WheelPrize
import com.brewingbytes.gemmory.policy.WheelSlice
import com.brewingbytes.gemmory.ads.LocalAdSpots
import com.brewingbytes.gemmory.ads.Spot
import com.brewingbytes.gemmory.ads.SpotOutcome
import com.brewingbytes.gemmory.ads.TapLook
import com.brewingbytes.gemmory.ads.TapSpot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import com.brewingbytes.gemmory.streak.NextGift
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The gallery's Inventory button (#38); also where TalkBack focus returns when its sheets close. */
const val INVENTORY_TAG = "gallery-inventory"

/**
 * The Inventory button (#38, founder pick E1): a 48dp bag labelled "Inventory" in the top bar, for players without
 * VIP. A small bone square sits on it while a gift spin is waiting; the counts live in the sheet.
 */
@Composable
fun InventoryButton(spinWaiting: Boolean, onClick: () -> Unit) {
    val label = LocalWords.current.text(if (spinWaiting) "inventory_spin_waiting" else "inventory_title")
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            // The tag before the clear, which drops everything after it.
            .testTag(INVENTORY_TAG)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(GemIcons.Bag, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
        if (spinWaiting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(7.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GemColors.Bone),
            )
        }
    }
}

/**
 * The Inventory sheet (#38): fills, kept photo canvases, unlock tokens and streak freezes (#102) in recessed wells with
 * mono counts, then the spin when one waits, the fills video, and the streak's way to the next gift. VIP never sees it.
 */
@Composable
fun InventorySheet(
    state: InventoryState,
    streak: Int,
    onWatchFills: () -> Unit,
    onSpin: () -> Unit,
    onDismiss: () -> Unit,
    /** False while ads are off (#203): the fills come free, "Get 3 fills". */
    adsOn: Boolean = true,
    /** Today's gift is still there today and its video is ready (1.0.7, DR-D7): its row comes first. */
    gift: Boolean = false,
    onGift: () -> Unit = {},
) {
    val strings = LocalWords.current
    GemSheet(LocalWords.current.text("inventory_title"), onDismiss, returnFocusTo = INVENTORY_TAG) {
        if (gift) GiftRow(onGift)
        ItemRow(GemIcons.ToolRegion, state.fills, LocalWords.current.text("inventory_fills"), LocalWords.current.text("inventory_fills_body"), "inventory-fills")
        ItemRow(GemIcons.PhotoCanvas, state.canvases, LocalWords.current.text("inventory_canvases"), LocalWords.current.text("inventory_canvases_body"), "inventory-canvases")
        ItemRow(GemIcons.Token, state.tokens, LocalWords.current.text("inventory_tokens"), LocalWords.current.text("inventory_tokens_body"), "inventory-tokens")
        ItemRow(
            GemIcons.Freeze,
            state.freezes,
            LocalWords.current.text("inventory_freezes"),
            strings.text("inventory_freezes_body", Inventory.MAX_FREEZES),
            "inventory-freezes",
        )
        Spacer(Modifier.height(12.dp))
        if (state.spinsWaiting > 0) {
            PrimaryButton(spinLabel(strings, state.spinsWaiting), onSpin, Modifier.fillMaxWidth().testTag("inventory-spin"))
            Spacer(Modifier.height(8.dp))
            QuietButton(watchLabel(strings, adsOn), onWatchFills, Modifier.fillMaxWidth().testTag("inventory-watch"))
        } else {
            PrimaryButton(watchLabel(strings, adsOn), onWatchFills, Modifier.fillMaxWidth().testTag("inventory-watch"))
            Spacer(Modifier.height(16.dp))
            NextGift(streak, tag = "inventory-next-gift")
        }
    }
}

internal fun watchLabel(s: Words, adsOn: Boolean = true) =
    s.plural(if (adsOn) "inventory_watch" else "inventory_get_fills", Inventory.FILLS_PER_VIDEO, Inventory.FILLS_PER_VIDEO)

internal fun spinLabel(s: Words, waiting: Int) =
    if (waiting > 1) s.plural("inventory_spin_several", waiting, waiting) else s.text("inventory_spin", Inventory.SPIN_EVERY)

/** Today's gift's row in the Inventory sheet (DR-D7): until it's opened or local midnight, after a dismissed popup. */
@Composable
private fun GiftRow(onClick: () -> Unit) {
    val words = LocalWords.current
    val name = words.text("gift_title")
    val body = words.text("gift_row_body")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("inventory-gift")
            .clearAndSetSemantics {
                contentDescription = "$name. $body"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(LocalFelt.current.ground),
            contentAlignment = Alignment.Center,
        ) {
            Image(GemIcons.GiftBox, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
        }
        // Where the item rows' 40dp count column ends, so the names line up.
        Spacer(Modifier.size(width = 40.dp, height = 0.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = GemType.body, color = GemColors.Bone)
            Text(body, style = GemType.label, color = GemColors.TextMuted)
        }
    }
}

@Composable
private fun ItemRow(icon: ImageVector, count: Int, name: String, body: String, tag: String) {
    val description = LocalWords.current.text("inventory_item", name, count, body)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(tag)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(LocalFelt.current.ground),
            contentAlignment = Alignment.Center,
        ) {
            Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
        }
        Text(
            "$count",
            style = GemType.mono.copy(fontSize = 18.sp),
            color = GemColors.Bone,
            modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = GemType.body, color = GemColors.Bone)
            Text(body, style = GemType.label, color = GemColors.TextMuted)
        }
    }
}

/** A slice's shell color (no new UI color); the streak freeze's `felt` slice carries a bone cross (#102). */
private fun WheelSlice.color(): Color = when (this) {
    WheelPrize.FILLS_SMALL, GiftPrize.FILLS_SMALL -> GemColors.Patina
    WheelPrize.FILLS_LARGE, GiftPrize.FILLS_LARGE -> GemColors.PatinaText
    WheelPrize.PHOTO_CANVAS, GiftPrize.PHOTO_CANVAS -> GemColors.Bone
    WheelPrize.UNLOCK_TOKEN, GiftPrize.UNLOCK_TOKEN -> GemColors.TextMuted
    else -> GemColors.Felt
}

/**
 * "3 fills", "1 photo canvas", "1 unlock token", "Streak freeze"; the freeze slice reads its fills while
 * [freezesFull] (#102, E7: at 2 held it pays fills).
 */
fun WheelSlice.label(s: Words, freezesFull: Boolean = false): String = when (item) {
    Item.FILLS -> s.plural("prize_fills", amount, amount)
    Item.PHOTO_CANVAS -> s.plural("prize_photo_canvases", amount, amount)
    Item.UNLOCK_TOKEN -> s.plural("prize_unlock_tokens", amount, amount)
    Item.STREAK_FREEZE ->
        if (freezesFull) s.plural("prize_fills", Inventory.FILLS_FOR_FULL_FREEZE, Inventory.FILLS_FOR_FULL_FREEZE) else s.text("prize_streak_freeze")
}

/** What a spin pays, as an offer: "1 photo canvas", "3 fills", "Streak freeze" → "1 streak freeze" (DR-D12). */
internal fun Spin.amountLabel(s: Words): String = when (item) {
    Item.FILLS -> s.plural("prize_fills", amount, amount)
    Item.PHOTO_CANVAS -> s.plural("prize_photo_canvases", amount, amount)
    Item.UNLOCK_TOKEN -> s.plural("prize_unlock_tokens", amount, amount)
    Item.STREAK_FREEZE -> s.text("prize_one_freeze")
}

/** "3 fills added to your inventory." / "A streak freeze added to your inventory." */
fun Spin.result(s: Words): String = when (item) {
    Item.STREAK_FREEZE -> s.text("prize_added_freeze")
    Item.FILLS -> s.plural("prize_added_fills", amount, amount)
    else -> s.text("prize_added", s.titlecaseFirst(amountLabel(s)))
}

/** Two pays that read as one total ("12 fills added"): the same item, and not the freeze, whose line has no count. */
private fun sameKind(a: Spin, b: Spin) = a.item == b.item && a.item != Item.STREAK_FREEZE

/** Where [prize]'s segment's middle is, in degrees clockwise from the top. */
internal fun segmentMiddle(prize: WheelSlice, slices: List<WheelSlice> = WheelPrize.entries): Float {
    var start = 0f
    for (p in slices) {
        val sweep = p.percent * 3.6f
        if (p == prize) return start + sweep / 2f
        start += sweep
    }
    error("no segment for $prize")
}

/** The segment under the pointer when the wheel is turned [rotation] degrees clockwise. */
internal fun segmentAt(rotation: Float, slices: List<WheelSlice> = WheelPrize.entries): WheelSlice {
    val at = ((-rotation % 360f) + 360f) % 360f
    var start = 0f
    for (p in slices) {
        start += p.percent * 3.6f
        if (at < start) return p
    }
    return slices.last()
}

/**
 * The 5-day gift wheel (#38, founder pick W1): five flat segments sized by their odds (#102 added the streak freeze;
 * it reads its fills while [freezesFull]), with the odds in words below.
 * Spin rolls and adds the prize at once (a kill mid-spin can't spin again), then the wheel turns 5 times and lands on
 * it over 3.2s, easing out, with a tick as each segment passes the pointer and the milestone chime on landing. Under
 * reduced motion it doesn't turn: the result appears with the chime. 1.0.7 (DR-D12): once it lands, "Watch a video:
 * +<the slice>" when its video is ready, between the result and Spin/Done; Spin again or closing drops it.
 */
@Composable
fun WheelSheet(spinsWaiting: Int, freezesFull: Boolean, onSpin: suspend () -> Spin?, onDismiss: () -> Unit) {
    val spots = LocalAdSpots.current
    // Keyed by the landing, not the Spin: two landings on the same slice are equal Spins (review).
    val again = remember { mutableStateOf<Int?>(null) }
    // What "same again" paid on a landing: shown in the result line as one total (design review).
    val paid = remember { mutableStateOf<Pair<Int, Spin>?>(null) }
    DisposableEffect(Unit) {
        spots.want(Spot.AGAIN)
        onDispose { spots.unwant(Spot.AGAIN) }
    }
    PrizeWheelSheet(
        title = LocalWords.current.text("wheel_title"),
        slices = WheelPrize.entries,
        spinsWaiting = spinsWaiting,
        freezesFull = freezesFull,
        spinLabel = LocalWords.current.text("wheel_spin"),
        onSpin = onSpin,
        onDismiss = onDismiss,
        paidAgain = { landing -> paid.value?.takeIf { it.first == landing }?.second },
        afterLanding = { spin, spinning, landing ->
            // Decided once at landing (DR-D12): shown only if its video is ready then.
            LaunchedEffect(landing) { again.value = landing.takeIf { Spot.AGAIN in spots.ready.value } }
            if (again.value == landing && !spinning) {
                LaunchedEffect(landing) { spots.seen(Spot.AGAIN) }
                key(landing) {
                    TapSpot(
                        LocalWords.current.text("spot_again", spin.amountLabel(LocalWords.current)),
                        TapLook.QUIET,
                        play = { spots.playAgain(spin) },
                        onGranted = { paid.value = landing to it.spin },
                        tag = "wheel-again",
                        grantedLine = { !sameKind(spin, it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    )
}

/**
 * Today's gift (1.0.7, DR-D11): the wheel sheet with [GiftPrize]'s four slices and their odds before the video, "Watch
 * a video to spin" and "Not now". The video plays first; on its reward the prize is rolled and added, then the wheel
 * turns and lands on it. "Not now" (or a dismiss) keeps it for the Inventory row until midnight (DR-D7).
 */
@Composable
fun GiftSheet(onDismiss: () -> Unit) {
    val spots = LocalAdSpots.current
    var outcome by remember { mutableStateOf<SpotOutcome?>(null) }
    LaunchedEffect(Unit) { spots.seen(Spot.GIFT) }
    PrizeWheelSheet(
        title = LocalWords.current.text("gift_title"),
        slices = GiftPrize.entries,
        spinsWaiting = 1,
        freezesFull = false,
        spinLabel = LocalWords.current.text("gift_watch"),
        onSpin = {
            val result = spots.playGift()
            outcome = result
            (result as? SpotOutcome.Granted)?.spin
        },
        onDismiss = onDismiss,
        notNow = LocalWords.current.text("gift_not_now"),
        afterLanding = { _, _, _ -> },
        beforeButtons = {
            when (outcome) {
                SpotOutcome.ClosedEarly -> SpotLine(LocalWords.current.text("spot_watch_to_end"), "gift-closed-early")
                SpotOutcome.NoVideo, SpotOutcome.Gone -> SpotLine(LocalWords.current.text("spot_no_video"), "gift-none")
                SpotOutcome.Offline -> SpotLine(LocalWords.current.text("video_offline"), "gift-offline")
                else -> Unit
            }
        },
        // Closed early: the offer stays for another go; no video or offline: only "Not now".
        retryOnMiss = { outcome == SpotOutcome.ClosedEarly },
    )
}

@Composable
private fun SpotLine(text: String, tag: String) {
    Text(
        text,
        style = GemType.label,
        color = GemColors.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The prize wheel sheet both wheels share: the slices, their odds in words, the result line and the buttons. */
@Composable
private fun PrizeWheelSheet(
    title: String,
    slices: List<WheelSlice>,
    spinsWaiting: Int,
    freezesFull: Boolean,
    spinLabel: String,
    onSpin: suspend () -> Spin?,
    onDismiss: () -> Unit,
    afterLanding: @Composable (spin: Spin, spinning: Boolean, landing: Int) -> Unit,
    notNow: String? = null,
    beforeButtons: @Composable () -> Unit = {},
    retryOnMiss: () -> Boolean = { false },
    paidAgain: (landing: Int) -> Spin? = { null },
) {
    val reducedMotion = rememberReducedMotion()
    val feedback = LocalShellUi.current.rememberFeedback()
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf<Spin?>(null) }
    // Counts the landings in this sheet, so each one is its own offer moment even on the same slice.
    var landing by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(spinsWaiting) }
    val label = remember { FreezeLabel(freezesFull) }
    val latestFull by rememberUpdatedState(freezesFull)
    LaunchedEffect(freezesFull) { label.held(freezesFull) }
    val full = label.full

    LaunchedEffect(Unit) {
        var last = segmentAt(rotation.value, slices)
        snapshotFlow { segmentAt(rotation.value, slices) }.collect {
            if (it != last && spinning) feedback.gemPlaced()
            last = it
        }
    }

    val strings = LocalWords.current
    val wheelDescription = strings.text("wheel_description", odds(strings, full, slices))
    GemSheet(title, onDismiss, returnFocusTo = INVENTORY_TAG) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Wheel(
                rotation.value,
                slices,
                Modifier
                    .padding(top = 12.dp)
                    .size(220.dp)
                    .testTag("wheel")
                    .clearAndSetSemantics { contentDescription = wheelDescription },
            )
            // The pointer, over the wheel's top edge.
            Canvas(Modifier.size(20.dp, 16.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    },
                    GemColors.Bone,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            for (prize in slices) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(prize.color()))
                    Text(" " + strings.text("wheel_odd", prize.label(strings, full), prize.percent), style = GemType.label, color = GemColors.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val result = won?.takeIf { !spinning }
        Text(
            result?.let { won ->
                val extra = paidAgain(landing)
                (if (extra != null && sameKind(won, extra)) won.copy(amount = won.amount + extra.amount) else won).result(strings)
            } ?: "",
            style = GemType.body,
            color = GemColors.Bone,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wheel-result")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(12.dp))
        won?.let { afterLanding(it, spinning, landing) }
        beforeButtons()
        if (left > 0 || spinning) {
            PrimaryButton(
                spinLabel,
                {
                    if (spinning) return@PrimaryButton
                    label.spinStarted(latestFull)
                    spinning = true
                    scope.launch {
                        val spin = onSpin()
                        if (spin == null) {
                            spinning = false
                            label.spinEnded(latestFull)
                            if (!retryOnMiss()) left = 0
                            return@launch
                        }
                        left--
                        won = spin
                        landing++
                        val prize = spin.prize
                        val from = rotation.value % 360f
                        rotation.snapTo(from)
                        val target = from - from % 360f + TURNS * 360f + (360f - segmentMiddle(prize, slices))
                        if (reducedMotion) {
                            rotation.snapTo(target)
                        } else {
                            withFullMotion { rotation.animateTo(target, tween(SPIN_MS, easing = SPIN_EASING)) }
                        }
                        spinning = false
                        label.spinEnded(latestFull)
                        feedback.milestone()
                    }
                },
                Modifier.fillMaxWidth().testTag("wheel-spin"),
                held = spinning,
            )
            if (notNow != null && won == null && !spinning) {
                Spacer(Modifier.height(8.dp))
                QuietButton(notNow, onDismiss, Modifier.fillMaxWidth().testTag("wheel-not-now"))
            }
        } else {
            QuietButton(LocalWords.current.text("action_done"), onDismiss, Modifier.fillMaxWidth().testTag("wheel-done"))
        }
    }
}

/**
 * Whether the freeze slice reads "5 fills" (#102, E7). Taken when a spin is tapped and again when it lands, never in
 * between: the prize is added before the wheel turns, so a freeze won at 1 held would otherwise relabel the slice the
 * wheel is landing on (design system: "its label changes only between spins").
 */
class FreezeLabel(full: Boolean) {
    var full by mutableStateOf(full)
        private set
    private var spinning = false

    /** The Spin tap, with the freezes held as of now. */
    fun spinStarted(nowFull: Boolean) {
        full = nowFull
        spinning = true
    }

    /** The wheel landed (or there was no spin). */
    fun spinEnded(nowFull: Boolean) {
        spinning = false
        full = nowFull
    }

    /** The freezes held changed: shown at once between spins, ignored during one. */
    fun held(nowFull: Boolean) {
        if (!spinning) full = nowFull
    }
}

internal fun odds(s: Words, freezesFull: Boolean = false, slices: List<WheelSlice> = WheelPrize.entries): String =
    slices.joinToString(s.text("list_separator")) { s.text("wheel_odd", it.label(s, freezesFull), it.percent) }

@Composable
private fun Wheel(rotation: Float, slices: List<WheelSlice>, modifier: Modifier) {
    val deep = LocalFelt.current.deep
    Canvas(modifier.rotate(rotation)) {
        var start = -90f
        for (prize in slices) {
            val sweep = prize.percent * 3.6f
            drawArc(prize.color(), start, sweep, useCenter = true)
            drawArc(deep, start, sweep, useCenter = true, style = Stroke(width = 2.dp.toPx()))
            if (prize == WheelPrize.STREAK_FREEZE) freezeCross(start + sweep / 2f)
            start += sweep
        }
        val hub = 20.dp.toPx()
        drawCircle(deep, hub, Offset(size.width / 2f, size.height / 2f))
    }
}

/** The freeze mark on its slice: a bone cross two thirds out, along [angle] (degrees, 0 = 3 o'clock). */
private fun DrawScope.freezeCross(angle: Float) {
    val radians = angle.toDouble() * (PI / 180.0)
    val reach = size.minDimension / 2f * 0.66f
    val center = Offset(size.width / 2f + reach * cos(radians).toFloat(), size.height / 2f + reach * sin(radians).toFloat())
    val arm = 8.dp.toPx()
    val stroke = 2.4.dp.toPx()
    drawLine(GemColors.Bone, center - Offset(0f, arm), center + Offset(0f, arm), stroke, StrokeCap.Round)
    drawLine(GemColors.Bone, center - Offset(arm, 0f), center + Offset(arm, 0f), stroke, StrokeCap.Round)
}

private const val TURNS = 5
private const val SPIN_MS = 3_200
private val SPIN_EASING = CubicBezierEasing(0.1f, 0.6f, 0.2f, 1f)
