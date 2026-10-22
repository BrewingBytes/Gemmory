package com.brewingbytes.gemmory.streak

import android.app.Application
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.ads.RewardPhase
import com.brewingbytes.gemmory.ads.RewardedUi
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.play.RepairHow
import com.brewingbytes.gemmory.policy.BrokenStreak
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.CellKind
import com.brewingbytes.gemmory.policy.DailyStreak
import com.brewingbytes.gemmory.policy.HistoryDay
import com.brewingbytes.gemmory.policy.StreakCalendar
import com.brewingbytes.gemmory.policy.Unavailable
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The streak screen's state (#101): the streak as it stands, the best, the log and the month showing. */
data class StreakUi(
    val snapshot: StreakSnapshot,
    val best: Int,
    val history: List<HistoryDay>,
    val month: LongRange,
    val firstMonth: LongRange,
    /** Canvas key to title, for TalkBack on finished days. */
    val titles: Map<String, String> = emptyMap(),
    /** The broken streak that can still be brought back (#102), with VIP's weekly repair. */
    val repair: RepairOffer? = null,
    /** Brought back just now (#102): the number rolls up and "Streak back" shows until the screen closes. */
    val restored: Restored? = null,
)

/** A repair offer (#102): [broken] can be repaired [until] that day; for VIP, its weekly repair [vipReady] or last used. */
data class RepairOffer(val broken: BrokenStreak, val until: Long, val vip: Boolean, val vipReady: Boolean, val lastVipRepairDay: Long)

/**
 * A repair just made: the streak is back at [length], with the missed [days] repaired. [rollUp]: the number's roll-up
 * and its announcement are still to come; they run once, never again after a rotation.
 */
data class Restored(val length: Int, val days: List<Long>, val rollUp: Boolean = true)

/** Reads the streak screen's state and its day thumbnails (#101); one month at a time, thumbnails off the main thread. */
class StreakViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GemmoryApp
    private val _ui = MutableStateFlow(read(null))
    val ui: StateFlow<StreakUi> = _ui.asStateFlow()

    private val thumbs = LruCache<String, ImageBitmap>(THUMBS)

    /** Opened from [source]: re-read as of now, log it ([log]: not again after a rotation), and fetch the titles. */
    fun opened(source: String, log: Boolean) {
        // A rotation keeps "Streak back" but never replays its roll-up (the celebration's rule: it replays nothing).
        _ui.value = read(_ui.value.month.takeIf { !log }).copy(restored = _ui.value.restored.takeIf { !log }?.copy(rollUp = false))
        if (log) app.analytics.log(AnalyticsEvent.streakScreenOpened(source))
        if (log) _ui.value.repair?.let { app.analytics.log(AnalyticsEvent.streakRepairOffered(it.broken.length)) }
        viewModelScope.launch {
            val (pictures, photos) = app.store.summaries()
            val titles = (pictures + photos).filterIsInstance<CanvasSummary.Playable>().associate { CanvasStore.reminderKey(it.ref) to it.title }
            _ui.update { it.copy(titles = titles) }
        }
    }

    /** A repair was made (#102): re-read, and roll the number up to [length]. */
    fun restored(length: Int, days: List<Long>) {
        _ui.value = read(_ui.value.month).copy(restored = Restored(length, days))
    }

    /** The restored number has rolled up (or showed at once): not again. */
    fun rolledUp() = _ui.update { it.copy(restored = it.restored?.copy(rollUp = false)) }

    /** VIP's weekly repair (#102): "Bring it back", no video. */
    fun vipRepair() {
        viewModelScope.launch {
            val result = app.streak.repair(RepairHow.VIP) ?: return@launch
            restored(result.length, result.covered.map { it.day })
        }
    }

    fun previousMonth() = _ui.update { ui -> ui.copy(month = StreakCalendar.monthOf(ui.month.first - 1)).takeIf { ui.month.first > ui.firstMonth.first } ?: ui }

    fun nextMonth() = _ui.update { ui ->
        ui.copy(month = StreakCalendar.monthOf(ui.month.last + 1)).takeIf { ui.month.last < ui.snapshot.today } ?: ui
    }

    /** The first picture finished on a day, from its saved thumbnail; null when the canvas is gone. */
    suspend fun thumbnail(canvas: String): ImageBitmap? {
        thumbs.get(canvas)?.let { return it }
        val ref = CanvasStore.refOfReminderKey(canvas) ?: return null
        val image = withContext(Dispatchers.IO) {
            app.store.repository.thumbFile(ref).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()
        } ?: return null
        thumbs.put(canvas, image)
        return image
    }

    private fun read(month: LongRange?): StreakUi {
        val snapshot = app.streakView.refresh()
        val history = app.streak.history()
        val best = maxOf(app.achievements.state.bestStreak, snapshot.shown)
        val repair = app.streak.repairOffer(snapshot.cover)?.let { broken ->
            val vip = snapshot.cover.vip
            RepairOffer(broken, DailyStreak.repairUntil(broken), vip, vip && app.streak.vipRepairReady(), app.streak.lastVipRepairDay())
        }
        return StreakUi(snapshot, best, history, month ?: StreakCalendar.monthOf(snapshot.today), StreakCalendar.firstMonth(history, snapshot.today), repair = repair)
    }

    private companion object {
        const val THUMBS = 42
    }
}

