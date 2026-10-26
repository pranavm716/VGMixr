package io.github.pranavm716.vgmixr.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.settings.Accent

/**
 * Each accent's color. All are dark enough for the white icons drawn on the accent (at least 2:1
 * against white) and bright enough to stand out on the background.
 */
val Accent.color: Color
    get() = when (this) {
        Accent.RED -> DefaultAccent
        Accent.ORANGE -> Color(0xFFF44336)
        Accent.GOLD -> Color(0xFFFF9800)
        Accent.GREEN -> Color(0xFF388E3C)
        Accent.TEAL -> Color(0xFF00838F)
        Accent.BLUE -> Color(0xFF1E86E2)
        Accent.PURPLE -> Color(0xFF7C4DFF)
        Accent.PINK -> Color(0xFFE91E63)
    }

/** The accent's name, read out for its circle in Settings. */
@get:StringRes
val Accent.label: Int
    get() = when (this) {
        Accent.RED -> R.string.accent_red
        Accent.ORANGE -> R.string.accent_orange
        Accent.GOLD -> R.string.accent_gold
        Accent.GREEN -> R.string.accent_green
        Accent.TEAL -> R.string.accent_teal
        Accent.BLUE -> R.string.accent_blue
        Accent.PURPLE -> R.string.accent_purple
        Accent.PINK -> R.string.accent_pink
    }
