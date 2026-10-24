package com.brewingbytes.gemmory.streak

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.play.PendingCelebration
import com.brewingbytes.gemmory.policy.BrokenStreak
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.StreakCalendar
import com.brewingbytes.gemmory.policy.StreakShareCard
import com.brewingbytes.gemmory.storage.AtomicFiles
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeManager
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * What the celebration shows, read once when it opens, so a rotation shows the same and replays nothing. [repairable]:
 * the streak that ended can still be brought back (#102), so the text button says so.
 */
class StreakDayData(
    val celebration: PendingCelebration,
    val week: List<CalendarCell>,
    val vip: Boolean,
    val text: CelebrationText,
    val repairable: BrokenStreak? = null,
)

/**
 * The celebration's state (#101, E2): today's pending celebration, taken from the streak once (so it shows once), and
 * whether its sound and analytics already ran. Activity-scoped, so a rotation keeps it; after process death it's gone,
 * and the route closes to the gallery.
 */
class StreakDayViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GemmoryApp
    private var data: StreakDayData? = null
    private var shownFor: PendingCelebration? = null

    /** The celebration to show: the one taken already, else today's pending one; null when there is none. */
    fun take(): StreakDayData? {
        data?.let { return it }
        val celebration = app.streak.takeCelebration() ?: return null
        val snapshot = app.streakView.refresh()
        val start = StreakCalendar.weekStart(snapshot.today, firstDayOfWeek())
        val week = StreakCalendar.cells(start..start + 6, app.streak.history(), snapshot.state, snapshot.cover, snapshot.today)
        val vip = snapshot.cover.vip
        val text = StreakText(app.strings).celebration(celebration.length, celebration.covered, vip, snapshot.cover.freezes)
        // A new streak's first day while the old one can still come back (#102, "Restart with repair open").
        val repairable = app.streak.repairOffer(snapshot.cover)?.takeIf { celebration.length == 1 }
        return StreakDayData(celebration, week, vip, text, repairable).also { data = it }
    }

    /** Logs `streak_celebration_shown` once per celebration; true the first time, when the sound plays. */
    fun shown(celebration: PendingCelebration): Boolean {
        if (shownFor == celebration) return false
        shownFor = celebration
        val milestone = if (StreakText.isMilestone(celebration.length)) celebration.length else 0
        app.analytics.log(AnalyticsEvent.streakCelebrationShown(celebration.length, milestone, StreakText.savedBy(celebration.covered)))
        return true
    }

    /** Closed: the next new day takes a new one; a card still being made is dropped. */
    fun done() {
        data = null
        sharing?.cancel()
        _card.value = null
    }

    private var sharing: Job? = null
    private val _card = MutableStateFlow<File?>(null)

    /** The milestone share card made by [share], until the share sheet opens with it ([shared]). */
    val card: StateFlow<File?> = _card.asStateFlow()

    /**
     * Makes the milestone share card (#104) in this ViewModel's scope, so a rotation keeps it; it lands in [card]. A
     * second tap while one is being made does nothing, nor does a day that isn't a milestone.
     */
    fun share() {
        if (sharing?.isActive == true) return
        sharing = viewModelScope.launch { shareCard()?.let { _card.value = it } }
    }

    /** The share sheet opened with [card]. */
    fun shared() {
        _card.value = null
    }

    /**
     * The card as a PNG in the share cache, made off the main thread; null when this isn't a milestone or the card
     * couldn't be written.
     */
    private suspend fun shareCard(): File? {
        val data = data ?: return null
        val c = data.celebration
        val streakText = StreakText(app.strings)
        val title = streakText.milestone(c.length) ?: return null
        val (pictures, photos) = app.store.summaries()
        val playable = (pictures + photos).filterIsInstance<CanvasSummary.Playable>()
        val finishes = playable.mapNotNull { s -> s.firstFinishedDay?.let { it to CanvasStore.reminderKey(s.ref) } }.groupBy({ it.first }, { it.second })
        // Bundled and remote pictures only: a photo canvas's grid is never handed to the renderer.
        val patterns = playable.filter { it.ref.kind != CanvasKind.PHOTO }.associate { CanvasStore.reminderKey(it.ref) to it.state.pattern }
        val days = StreakShareCard.days(data.week, c.day, finishes, CardPictures::isPicture)
        val letters = data.week.map { streakText.weekday(it.day).take(1) }
        return withContext(Dispatchers.Default) {
            val card = StreakShareCardRenderer(app).render(c.length, title, days, letters) { ref ->
                patterns[CanvasStore.reminderKey(ref)]?.let { CardPictures.art(ref, it) }
            }
            val png = ByteArrayOutputStream().also { card.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            card.recycle()
            val file = File(File(app.cacheDir, EncodeManager.DIR).apply { mkdirs() }, "streak-${c.length}.png")
            withContext(Dispatchers.IO) { runCatching { AtomicFiles().write(file, png) } }.map { file }.getOrNull()
        }
    }
}

/** The locale's first day of the week (G12), for the week row and the calendar. */
fun firstDayOfWeek(): kotlinx.datetime.DayOfWeek =
    kotlinx.datetime.DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.ordinal]

const val CELEBRATION_TAG = "streak-celebration"
const val CELEBRATION_CONTINUE_TAG = "streak-celebration-continue"
const val CELEBRATION_SEE_TAG = "streak-celebration-see"
const val CELEBRATION_SHARE_TAG = "streak-celebration-share"

/**
 * The new streak day (#101): full screen on `felt-deep-dark` after the completion screen. The flame ignites, the number
 * rolls up, the week row fills, then the copy table's words. Milestones (7/30/50/100/365) get the bigger flame and the
 * lamp edge once. Continue (and back) closes it to the gallery; "See your streak" opens the streak screen, and on
 * milestones "Share" shares the card (#104) through the share sheet, back to here. Reduced
 * motion: all at once; sound and haptic stay unless Milestones are off (G15).
 */
