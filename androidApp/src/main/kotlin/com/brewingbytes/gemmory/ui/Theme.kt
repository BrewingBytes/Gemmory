package com.brewingbytes.gemmory.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.strings
import kotlin.random.Random

/** Tokens from docs/design-system.md ("The Sorting Tray"). */
object GemColors {
    val Felt = Color(0xFF4F5A52)
    val FeltDeep = Color(0xFF3E4741)
    val FeltRaised = Color(0xFF5C685F)
    val FeltDark = Color(0xFF39423C)
    val FeltDeepDark = Color(0xFF2E3530)
    val FeltRaisedDark = Color(0xFF47524A)
    val Bone = Color(0xFFE9E4D8)
    val OnBone = Color(0xFF2B322D)
    val TextMuted = Color(0xFFCDD3C9)
    val Lamp = Color(0xFFF4E3B1)
    val Patina = Color(0xFF7FB3A0)
    val PatinaText = Color(0xFFACD6C6)
    val Fault = Color(0xFFE8B09E)
    val Scrim = Color(0xFF141815)
    val FrameText = Color(0xFFF2EEE4)

    /** The streak flame (#101, G1): the one saturated color outside a picture. Amber, orange from 30 days, red-orange from 100. */
    val Flame = Color(0xFFF5A623)
    val Flame30 = Color(0xFFF08A2C)
    val Flame100 = Color(0xFFE0563B)
    val FlameCore = Color(0xFFFCE3A6)

    /** The flame before today's finish (G1): grey ember. */
    val Ember = Color(0xFF7E8880)
    val EmberCore = Color(0xFFA3ACA4)
}

/** The three felt tokens drop one step under system dark mode; every other token stays the same. */
@Immutable
data class Felt(val ground: Color, val deep: Color, val raised: Color)

val LocalFelt = staticCompositionLocalOf { Felt(GemColors.Felt, GemColors.FeltDeep, GemColors.FeltRaised) }

private val AtkinsonMono = FontFamily(
    Font(R.font.atkinson_hyperlegible_mono, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun AppFonts.Face.font(weight: FontWeight) =
    if (variable) Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(this.weight))) else Font(res, weight)

/**
 * The type scale in one language's faces (T49): Latin is Bricolage Grotesque + Figtree as before, word for word the
 * same styles. German and Hungarian hyphenate; Japanese breaks lines at phrases and reads at 1.6 (DR6, DC4).
 */
@Immutable
class GemTypeSet private constructor(fonts: AppFonts, language: String) {
    private val displayFamily = FontFamily(fonts.display.font(FontWeight.SemiBold))
    private val bodyFamily = FontFamily(fonts.body.font(FontWeight.Normal), fonts.bodyStrong.font(FontWeight.SemiBold))
    private val wrap = when (language) {
        "de", "hu" -> TextStyle(hyphens = Hyphens.Auto)
        "ja" -> TextStyle(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))
        else -> TextStyle()
    }

    val display = wrap.merge(TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.01).em))
    val displayLarge = wrap.merge(TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.02).em))
    val body = wrap.merge(TextStyle(fontFamily = bodyFamily, fontSize = 16.sp, lineHeight = if (language == "ja") 25.6.sp else 24.sp))
    val button = wrap.merge(TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
    val label = wrap.merge(TextStyle(fontFamily = bodyFamily, fontSize = 13.sp))

    companion object {
        private val sets = mutableMapOf<String, GemTypeSet>()

        /** The scale for a language code, made once per language. */
        fun of(language: String): GemTypeSet {
            val key = when (language) { "de", "hu", "ja", "bg" -> language; else -> "en" }
            return synchronized(sets) { sets.getOrPut(key) { GemTypeSet(AppFonts.forLanguage(key), key) } }
        }
    }
}

val LocalGemType = staticCompositionLocalOf { GemTypeSet.of("en") }

/** The type scale of the language the app shows, provided by [GemmoryTheme]. */
object GemType {
    val display: TextStyle @Composable @ReadOnlyComposable get() = LocalGemType.current.display
    val displayLarge: TextStyle @Composable @ReadOnlyComposable get() = LocalGemType.current.displayLarge
    val body: TextStyle @Composable @ReadOnlyComposable get() = LocalGemType.current.body
    val button: TextStyle @Composable @ReadOnlyComposable get() = LocalGemType.current.button
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalGemType.current.label

    /** Figures in every language (DC3): Atkinson Hyperlegible Mono 700, tabular. */
    val mono = TextStyle(fontFamily = AtkinsonMono, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

    /** The streak number (#101, G8): mono 700, tabular; 88sp on the celebration, 56sp on the streak screen. */
    val streakHero = mono.copy(fontSize = 88.sp, lineHeight = 92.sp)
}

@Composable
fun GemmoryTheme(content: @Composable () -> Unit) {
    val felt = if (isSystemInDarkTheme()) {
        Felt(GemColors.FeltDark, GemColors.FeltDeepDark, GemColors.FeltRaisedDark)
    } else {
        Felt(GemColors.Felt, GemColors.FeltDeep, GemColors.FeltRaised)
    }
    val scheme = darkColorScheme(
        primary = GemColors.Bone,
        onPrimary = GemColors.OnBone,
        background = felt.ground,
        onBackground = GemColors.Bone,
        surface = felt.deep,
        onSurface = GemColors.Bone,
        onSurfaceVariant = GemColors.TextMuted,
        secondaryContainer = felt.raised,
        onSecondaryContainer = GemColors.Bone,
    )
    val type = GemTypeSet.of(strings().locale.language)
    val typography = Typography(
        headlineLarge = type.displayLarge,
        titleLarge = type.display,
        bodyLarge = type.body,
        bodyMedium = type.body,
        labelLarge = type.button,
        bodySmall = type.label,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalFelt provides felt, LocalGemType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** The felt's ~9% grain: one small seeded noise bitmap, tiled (also on the streak share card, #104). */
fun grainBitmap(): Bitmap {
    val size = 96
    val random = Random(0x6E6D)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (23 shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** ~9% grain over the felt ground. */
private val grainBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(grainBitmap().asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

fun Modifier.feltGround(color: Color): Modifier = background(color).drawBehind { drawRect(grainBrush) }

private val ButtonShape = RoundedCornerShape(6.dp)

/** [held]: shows the pressed state (1dp down) and ignores taps, for single-flight actions like Start. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, held: Boolean = false) {
    Button(
        onClick = { if (!held) onClick() },
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp).offset(y = if (held) 1.dp else 0.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = GemColors.Bone,
            contentColor = GemColors.OnBone,
            disabledContainerColor = GemColors.Bone.copy(alpha = 0.55f),
            disabledContentColor = GemColors.OnBone.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, style = GemType.button) }
}

/** [selected]: the 2dp `lamp` outline ("this one"), e.g. T56's tapped answer while its reward lands. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false) {
    val raised = LocalFelt.current.raised
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = ButtonShape,
        border = if (selected) BorderStroke(2.dp, GemColors.Lamp) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = raised,
            contentColor = GemColors.Bone,
            disabledContainerColor = raised.copy(alpha = 0.55f),
            disabledContentColor = GemColors.Bone.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, style = GemType.button) }
}

/** Ratio chip: felt-raised at rest, lamp outline when selected (lamp = "this one"). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clip(shape)
            .background(LocalFelt.current.raised)
            .border(if (selected) BorderStroke(2.dp, GemColors.Lamp) else BorderStroke(0.dp, Color.Transparent), shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = GemType.button, color = GemColors.Bone, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
