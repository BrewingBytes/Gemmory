package com.brewingbytes.gemmory.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemSheet
import com.brewingbytes.gemmory.ui.GemType

/** Selected color copy (design system, Settings → Selected color, #19). */
object HighlightCopy {
    const val TITLE = "Selected color"

    fun name(style: HighlightStyle): String = when (style) {
        HighlightStyle.TINT -> "Stronger tint"
        HighlightStyle.OUTLINE -> "Outline"
        HighlightStyle.DIM -> "Dim the rest"
    }

    fun description(style: HighlightStyle): String = when (style) {
        HighlightStyle.TINT -> "Its cells turn a deeper shade"
        HighlightStyle.OUTLINE -> "A ring around its cells when zoomed in"
        HighlightStyle.DIM -> "Every other color steps back"
    }
}

/**
 * The Selected color sheet: three radio rows in one group. A pick saves and closes; swipe, back or the scrim close it
 * without a change, and focus goes back to the Settings row.
 */
@Composable
fun HighlightSheet(current: HighlightStyle, onPick: (HighlightStyle) -> Unit, onDismiss: () -> Unit, returnFocusTo: String) {
    GemSheet(HighlightCopy.TITLE, onDismiss, returnFocusTo) {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            for (style in HighlightStyle.entries) {
                val selected = style == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        // The name and description merge into the row's one node; "selected" comes from here.
                        .selectable(selected = selected, role = Role.RadioButton) { onPick(style) }
                        .padding(vertical = 8.dp)
                        .testTag(optionTag(style)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioMark(selected)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(HighlightCopy.name(style), style = GemType.body, color = GemColors.Bone)
                        Text(HighlightCopy.description(style), style = GemType.label, color = GemColors.TextMuted)
                    }
                }
            }
        }
    }
}

/** Unselected: a 1.5dp bone ring. Selected: a 2dp lamp ring around an 8dp lamp dot. */
@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) GemColors.Lamp else GemColors.Bone, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).background(GemColors.Lamp, CircleShape))
    }
}

fun optionTag(style: HighlightStyle) = "highlight-${style.key}"