const val STREAK_SCREEN_TAG = "streak-screen"
const val STREAK_TODAYS_PICTURE_TAG = "streak-todays-picture"
const val STREAK_FREEZES_TAG = "streak-freezes"
const val STREAK_REPAIR_TAG = "streak-repair"
const val STREAK_REPAIR_PRIMARY_TAG = "streak-repair-primary"
const val STREAK_REPAIR_LINE_TAG = "streak-repair-line"
const val STREAK_RESTORED_TAG = "streak-restored"

/** What the repair panel does (#102): the video (one tap, and Try again), the grace, and VIP's free repair. */
class RepairActions(val onWatch: () -> Unit, val onGrace: () -> Unit, val onVipRepair: () -> Unit)

/**
 * "Your streak" (#101, the streak screen copy and spec tables): while a broken streak can come back, the repair panel
 * first (#102), then the flame and the number with the best, today's status, the covers (day off; VIP freeze for VIP,
 * else the streak freezes held, #102), the next gift (not VIP), then the month calendar. [onToday] opens Today's
 * picture, null when there is none to finish. [video] is the rewarded state while the repair's video is under way.
 */
@Composable
fun StreakScreen(
    vm: StreakViewModel,
    onBack: () -> Unit,
    onToday: (() -> Unit)?,
    video: RewardedUi? = null,
    repair: RepairActions = RepairActions({}, {}, {}),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.snapshot
    val restored = ui.restored
    val shown = if (restored != null) maxOf(s.shown, restored.length) else s.shown
    val vip = s.cover.vip
    Column(Modifier.fillMaxSize().feltGround(LocalFelt.current.ground).windowInsetsPadding(WindowInsets.safeDrawing).testTag(STREAK_SCREEN_TAG)) {
        AppTopBar("Your streak", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                // The panel stays while its "restored" moment shows, then goes with the offer.
                val offer = ui.repair
                if (offer != null && restored == null) RepairPanel(offer, video, repair)
                Hero(shown, ui.best, s.doneToday, rollUp = restored?.rollUp == true, onRolledUp = vm::rolledUp)
                if (restored != null) {
                    Text(
                        StreakText.restored(restored.length),
                        style = GemType.body,
                        color = GemColors.Bone,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag(STREAK_RESTORED_TAG)
                            // Announced with the roll-up only, so a rotation doesn't read it again.
                            .semantics { if (restored.rollUp) liveRegion = LiveRegionMode.Polite },
                    )
                }
                Section {
                    val status = StreakText.todayStatus(shown, s.doneToday)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        if (s.doneToday) {
                            Flame(lit = true, length = shown, size = 18.dp, still = true)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column {
                            restored?.let { r -> StreakText.repaired(r.days)?.let { Text(it, style = GemType.body, color = GemColors.Bone) } }
                            Text(status, style = GemType.body, color = GemColors.Bone)
                            StreakText.awaiting(s.awaiting, s.today)?.let { Text(it, style = GemType.label, color = GemColors.TextMuted) }
                        }
                    }
                    if (!s.doneToday && onToday != null) {
                        Spacer(Modifier.height(8.dp))
                        QuietButton("Today's picture", onToday, Modifier.fillMaxWidth().testTag(STREAK_TODAYS_PICTURE_TAG))
                    }
                }
                if (shown > 0) {
                    Section {
                        Weekly("Day off", s.state.restDay, s.today, CellKind.DAY_OFF, shown)
                        Spacer(Modifier.height(8.dp))
                        if (vip) {
                            Weekly("VIP freeze", s.state.vipFreezeDay, s.today, CellKind.VIP_FREEZE, shown)
                        } else {
                            // VIP never spends held freezes (O2), so only players without it see them (#102).
                            val (line, sub) = StreakText.freezes(s.cover.freezes)
                            SafetyRow(line, sub, CellKind.FREEZE, s.today, shown, Modifier.testTag(STREAK_FREEZES_TAG))
                        }
                    }
                }
                if (!vip) Section { NextGift(shown, tag = "streak-next-gift") }
                Section { Calendar(ui, vm) }
            }
        }
    }
}

