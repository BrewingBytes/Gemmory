package com.brewingbytes.gemmory.streak

import android.app.Application
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.play.Feedback
import com.brewingbytes.gemmory.play.PendingCelebration
import com.brewingbytes.gemmory.policy.BrokenStreak
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.StreakCalendar
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemTextButton
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.PrimaryButton
import com.brewingbytes.gemmory.ui.rememberReducedMotion
import com.brewingbytes.gemmory.ui.withFullMotion
import kotlinx.coroutines.delay
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
        val text = StreakText.celebration(celebration.length, celebration.covered, vip, snapshot.cover.freezes)
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

    /** Closed: the next new day takes a new one. */
    fun done() {
        data = null
    }
}

/** The locale's first day of the week (G12), for the week row and the calendar. */
fun firstDayOfWeek(): kotlinx.datetime.DayOfWeek =
    kotlinx.datetime.DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.ordinal]

const val CELEBRATION_TAG = "streak-celebration"
const val CELEBRATION_CONTINUE_TAG = "streak-celebration-continue"
const val CELEBRATION_SEE_TAG = "streak-celebration-see"

/**
 * The new streak day (#101): full screen on `felt-deep-dark` after the completion screen. The flame ignites, the number
 * rolls up, the week row fills, then the copy table's words. Milestones (7/30/50/100/365) get the bigger flame and the
 * lamp edge once. Continue (and back) closes it to the gallery; "See your streak" opens the streak screen. Reduced
 * motion: all at once; sound and haptic stay unless Milestones are off (G15).
 */
@Composable
fun StreakCelebration(data: StreakDayData, vm: StreakDayViewModel, onContinue: () -> Unit, onSeeStreak: (repair: Boolean) -> Unit) {
    val c = data.celebration
    val milestone = StreakText.isMilestone(c.length)
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
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = "${data.text.title}. ${data.text.line}" },
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
            PrimaryButton("Continue", onContinue, Modifier.padding(top = 8.dp).widthIn(max = 480.dp).fillMaxWidth().testTag(CELEBRATION_CONTINUE_TAG))
            // Share on milestones arrives with the share card (#104, G10); until then, the streak screen. With a repair
            // open (#102), it says the old streak can still come back.
            val repairable = data.repairable
            GemTextButton(
                repairable?.let { StreakText.stillRestorable(it.length) } ?: "See your streak",
                { onSeeStreak(repairable != null) },
                Modifier.padding(bottom = 8.dp).testTag(CELEBRATION_SEE_TAG),
            )
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
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = StreakText.week(week) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (cell in week) {
                Text(
                    StreakText.weekday(cell.day).take(1),
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
