package com.brewingbytes.gemmory.streak

import android.app.Application
import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.CellKind
import com.brewingbytes.gemmory.policy.HistoryDay
import com.brewingbytes.gemmory.policy.StreakCalendar
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.ui.AppTopBar
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemIcons
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.IconAction
import com.brewingbytes.gemmory.ui.LocalFelt
import com.brewingbytes.gemmory.ui.QuietButton
import com.brewingbytes.gemmory.ui.feltGround
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

/** The streak screen's state (#101): the streak as it stands, the best, the log and the month showing. */
data class StreakUi(
    val snapshot: StreakSnapshot,
    val best: Int,
    val history: List<HistoryDay>,
    val month: LongRange,
    val firstMonth: LongRange,
    /** Canvas key to title, for TalkBack on finished days. */
    val titles: Map<String, String> = emptyMap(),
)

/** Reads the streak screen's state and its day thumbnails (#101); one month at a time, thumbnails off the main thread. */
class StreakViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GemmoryApp
    private val _ui = MutableStateFlow(read(null))
    val ui: StateFlow<StreakUi> = _ui.asStateFlow()

    private val thumbs = LruCache<String, ImageBitmap>(THUMBS)

    /** Opened from [source]: re-read as of now, log it ([log]: not again after a rotation), and fetch the titles. */
    fun opened(source: String, log: Boolean) {
        _ui.value = read(_ui.value.month.takeIf { !log })
        if (log) app.analytics.log(AnalyticsEvent.streakScreenOpened(source))
        viewModelScope.launch {
            val (pictures, photos) = app.store.summaries()
            val titles = (pictures + photos).filterIsInstance<CanvasSummary.Playable>().associate { CanvasStore.reminderKey(it.ref) to it.title }
            _ui.update { it.copy(titles = titles) }
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
        return StreakUi(snapshot, best, history, month ?: StreakCalendar.monthOf(snapshot.today), StreakCalendar.firstMonth(history, snapshot.today))
    }

    private companion object {
        const val THUMBS = 42
    }
}

const val STREAK_SCREEN_TAG = "streak-screen"
const val STREAK_TODAYS_PICTURE_TAG = "streak-todays-picture"

/**
 * "Your streak" (#101, the streak screen copy and spec tables): the flame and the number with the best, today's
 * status, the weekly covers (day off; VIP freeze for VIP), the next gift (not VIP), then the month calendar. [onToday]
 * opens Today's picture, null when there is none to finish.
 */
@Composable
fun StreakScreen(vm: StreakViewModel, onBack: () -> Unit, onToday: (() -> Unit)?) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.snapshot
    val shown = s.shown
    val vip = s.cover.vip
    Column(Modifier.fillMaxSize().feltGround(LocalFelt.current.ground).windowInsetsPadding(WindowInsets.safeDrawing).testTag(STREAK_SCREEN_TAG)) {
        AppTopBar("Your streak", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Hero(shown, ui.best, s.doneToday)
                Section {
                    val status = StreakText.todayStatus(shown, s.doneToday)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        if (s.doneToday) {
                            Flame(lit = true, length = shown, size = 18.dp, still = true)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column {
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
                        if (vip) {
                            Spacer(Modifier.height(8.dp))
                            Weekly("VIP freeze", s.state.vipFreezeDay, s.today, CellKind.VIP_FREEZE, shown)
                        }
                    }
                }
                if (!vip) Section { NextGift(shown, tag = "streak-next-gift") }
                Section { Calendar(ui, vm) }
            }
        }
    }
}

@Composable
private fun Hero(shown: Int, best: Int, lit: Boolean) {
    val big = LocalDensity.current.fontScale > 1.5f
    val text = buildString {
        append("${StreakText.days(shown)} in a row")
        if (best > 0) append(". Best: ${StreakText.days(best)}")
    }
    val number: @Composable () -> Unit = {
        Text("$shown", style = GemType.streakHero.copy(fontSize = 56.sp, lineHeight = 60.sp), color = GemColors.Bone)
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
            Column { number(); words() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { number(); words() }
        }
    }
}

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
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
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

