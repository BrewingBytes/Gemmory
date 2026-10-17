package com.brewingbytes.gemmory.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object GemIcons {
    val Back: ImageVector = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f, autoMirror = true)
        .path(fill = SolidColor(Color.White)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            close()
        }
        .build()

    /** Material "close" cross, for the paywall's close (design system, Paywall: visible immediately, top-left). */
    val Close: ImageVector = ImageVector.Builder("Close", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"),
            fill = SolidColor(Color.White),
        )
        .build()

    /** Material "search" magnifier, for Find (design D24). */
    val Find: ImageVector = ImageVector.Builder("Find", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M15.5 14h-0.79l-0.28-0.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16" +
                    "c1.61 0 3.09-0.59 4.23-1.57l0.27 0.28v0.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 " +
                    "9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
            ),
            fill = SolidColor(Color.White),
        )
        .build()

    /** Placement tool "Tap" (#20): one gem. */
    val ToolTap: ImageVector = ImageVector.Builder("ToolTap", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M9.5 8h5.0a1.5 1.5 0 0 1 1.5 1.5v5.0a1.5 1.5 0 0 1 -1.5 1.5h-5.0a1.5 1.5 0 0 1 -1.5 -1.5v-5.0a1.5 1.5 0 0 1 1.5 -1.5z"), fill = SolidColor(Color.White))
        .build()

    /** Placement tool "Brush" (#20): a 3×3 of gems. */
    val ToolBrush: ImageVector = ImageVector.Builder("ToolBrush", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M5.0 4.0h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM10.75 4.0h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM16.5 4.0h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM5.0 9.75h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM10.75 9.75h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM16.5 9.75h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM5.0 15.5h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM10.75 15.5h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1zM16.5 15.5h2.5a1 1 0 0 1 1 1v2.5a1 1 0 0 1 -1 1h-2.5a1 1 0 0 1 -1 -1v-2.5a1 1 0 0 1 1 -1z"), fill = SolidColor(Color.White))
        .build()

    /** Placement tool "Fill region" (#20): a patch with the tapped gem at its heart. */
    val ToolRegion: ImageVector = ImageVector.Builder("ToolRegion", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M12 3.5c-4.1 0-8.5 2.4-8.5 7.2 0 3.6 2.2 4.6 3.4 6.4 1.4 2.1 2.8 3.4 5.6 3.4 4.3 0 7.9-3.3 7.9-8.3" +
                    "0-5-3.6-8.7-8.4-8.7zm0 1.9c3.7 0 6.5 2.8 6.5 6.8 0 3.9-2.8 6.4-6 6.4-2 0-2.9-0.9-4-2.6" +
                    "-1.3-1.9-3-2.6-3-5.3 0-3.4 3.4-5.3 6.5-5.3zM10.5 10h3a0.5 0.5 0 0 1 0.5 0.5v3a0.5 0.5 0 0 1-0.5 0.5h-3" +
                    "a0.5 0.5 0 0 1-0.5-0.5v-3a0.5 0.5 0 0 1 0.5-0.5z",
            ),
            fill = SolidColor(Color.White),
        )
        .build()

    /** "Fill the rest" of a color (#20): scattered gems and a check. */
    val FillColor: ImageVector = ImageVector.Builder("FillColor", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M4 4h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1zM16 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1zM6 15h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1zM13.5 16.2l1.4-1.4 2.1 2.1 4.2-4.2 1.4 1.4-5.6 5.6z"), fill = SolidColor(Color.White))
        .build()

    /** The gallery's Inventory (#38): a bag. */
    val Bag: ImageVector = ImageVector.Builder("Bag", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M7 7V6a5 5 0 0 1 10 0v1h3l-1.2 14H5.2L4 7h3zm2 0h6V6a3 3 0 0 0-6 0v1z"), fill = SolidColor(Color.White))
        .build()

    /** Inventory: kept photo canvases (#38), a framed landscape. */
    val PhotoCanvas: ImageVector = ImageVector.Builder("PhotoCanvas", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm0 2v9.6l4-4 4 4 3-3 3 3V6H5z " +
                    "M16 7.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z",
            ),
            fill = SolidColor(Color.White),
        )
        .build()

    /** Inventory: unlock tokens (#38), an open padlock (never drawn over art). */
    val Token: ImageVector = ImageVector.Builder("Token", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M7 10V7a5 5 0 0 1 9.6-1.9l-1.8 0.8A3 3 0 0 0 9 7v3h9a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-7" +
                    "a2 2 0 0 1 2-2h1zm-1 2v7h12v-7H6z",
            ),
            fill = SolidColor(Color.White),
        )
        .build()

    /** Material "visibility" eye, for the canvas's Peek (#35). */
    val Peek: ImageVector = ImageVector.Builder("Peek", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17" +
                    "c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
            ),
            fill = SolidColor(Color.White),
        )
        .build()

    /** Material "share" (three nodes), for Share progress on the play canvas (#36). */
    val Share: ImageVector = ImageVector.Builder("Share", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 " +
                    "2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 " +
                    "1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-" +
                    "1.31 2.92-2.92-1.31-2.92-2.92-2.92z",
            ),
            fill = SolidColor(Color.White),
        )
        .build()

    /** Material "settings" outline gear. */
    val Settings: ImageVector = ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.White)) {
            moveTo(19.43f, 12.98f)
            curveToRelative(0.04f, -0.32f, 0.07f, -0.64f, 0.07f, -0.98f)
            reflectiveCurveToRelative(-0.03f, -0.66f, -0.07f, -0.98f)
            lineToRelative(2.11f, -1.65f)
            curveToRelative(0.19f, -0.15f, 0.24f, -0.42f, 0.12f, -0.64f)
            lineToRelative(-2f, -3.46f)
            curveToRelative(-0.12f, -0.22f, -0.39f, -0.3f, -0.61f, -0.22f)
            lineToRelative(-2.49f, 1f)
            curveToRelative(-0.52f, -0.4f, -1.08f, -0.73f, -1.69f, -0.98f)
            lineToRelative(-0.38f, -2.65f)
            curveTo(14.46f, 2.18f, 14.25f, 2f, 14f, 2f)
            horizontalLineToRelative(-4f)
            curveToRelative(-0.25f, 0f, -0.46f, 0.18f, -0.49f, 0.42f)
            lineToRelative(-0.38f, 2.65f)
            curveToRelative(-0.61f, 0.25f, -1.17f, 0.59f, -1.69f, 0.98f)
            lineToRelative(-2.49f, -1f)
            curveToRelative(-0.23f, -0.09f, -0.49f, 0f, -0.61f, 0.22f)
            lineToRelative(-2f, 3.46f)
            curveToRelative(-0.13f, 0.22f, -0.07f, 0.49f, 0.12f, 0.64f)
            lineToRelative(2.11f, 1.65f)
            curveToRelative(-0.04f, 0.32f, -0.07f, 0.65f, -0.07f, 0.98f)
            reflectiveCurveToRelative(0.03f, 0.66f, 0.07f, 0.98f)
            lineToRelative(-2.11f, 1.65f)
            curveToRelative(-0.19f, 0.15f, -0.24f, 0.42f, -0.12f, 0.64f)
            lineToRelative(2f, 3.46f)
            curveToRelative(0.12f, 0.22f, 0.39f, 0.3f, 0.61f, 0.22f)
            lineToRelative(2.49f, -1f)
            curveToRelative(0.52f, 0.4f, 1.08f, 0.73f, 1.69f, 0.98f)
            lineToRelative(0.38f, 2.65f)
            curveToRelative(0.03f, 0.24f, 0.24f, 0.42f, 0.49f, 0.42f)
            horizontalLineToRelative(4f)
            curveToRelative(0.25f, 0f, 0.46f, -0.18f, 0.49f, -0.42f)
            lineToRelative(0.38f, -2.65f)
            curveToRelative(0.61f, -0.25f, 1.17f, -0.59f, 1.69f, -0.98f)
            lineToRelative(2.49f, 1f)
            curveToRelative(0.23f, 0.09f, 0.49f, 0f, 0.61f, -0.22f)
            lineToRelative(2f, -3.46f)
            curveToRelative(0.12f, -0.22f, 0.07f, -0.49f, -0.12f, -0.64f)
            lineToRelative(-2.11f, -1.65f)
            close()
            moveTo(12f, 15.5f)
            curveToRelative(-1.93f, 0f, -3.5f, -1.57f, -3.5f, -3.5f)
            reflectiveCurveToRelative(1.57f, -3.5f, 3.5f, -3.5f)
            reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
            reflectiveCurveToRelative(-1.57f, 3.5f, -3.5f, 3.5f)
            close()
        }
        .build()

    /** Play triangle for the "Watch to unlock" label (design D14): a vector, never the ▶ glyph some fonts draw as emoji. */
    val Play: ImageVector = ImageVector.Builder("Play", 12.dp, 12.dp, 24f, 24f, autoMirror = true)
        .path(fill = SolidColor(Color.White)) {
            moveTo(6f, 3.5f)
            lineTo(20f, 12f)
            lineTo(6f, 20.5f)
            close()
        }
        .build()

    /** A four-point spark for the "New" label on a remote picture's tile (#43), drawn as a vector like [Play]. */
    val Spark: ImageVector = ImageVector.Builder("Spark", 12.dp, 12.dp, 24f, 24f)
        .path(fill = SolidColor(Color.White)) {
            moveTo(12f, 1f)
            lineTo(14.6f, 9.4f)
            lineTo(23f, 12f)
            lineTo(14.6f, 14.6f)
            lineTo(12f, 23f)
            lineTo(9.4f, 14.6f)
            lineTo(1f, 12f)
            lineTo(9.4f, 9.4f)
            close()
        }
        .build()

    /** Three vertical dots (⋮), drawn as a vector so no font renders it as an emoji. */
    val More: ImageVector = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.White)) {
            for (cy in listOf(6f, 12f, 18f)) {
                moveTo(12f, cy - 2f)
                arcToRelative(2f, 2f, 0f, true, true, 0f, 4f)
                arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
                close()
            }
        }
        .build()
}

