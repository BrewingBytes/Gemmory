package com.brewingbytes.gemmory.gallery

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.policy.Inventory
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.WheelPrize
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
    val label = stringResource(if (spinWaiting) R.string.inventory_spin_waiting else R.string.inventory_title)
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
) {
    val strings = strings()
    GemSheet(stringResource(R.string.inventory_title), onDismiss, returnFocusTo = INVENTORY_TAG) {
        ItemRow(GemIcons.ToolRegion, state.fills, stringResource(R.string.inventory_fills), stringResource(R.string.inventory_fills_body), "inventory-fills")
        ItemRow(GemIcons.PhotoCanvas, state.canvases, stringResource(R.string.inventory_canvases), stringResource(R.string.inventory_canvases_body), "inventory-canvases")
        ItemRow(GemIcons.Token, state.tokens, stringResource(R.string.inventory_tokens), stringResource(R.string.inventory_tokens_body), "inventory-tokens")
        ItemRow(
            GemIcons.Freeze,
            state.freezes,
            stringResource(R.string.inventory_freezes),
            strings.text(R.string.inventory_freezes_body, Inventory.MAX_FREEZES),
            "inventory-freezes",
        )
        Spacer(Modifier.height(12.dp))
        if (state.spinsWaiting > 0) {
            PrimaryButton(spinLabel(strings, state.spinsWaiting), onSpin, Modifier.fillMaxWidth().testTag("inventory-spin"))
            Spacer(Modifier.height(8.dp))
            QuietButton(watchLabel(strings), onWatchFills, Modifier.fillMaxWidth().testTag("inventory-watch"))
        } else {
            PrimaryButton(watchLabel(strings), onWatchFills, Modifier.fillMaxWidth().testTag("inventory-watch"))
            Spacer(Modifier.height(16.dp))
            NextGift(streak, tag = "inventory-next-gift")
        }
    }
}

internal fun watchLabel(s: Strings) = s.plural(R.plurals.inventory_watch, Inventory.FILLS_PER_VIDEO, Inventory.FILLS_PER_VIDEO)

internal fun spinLabel(s: Strings, waiting: Int) =
    if (waiting > 1) s.plural(R.plurals.inventory_spin_several, waiting, waiting) else s.text(R.string.inventory_spin, Inventory.SPIN_EVERY)