@Composable
fun StreakCelebration(
    data: StreakDayData,
    vm: StreakDayViewModel,
    onContinue: () -> Unit,
    onSeeStreak: (repair: Boolean) -> Unit,
    onShare: (card: File, milestone: Int) -> Unit,
) {
    val c = data.celebration
    val milestone = StreakText.isMilestone(c.length)
    val description = stringResource(R.string.streak_celebration_words, data.text.title, data.text.line)
    val reduced = rememberReducedMotion()
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as GemmoryApp
    val feedback = remember { Feedback(context, app.settings) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    val first = remember(c) { vm.shown(c) }
    val number = remember { Animatable(if (first && !reduced) 0f else 1f) }
    val edge = remember { Animatable(0f) }
    LaunchedEffect(c) {
        if (!first) return@LaunchedEffect
        if (app.settings.milestones.value) {
            if (milestone) feedback.pictureFinished() else feedback.milestone(view)
        }
        if (reduced) return@LaunchedEffect
        delay(ROLL_DELAY_MS)
        number.animateTo(1f, tween(ROLL_MS, easing = FastOutSlowInEasing))
        if (milestone) {
            withFullMotion {
                edge.animateTo(0.9f, tween(EDGE_MS * 4 / 10))
                edge.animateTo(0f, tween(EDGE_MS * 6 / 10))
            }
        }
    }
    BackHandler(onBack = onContinue)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(GemColors.FeltDeepDark)
            .drawWithContent {
                drawContent()
                if (edge.value > 0f) {
                    val w = EDGE.toPx()
                    drawRect(GemColors.Lamp.copy(alpha = edge.value), Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                }
            }
            .testTag(CELEBRATION_TAG),
    ) {
        val bigFont = LocalDensity.current.fontScale > 1.5f
        val tall = maxHeight > 600.dp
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The moment scrolls at 200% font; the buttons stay at the bottom.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = if (tall) 48.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(112.dp), contentAlignment = Alignment.BottomCenter) {
                        Flame(lit = true, length = c.length, size = if (milestone) 112.dp else 96.dp, ignite = first)
                    }
                    RollingNumber(c.length, number.value, if (bigFont) 64 else 88)
                    Text(data.text.title, style = GemType.display, color = GemColors.Bone, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        data.text.line,
                        style = GemType.body,
                        color = GemColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                WeekRow(data.week, c.length, Modifier.widthIn(max = 480.dp).padding(top = 22.dp))
                data.text.gift?.let {
                    Text(it, style = GemType.body, color = GemColors.Bone, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton(stringResource(R.string.action_continue), onContinue, Modifier.padding(top = 8.dp).widthIn(max = 480.dp).fillMaxWidth().testTag(CELEBRATION_CONTINUE_TAG))
            // Milestones share the card (#104, G10); other days open the streak screen. With a repair open (#102), it
            // says the old streak can still come back.
            val repairable = data.repairable
            if (milestone) {
                // The share sheet opens once the card is made and the app is in front (it may finish in the background).
                val card by vm.card.collectAsStateWithLifecycle()
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(card) {
                    val file = card ?: return@LaunchedEffect
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    onShare(file, c.length)
                    vm.shared()
                }
                GemTextButton(
                    stringResource(R.string.action_share),
                    vm::share,
                    Modifier.padding(bottom = 8.dp).testTag(CELEBRATION_SHARE_TAG),
                )
            } else {
                GemTextButton(
                    repairable?.let { StreakText(strings()).stillRestorable(it.length) } ?: stringResource(R.string.nudge_broken_hint),
                    { onSeeStreak(repairable != null) },
                    Modifier.padding(bottom = 8.dp).testTag(CELEBRATION_SEE_TAG),
                )
            }
        }
        // One polite announcement (accessibility table); the roll-up isn't read digit by digit.
        Box(Modifier.size(1.dp).semantics { contentDescription = data.text.announcement; liveRegion = LiveRegionMode.Polite })
    }
}

/** The streak-hero number (G8) rolling from the day before to [length] as [progress] goes 0 to 1. */
@Composable
private fun RollingNumber(length: Int, progress: Float, sizeSp: Int) {
    val style = GemType.streakHero.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp + 4).sp)
    val height = (sizeSp + 4).dp
    Box(Modifier.height(height).clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val shift = progress * height.value
        Text("${length - 1}", style = style, color = GemColors.Bone, modifier = Modifier.graphicsLayer { translationY = -shift.dp.toPx() })
        Text("$length", style = style, color = GemColors.Bone, modifier = Modifier.graphicsLayer { translationY = (height.value - shift).dp.toPx() })
    }
}

/** This week, Monday-Sunday or the locale's (G12): the day's flame or cover mark; today outlined in lamp. One node. */
@Composable
fun WeekRow(week: List<CalendarCell>, length: Int, modifier: Modifier = Modifier) {
    val text = StreakText(strings())
    val description = text.week(week)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cell in week) {
                Text(
                    text.weekday(cell.day).take(1),
                    style = GemType.label.copy(fontSize = 12.sp),
                    color = GemColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cell in week) {
                val shape = RoundedCornerShape(2.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(GemColors.FeltDeep)
                        .then(if (cell.today) Modifier.border(2.dp, GemColors.Lamp, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { DayMark(cell, length, pictures = false) }
            }
        }
    }
}

private val EDGE = 2.dp
private const val ROLL_DELAY_MS = 500L
private const val ROLL_MS = 600
private const val EDGE_MS = 1_400