/** A 48dp icon button in bone with its own label (Accessibility: every icon button has a label). */
@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(GemColors.Bone))
    }
}

/** Material 3 structure: back (when [onBack] is set), a 22sp display title, then trailing actions. */
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack == null) 16.dp else 0.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(GemIcons.Back, "Back", onBack)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = GemType.display,
            color = GemColors.Bone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/** Text button: bone, underlined, 48dp minimum height (Phase 5 D16). */
@Composable
fun GemTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = GemType.button,
            color = GemColors.Bone,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
        )
    }
}

/** Switch: patina track when on, felt-raised at rest. */
@Composable
fun GemSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val raised = LocalFelt.current.raised
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = GemColors.Bone,
            checkedTrackColor = GemColors.Patina,
            checkedBorderColor = GemColors.Patina,
            uncheckedThumbColor = GemColors.Bone,
            uncheckedTrackColor = raised,
            uncheckedBorderColor = raised,
        ),
    )
}

/**
 * Bottom sheet (design system, Layout): felt-deep, 16dp top radius, grab handle, 20dp padding, 55% scrim, at most
 * 560dp wide and centered on large screens (D22). The content scrolls, so at 200% font nothing clips (D20).
 *
 * TalkBack focus (D19) moves to the title on open and back to the node tagged [returnFocusTo] (the trigger) on close.
 * Under reduced motion (D21) the sheet and its scrim fade in over 150ms instead of sliding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemSheet(title: String, onDismiss: () -> Unit, returnFocusTo: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = rememberReducedMotion()
    val fade = remember { Animatable(if (reducedMotion) 0f else 1f) }
    LaunchedEffect(Unit) { if (reducedMotion) withFullMotion { fade.animateTo(1f, tween(SHEET_FADE_MS)) } }
    // The trigger lives in the screen's window, not the sheet's.
    val screen = LocalView.current
    val returnTo by rememberUpdatedState(returnFocusTo)
    DisposableEffect(Unit) {
        openSheets++
        onDispose {
            openSheets--
            // When this sheet is replaced by another (Share progress → its result), the new title keeps the focus.
            returnTo?.let { screen.returnAccessibilityFocus(it, cancelled = { openSheets > 0 }) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        modifier = Modifier.graphicsLayer { alpha = fade.value },
        sheetMaxWidth = 560.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = LocalFelt.current.deep,
        contentColor = GemColors.Bone,
        scrimColor = GemColors.Scrim.copy(alpha = 0.55f * fade.value),
        dragHandle = { GrabHandle() },
    ) {
        val sheet = LocalView.current
        LaunchedEffect(Unit) {
            // After the sheet's window is up and TalkBack has looked at it.
            delay(OPEN_FOCUS_DELAY_MS)
            sheet.moveAccessibilityFocus(SHEET_TITLE_TAG)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                title,
                style = GemType.display,
                color = GemColors.Bone,
                modifier = Modifier.testTag(SHEET_TITLE_TAG).semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * The grab handle: a 32×4dp bar in a 48dp square, so the handle's expand and close actions (Material puts them on
 * it) get a full touch target and a label (Accessibility, Touch targets).
 */
@Composable
private fun GrabHandle() {
    Box(Modifier.size(48.dp).semantics { contentDescription = "Drag handle" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(GemColors.TextMuted))
    }
}

const val SHEET_TITLE_TAG = "sheet-title"
private const val SHEET_FADE_MS = 150
private const val OPEN_FOCUS_DELAY_MS = 400L

/** Sheets on screen now (main thread only). */
private var openSheets = 0

/** A confirm sheet: body text, a primary action and a "Keep" text button (design system, Navigation map). */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    returnFocusTo: String? = null,
) {
    GemSheet(title, onDismiss, returnFocusTo) {
        Text(body, style = GemType.body, color = GemColors.Bone)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(confirm, onConfirm, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { GemTextButton("Keep", onDismiss) }
    }
}

/** Reduced motion (design system, Motion): the system animator scale is 0 ("Remove animations"). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