/**
 * The repair panel (#102, mockup streak-b section 3): the rewarded sheet's states drawn inline, with the repair's copy.
 * VIP's weekly repair needs no video; once used, VIP sees the video panel with when it's ready again.
 */
@Composable
private fun RepairPanel(offer: RepairOffer, video: RewardedUi?, actions: RepairActions) {
    val phase = video?.phase
    val vipFree = offer.vipReady && video == null
    Column(Modifier.testTag(STREAK_REPAIR_TAG)) {
        Section {
            Text(StreakText.repairTitle(offer.broken.length), style = GemType.display, color = GemColors.Bone, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            val (line, fault) = when ((phase as? RewardPhase.NotAvailable)?.kind) {
                Unavailable.OFFLINE -> "You're offline. Connect to watch a video." to true
                Unavailable.NO_VIDEO -> "No video right now. Try again in a bit." to false
                Unavailable.GRACE_OFFER -> "No video right now. This one's on us today." to false
                null -> StreakText.repairBody(offer.broken.endedOn, vipFree) to false
            }
            Text(
                line,
                style = GemType.body,
                // `fault` is offline wording only, valid on felt-deep (design system, valid grounds).
                color = if (fault) GemColors.Fault else GemColors.Bone,
                modifier = Modifier.testTag(STREAK_REPAIR_LINE_TAG),
            )
            Spacer(Modifier.height(12.dp))
            val primary = Modifier.fillMaxWidth().testTag(STREAK_REPAIR_PRIMARY_TAG)
            when {
                vipFree -> PrimaryButton("Bring it back", actions.onVipRepair, primary)
                phase == RewardPhase.Loading -> {
                    PrimaryButton("Loading video…", {}, primary, enabled = false)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp).clearAndSetSemantics {},
                        color = GemColors.Bone,
                        trackColor = Color.Transparent,
                    )
                }
                phase is RewardPhase.NotAvailable && phase.kind == Unavailable.GRACE_OFFER -> PrimaryButton("Bring it back", actions.onGrace, primary)
                phase is RewardPhase.NotAvailable -> QuietButton("Try again", actions.onWatch, primary)
                else -> PrimaryButton("Watch a video", actions.onWatch, primary, held = phase == RewardPhase.Showing || phase == RewardPhase.Unlocked)
            }
            if (video?.closedEarly == true && (phase == RewardPhase.Loading || phase == RewardPhase.Ready)) {
                Muted("Watch to the end to bring it back.", Modifier.testTag("streak-repair-closed-early"))
            }
            if (offer.vip && !offer.vipReady) Muted(StreakText.vipRepairUsed(offer.lastVipRepairDay))
            Muted(StreakText.repairUntil(offer.until))
        }
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = GemType.label, color = GemColors.TextMuted, modifier = modifier.padding(top = 8.dp))
}

