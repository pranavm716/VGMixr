package io.github.pranavm716.vgmixr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Shuttle's dark look: one near-black for the window, bars and sheets (the default background;
// Settings offers other dark ones, see BackgroundColors.kt).
val Background = Color(0xFF111111)

/** The default accent (Accent.RED), for the selected tab and other current elements. */
val DefaultAccent = Color(0xFFbf0c3e)

val TextPrimary = Color(0xFFE6E6E6)
val TextSecondary = Color(0xFFA6A6A6)

/** The search pill's placeholder: a touch lighter than [TextSecondary], readable on the pill. */
val FieldHint = lerp(TextSecondary, TextPrimary, 0.2f)
val Inactive = Color(0xFF8A8A8A)
