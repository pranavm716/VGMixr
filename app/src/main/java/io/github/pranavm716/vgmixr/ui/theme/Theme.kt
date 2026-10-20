package io.github.pranavm716.vgmixr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark only, with no dynamic color: every screen is the same near-black with one [accent]. */
@Composable
fun VGMixrTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        onSecondary = Color.White,
        tertiary = accent,
        background = Background,
        onBackground = TextPrimary,
        surface = Background,
        onSurface = TextPrimary,
        surfaceVariant = Background,
        onSurfaceVariant = TextSecondary,
        surfaceContainer = Background,
        outline = Inactive,
        outlineVariant = Divider
    )
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