/**
 * [rollUp]: a repair just brought the streak back, so the number rolls 0 → [shown] over 600 ms once (#102; shown at
 * once under reduced motion), then [onRolledUp]. It starts at 0, so the first frame never shows the final number.
 */
@Composable
private fun Hero(shown: Int, best: Int, lit: Boolean, rollUp: Boolean = false, onRolledUp: () -> Unit = {}) {
    val reduced = rememberReducedMotion()
    val roll = remember { Animatable(if (rollUp && !reduced) 0f else 1f) }
    LaunchedEffect(rollUp) {
        if (!rollUp) return@LaunchedEffect
        if (reduced) roll.snapTo(1f) else roll.animateTo(1f, tween(ROLL_UP_MS, easing = FastOutSlowInEasing))
        onRolledUp()
    }
    val number = (shown * roll.value).roundToInt()
    val big = LocalDensity.current.fontScale > 1.5f
    val text = buildString {
        append("${StreakText.days(shown)} in a row")
        if (best > 0) append(". Best: ${StreakText.days(best)}")
    }
    val digits: @Composable () -> Unit = {
        Text("$number", style = GemType.streakHero.copy(fontSize = 56.sp, lineHeight = 60.sp), color = GemColors.Bone)
    }
    val words: @Composable () -> Unit = {
        Column {
            Text(if (shown == 1) "day in a row" else "days in a row", style = GemType.display, color = GemColors.Bone)
            if (best > 0) {
                Text(
                    buildBest(best),
                    style = GemType.label,
                    color = GemColors.Bone,
                )
            }
        }
    }
    // At 200% the hero stacks: the flame above the number (accessibility table).
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).clearAndSetSemantics { contentDescription = text; heading() },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Flame(lit = lit && shown > 0, length = shown, size = 72.dp)
        if (big) {
            Column { digits(); words() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { digits(); words() }
        }
    }
}

/** The restored number's roll-up (#102, mockup section 3). */
private const val ROLL_UP_MS = 600

private fun buildBest(best: Int) = buildAnnotatedString {
    append("Best: ")
    pushStyle(GemType.mono.toSpanStyle())
    append("$best")
    pop()
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LocalFelt.current.deep)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { content() }
}

/** One weekly cover's row (the safety rows): its mark, "Day off ready" or when it was used and is ready again. */
@Composable
private fun Weekly(name: String, lastUsed: Long, today: Long, mark: CellKind, length: Int) {
    val (line, sub) = StreakText.weekly(name, lastUsed, today)
    SafetyRow(line, sub, mark, today, length)
}

/** A safety row: the cover's mark, its line and an optional muted line under it; one TalkBack node. */
@Composable
private fun SafetyRow(line: String, sub: String?, mark: CellKind, today: Long, length: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp)) { DayMark(CalendarCell(today, mark), length, pictures = false) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(line, style = GemType.body, color = GemColors.Bone)
            sub?.let { Text(it, style = GemType.label, color = GemColors.TextMuted) }
        }
    }
}