@Composable
private fun ItemRow(icon: ImageVector, count: Int, name: String, body: String, tag: String) {
    val description = strings().text(R.string.inventory_item, name, count, body)
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

/**
 * The wheel's segments, in order clockwise from the top, with their shell colors (no new UI color); the streak freeze's
 * `felt` slice carries a bone cross, the freeze mark (#102, founder pick 2026-10-22).
 */
private val SEGMENTS = listOf(
    WheelPrize.FILLS_5 to GemColors.Patina,
    WheelPrize.FILLS_10 to GemColors.PatinaText,
    WheelPrize.PHOTO_CANVAS to GemColors.Bone,
    WheelPrize.UNLOCK_TOKEN to GemColors.TextMuted,
    WheelPrize.STREAK_FREEZE to GemColors.Felt,
)

/**
 * "5 fills", "1 photo canvas", "1 unlock token", "Streak freeze"; the freeze slice reads "5 fills" while
 * [freezesFull] (#102, E7: at 2 held it pays fills).
 */
internal fun WheelPrize.label(s: Strings, freezesFull: Boolean = false): String = when (item) {
    Item.FILLS -> s.plural(R.plurals.prize_fills, amount, amount)
    Item.PHOTO_CANVAS -> s.plural(R.plurals.prize_photo_canvases, amount, amount)
    Item.UNLOCK_TOKEN -> s.plural(R.plurals.prize_unlock_tokens, amount, amount)
    Item.STREAK_FREEZE -> if (freezesFull) s.plural(R.plurals.prize_fills, Inventory.FILLS_PER_VIDEO, Inventory.FILLS_PER_VIDEO) else s.text(R.string.prize_streak_freeze)
}

/** "5 fills added to your inventory." / "A streak freeze added to your inventory." */
internal fun Spin.result(s: Strings): String = when (item) {
    Item.STREAK_FREEZE -> s.text(R.string.prize_added_freeze)
    Item.FILLS -> s.plural(R.plurals.prize_added_fills, amount, amount)
    else -> s.text(R.string.prize_added, prize.label(s).replaceFirstChar { it.titlecase(s.locale) })
}

/** Where [prize]'s segment's middle is, in degrees clockwise from the top. */
internal fun segmentMiddle(prize: WheelPrize): Float {
    var start = 0f
    for (p in WheelPrize.entries) {
        val sweep = p.percent * 3.6f
        if (p == prize) return start + sweep / 2f
        start += sweep
    }
    error("no segment for $prize")
}

/** The segment under the pointer when the wheel is turned [rotation] degrees clockwise. */
internal fun segmentAt(rotation: Float): WheelPrize {
    val at = ((-rotation % 360f) + 360f) % 360f
    var start = 0f
    for (p in WheelPrize.entries) {
        start += p.percent * 3.6f
        if (at < start) return p
    }
    return WheelPrize.entries.last()
}

/**
 * The 5-day gift wheel (#38, founder pick W1): five flat segments sized by their odds (#102 added the streak freeze;
 * it reads "5 fills" while [freezesFull]), with the odds in words below.
 * Spin rolls and adds the prize at once (a kill mid-spin can't spin again), then the wheel turns 5 times and lands on
 * it over 3.2s, easing out, with a tick as each segment passes the pointer and the milestone chime on landing. Under
 * reduced motion it doesn't turn: the result appears with the chime.
 */
@Composable
fun WheelSheet(spinsWaiting: Int, freezesFull: Boolean, onSpin: suspend () -> Spin?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val feedback = remember { Feedback(context, (context.applicationContext as GemmoryApp).settings) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf<Spin?>(null) }
    var left by remember { mutableIntStateOf(spinsWaiting) }
    val label = remember { FreezeLabel(freezesFull) }
    val latestFull by rememberUpdatedState(freezesFull)
    LaunchedEffect(freezesFull) { label.held(freezesFull) }
    val full = label.full

    LaunchedEffect(Unit) {
        var last = segmentAt(rotation.value)
        snapshotFlow { segmentAt(rotation.value) }.collect {
            if (it != last && spinning) feedback.gemPlaced(view)
            last = it
        }
    }

    val strings = strings()
    val wheelDescription = strings.text(R.string.wheel_description, odds(strings, full))
    GemSheet(stringResource(R.string.wheel_title), onDismiss, returnFocusTo = INVENTORY_TAG) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Wheel(
                rotation.value,
                full,
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
            for ((prize, color) in SEGMENTS) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Text(" " + strings.text(R.string.wheel_odd, prize.label(strings, full), prize.percent), style = GemType.label, color = GemColors.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val result = won?.takeIf { !spinning }
        Text(
            result?.result(strings) ?: "",
            style = GemType.body,
            color = GemColors.Bone,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wheel-result")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(12.dp))
        if (left > 0 || spinning) {
            PrimaryButton(
                stringResource(R.string.wheel_spin),
                {
                    if (spinning) return@PrimaryButton
                    label.spinStarted(latestFull)
                    spinning = true
                    scope.launch {
                        val spin = onSpin()
                        if (spin == null) {
                            spinning = false
                            label.spinEnded(latestFull)
                            left = 0
                            return@launch
                        }
                        left--
                        won = spin
                        val prize = spin.prize
                        val from = rotation.value % 360f
                        rotation.snapTo(from)
                        val target = from - from % 360f + TURNS * 360f + (360f - segmentMiddle(prize))
                        if (reducedMotion) {
                            rotation.snapTo(target)
                        } else {
                            withFullMotion { rotation.animateTo(target, tween(SPIN_MS, easing = SPIN_EASING)) }
                        }
                        spinning = false
                        label.spinEnded(latestFull)
                        feedback.milestone(view)
                    }
                },
                Modifier.fillMaxWidth().testTag("wheel-spin"),
                held = spinning,
            )
        } else {
            QuietButton(stringResource(R.string.action_done), onDismiss, Modifier.fillMaxWidth().testTag("wheel-done"))
        }
    }
}

/**
 * Whether the freeze slice reads "5 fills" (#102, E7). Taken when a spin is tapped and again when it lands, never in
 * between: the prize is added before the wheel turns, so a freeze won at 1 held would otherwise relabel the slice the
 * wheel is landing on (design system: "its label changes only between spins").
 */
internal class FreezeLabel(full: Boolean) {
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

internal fun odds(s: Strings, freezesFull: Boolean = false): String =
    WheelPrize.entries.joinToString(s.text(R.string.list_separator)) { s.text(R.string.wheel_odd, it.label(s, freezesFull), it.percent) }

@Composable
private fun Wheel(rotation: Float, freezesFull: Boolean, modifier: Modifier) {
    val deep = LocalFelt.current.deep
    Canvas(modifier.rotate(rotation)) {
        var start = -90f
        for ((prize, color) in SEGMENTS) {
            val sweep = prize.percent * 3.6f
            drawArc(color, start, sweep, useCenter = true)
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
    val radians = Math.toRadians(angle.toDouble())
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
