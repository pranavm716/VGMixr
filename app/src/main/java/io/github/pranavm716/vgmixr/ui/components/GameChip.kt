package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/** A game's badge: its short name on its color, as in the review tool. */
data class ChipUi(val shortName: String, val color: String)

/** A manifest `#RRGGBB` color, parsed by hand so it works in plain JVM tests. */
fun chipColor(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(16))

/** Black or white text, whichever contrasts more (WCAG relative luminance, like the review tool). */
fun chipTextColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLong(16)
    fun channel(shift: Int): Double {
        val c = ((rgb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    return if (luminance > 0.179) Color.Black else Color.White
}

// Monospace, so equal characters take equal width on every badge.
private val ChipText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

@Composable
fun GameChip(chip: ChipUi, modifier: Modifier = Modifier) {
    Text(
        chip.shortName,
        style = ChipText,
        color = chipTextColor(chip.color),
        modifier = modifier
            .background(chipColor(chip.color), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** A track's games, side by side. */
@Composable
fun GameChips(chips: List<ChipUi>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { GameChip(it) }
    }
}
