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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.brewingbytes.gemmory.R
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
    val Fault = Color(0xFFE8B09E)
    val Scrim = Color(0xFF141815)
    val FrameText = Color(0xFFF2EEE4)
}

/** The three felt tokens drop one step under system dark mode; every other token stays the same. */
@Immutable
data class Felt(val ground: Color, val deep: Color, val raised: Color)

val LocalFelt = staticCompositionLocalOf { Felt(GemColors.Felt, GemColors.FeltDeep, GemColors.FeltRaised) }

private val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
val Figtree = FontFamily(
    Font(R.font.figtree, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.figtree, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
private val AtkinsonMono = FontFamily(
    Font(R.font.atkinson_hyperlegible_mono, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

object GemType {
    val display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.01).em)
    val displayLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.02).em)
    val body = TextStyle(fontFamily = Figtree, fontSize = 16.sp, lineHeight = 24.sp)
    val button = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val label = TextStyle(fontFamily = Figtree, fontSize = 13.sp)
    val mono = TextStyle(fontFamily = AtkinsonMono, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
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
    val typography = Typography(
        headlineLarge = GemType.displayLarge,
        titleLarge = GemType.display,
        bodyLarge = GemType.body,
        bodyMedium = GemType.body,
        labelLarge = GemType.button,
        bodySmall = GemType.label,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalFelt provides felt) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** ~9% grain over the felt ground, tiled from one small noise bitmap. */
private val grainBrush: ShaderBrush by lazy {
    val size = 96
    val random = Random(0x6E6D)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (23 shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

fun Modifier.feltGround(color: Color): Modifier = background(color).drawBehind { drawRect(grainBrush) }

private val ButtonShape = RoundedCornerShape(6.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
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

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val raised = LocalFelt.current.raised
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = ButtonShape,
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
    ) { Text(text, style = GemType.button, color = GemColors.Bone) }
}
