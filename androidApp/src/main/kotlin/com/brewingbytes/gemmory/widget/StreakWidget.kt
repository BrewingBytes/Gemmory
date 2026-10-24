package com.brewingbytes.gemmory.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.ui.GemColors

/**
 * The home-screen streak widget (#103, G17): one 2×2 on a fixed felt ground (never dynamic color), the days in a row
 * in mono, the flame grey until today's picture is finished and lit after, and one status line. Never a picture or a
 * photo: a home screen is public. A tap opens the streak screen over the gallery. Its state is
 * [StreakWidgets.state], read from the one StreakView (O8).
 */
class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgets = (context.applicationContext as GemmoryApp).streakWidgets
        val tap = actionStartActivity(tapIntent(context))
        provideContent {
            val state by widgets.state.collectAsState()
            StreakWidgetContent(state, tap)
        }
    }

    companion object {
        /** The smallest 2×2 a launcher gives (110dp): smaller number, flame and padding (mockup streak-c section 3). */
        val COMPACT = DpSize(110.dp, 110.dp)
        val FULL = DpSize(140.dp, 140.dp)

        const val ACTION_TAP = "com.brewingbytes.gemmory.action.STREAK_WIDGET"

        /** Opens [MainActivity] in its task (never a second one), which shows the streak screen over the gallery. */
        fun tapIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_TAP)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * [intent] is a widget tap. A relaunch from Recents carries the task's first intent again (see ReminderTap):
         * that's not a tap.
         */
        fun isTap(intent: Intent?): Boolean =
            intent?.action == ACTION_TAP && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
    }
}

/** Which of the widget's two texts: the number in mono, or the status line in Figtree. */
enum class WidgetText { NUMBER, LINE }

/**
 * The widget's layout (mockup streak-c): the number top left, the flame top right, the line at the bottom; one
 * TalkBack node whose description is [StreakWidgetState.description]. [text] draws each text (the number as a bitmap;
 * a test passes Glance's Text to find it).
 */
@Composable
fun StreakWidgetContent(
    state: StreakWidgetState,
    onTap: Action,
    text: @Composable (WidgetText, String, Boolean) -> Unit = { kind, value, compact -> WidgetTextView(kind, value, compact) },
) {
    val compact = LocalSize.current.width < StreakWidget.FULL.width
    // Resolved with the widget's own context (T48): the launcher's process draws it.
    val strings = LocalContext.current.strings
    val description = state.description(strings)
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_felt))
            .cornerRadius(R.dimen.widget_radius)
            .padding(padding(compact))
            .semantics { contentDescription = description }
            .clickable(onTap),
    ) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Box(GlanceModifier.defaultWeight()) { text(WidgetText.NUMBER, state.number.toString(), compact) }
            Image(
                ImageProvider(flameDrawable(state.flame)),
                contentDescription = null,
                modifier = GlanceModifier.size(flameSize(compact)),
            )
        }
        Box(GlanceModifier.fillMaxWidth()) { text(WidgetText.LINE, state.line(strings), compact) }
    }
}

private fun padding(compact: Boolean) = if (compact) 10.dp else 14.dp

private fun flameSize(compact: Boolean) = if (compact) 24.dp else 32.dp

fun flameDrawable(flame: WidgetFlame): Int = when (flame) {
    WidgetFlame.EMBER -> R.drawable.widget_flame_ember
    WidgetFlame.LIT -> R.drawable.widget_flame
    WidgetFlame.LIT_30 -> R.drawable.widget_flame_30
    WidgetFlame.LIT_100 -> R.drawable.widget_flame_100
}

/**
 * One of the widget's texts. The number is a bitmap in the design-system mono (a launcher draws a widget's TextViews in
 * the system font, whatever font their layout names), sized in dp and shrunk to the room beside the flame, so a long
 * streak never outgrows the tile; TalkBack reads the widget's description instead. The line is Glance's Text in the system font, so it follows font scale.
 */
@Composable
private fun WidgetTextView(kind: WidgetText, value: String, compact: Boolean) {
    when (kind) {
        WidgetText.NUMBER -> {
            val context = LocalContext.current
            val room = LocalSize.current.width - padding(compact) * 2 - flameSize(compact) - NUMBER_GAP
            val bitmap = numberBitmap(context, value, sizeDp = if (compact) 32f else 44f, maxWidthDp = room.value)
            val density = context.resources.displayMetrics.density
            Image(
                ImageProvider(bitmap),
                contentDescription = null,
                modifier = GlanceModifier.size((bitmap.width / density).dp, (bitmap.height / density).dp),
            )
        }
        WidgetText.LINE -> Text(
            value,
            style = TextStyle(color = ColorProvider(GemColors.TextMuted), fontSize = if (compact) 12.sp else 14.sp),
            maxLines = 2,
        )
    }
}

/** The least room between the number and the flame. */
private val NUMBER_GAP = 4.dp

/**
 * [text] in Atkinson Hyperlegible Mono 700 with tabular figures, `bone`, at [sizeDp], or smaller when it would be wider
 * than [maxWidthDp]; tightly cropped.
 */
fun numberBitmap(context: Context, text: String, sizeDp: Float, maxWidthDp: Float = Float.MAX_VALUE): Bitmap {
    val density = context.resources.displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_mono)
        fontVariationSettings = "'wght' 700"
        fontFeatureSettings = "tnum"
        textSize = sizeDp * density
        color = GemColors.Bone.toArgb()
    }
    val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
    val maxWidth = maxWidthDp * density
    if (bounds.width() > maxWidth && maxWidth > 0) {
        paint.textSize *= maxWidth / bounds.width()
        paint.getTextBounds(text, 0, text.length, bounds)
    }
    val bitmap = createBitmap(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1))
    Canvas(bitmap).drawText(text, -bounds.left.toFloat(), -bounds.top.toFloat(), paint)
    return bitmap
}

/**
 * The widget's provider. The first one placed logs `streak_widget_added` and turns on its refresh (the midnight alarm
 * and the clock receivers); the last one removed turns it off, so nothing starts the process for a widget that's gone.
 */
class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        (context.applicationContext as GemmoryApp).streakWidgets.onAdded()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        (context.applicationContext as GemmoryApp).streakWidgets.onRemoved()
    }
}
