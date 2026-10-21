package com.brewingbytes.gemmory.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.brewingbytes.gemmory.policy.Inventory
import com.brewingbytes.gemmory.ui.GemColors
import com.brewingbytes.gemmory.ui.GemType
import com.brewingbytes.gemmory.ui.LocalFelt

/**
 * "Next gift spin at 5 days in a row" and five bone/felt steps for the days so far (no countdown, no loss copy): in the
 * Inventory sheet (#38) and on the streak screen (#101).
 */
@Composable
fun NextGift(streak: Int, tag: String, modifier: Modifier = Modifier) {
    val done = streak % Inventory.SPIN_EVERY
    val next = streak - done + Inventory.SPIN_EVERY
    Column(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .clearAndSetSemantics { contentDescription = nextGiftText(next, done) },
    ) {
        Text("Next gift spin at $next days in a row", style = GemType.label, color = GemColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(Inventory.SPIN_EVERY) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < done) GemColors.Bone else LocalFelt.current.ground),
                )
            }
        }
    }
}

internal fun nextGiftText(next: Int, done: Int) = "Next gift spin at $next days in a row. $done of ${Inventory.SPIN_EVERY} days so far."