/** The month: back to the first month with history, never ahead of this one (cell table, G16: not tappable). */
@Composable
private fun Calendar(ui: StreakUi, vm: StreakViewModel) {
    val s = ui.snapshot
    val first = LocalDate.ofEpochDay(ui.month.first)
    val title = "${first.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${first.year}"
    val cells = StreakCalendar.cells(ui.month, ui.history, s.state, s.cover, s.today)
    val lead = ((first.dayOfWeek.value - 1) - firstDayOfWeek().ordinal + 7) % 7
    val canBack = ui.month.first > ui.firstMonth.first
    val canForward = ui.month.last < s.today
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alpha(if (canBack) 1f else 0.35f)) { IconAction(GemIcons.PreviousMonth, "Previous month", { if (canBack) vm.previousMonth() }) }
            Text(title, style = GemType.button, color = GemColors.Bone, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
            Box(Modifier.alpha(if (canForward) 1f else 0.35f)) { IconAction(GemIcons.NextMonth, "Next month", { if (canForward) vm.nextMonth() }) }
        }
        val start = StreakCalendar.weekStart(ui.month.first, firstDayOfWeek())
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics { }) {
            for (d in 0 until 7) {
                Text(
                    StreakText.weekday(start + d).take(1),
                    style = GemType.label.copy(fontSize = 12.sp),
                    color = GemColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val slots = List(lead) { null } + cells
        for (week in slots.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                for (i in 0 until 7) {
                    val cell = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) { if (cell != null) CalendarDay(cell, s.shown, ui.titles, s.today, vm) }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(cell: CalendarCell, length: Int, titles: Map<String, String>, today: Long, vm: StreakViewModel) {
    val shape = RoundedCornerShape(2.dp)
    val past = cell.kind != CellKind.FUTURE
    val what = when (cell.kind) {
        CellKind.FINISHED -> cell.canvas?.let(titles::get)?.let { "finished $it" } ?: "done"
        else -> StreakText.what(cell.kind) ?: if (cell.today) "not finished yet" else null
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .then(if (past) Modifier.background(GemColors.Felt) else Modifier)
            .then(if (cell.today) Modifier.border(BorderStroke(2.dp, GemColors.Lamp), shape) else Modifier)
            .clearAndSetSemantics { contentDescription = StreakText.cell(cell.day, today, what) },
        contentAlignment = Alignment.Center,
    ) {
        val canvas = cell.canvas
        val picture = if (cell.kind == CellKind.FINISHED && canvas != null) {
            produceState<ImageBitmap?>(null, canvas) { value = vm.thumbnail(canvas) }.value
        } else {
            null
        }
        when {
            picture != null -> Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
            // A picture cell has no date (it would cover the picture at 200%); every other day keeps it.
            cell.kind == CellKind.EMPTY || cell.kind == CellKind.FUTURE ->
                Text("${LocalDate.ofEpochDay(cell.day).dayOfMonth}", style = GemType.mono.copy(fontSize = 11.sp), color = GemColors.TextMuted)
            else -> DayMark(cell, length, pictures = true)
        }
    }
}

/**
 * How a day counted, drawn in its cell (cell table): a still lit flame for a finished or kept day (a finished day's
 * picture goes in the calendar instead), a bone outline for the day off, patina with a cross for a freeze, bone with a
 * dashed stitch for a repaired day, a dashed outline for a day awaiting cover. Empty days draw nothing.
 */
@Composable
fun DayMark(cell: CalendarCell, length: Int, pictures: Boolean) {
    when (cell.kind) {
        CellKind.FINISHED, CellKind.KEPT -> Flame(lit = true, length = length, size = if (pictures) 20.dp else 22.dp, still = true)
        CellKind.DAY_OFF, CellKind.VIP_FREEZE, CellKind.FREEZE, CellKind.REPAIRED, CellKind.AWAITING -> MarkSquare(cell.kind)
        CellKind.EMPTY, CellKind.FUTURE -> Unit
    }
}

@Composable
private fun MarkSquare(kind: CellKind) {
    val onFelt = LocalFelt.current.deep
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            CellKind.DAY_OFF -> drawRect(GemColors.Bone, Offset(1.dp.toPx(), 1.dp.toPx()), size.copy(w - 2.dp.toPx(), h - 2.dp.toPx()), style = Stroke(2.dp.toPx()))
            CellKind.VIP_FREEZE, CellKind.FREEZE -> {
                drawRect(GemColors.Patina)
                val inset = w * 0.2f
                drawLine(onFelt, Offset(w / 2, inset), Offset(w / 2, h - inset), 1.6.dp.toPx())
                drawLine(onFelt, Offset(inset, h / 2), Offset(w - inset, h / 2), 1.6.dp.toPx())
            }
            CellKind.REPAIRED -> {
                drawRect(GemColors.Bone)
                drawLine(
                    GemColors.OnBone,
                    Offset(w * 0.12f, h / 2),
                    Offset(w * 0.88f, h / 2),
                    1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            }
            CellKind.AWAITING -> drawRect(
                GemColors.TextMuted,
                Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
                size.copy(w - 1.5.dp.toPx(), h - 1.5.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
            )
            else -> Unit
        }
    }
}

