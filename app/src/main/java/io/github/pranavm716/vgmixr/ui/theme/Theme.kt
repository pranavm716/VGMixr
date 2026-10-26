package io.github.pranavm716.vgmixr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Dark only, with no dynamic color: every screen is the same dark [background] (near-black by
 * default) with one [accent].
 */
@Composable
fun VGMixrTheme(
    accent: Color = DefaultAccent,
    background: Color = Background,
    content: @Composable () -> Unit
) {
    val surfaces = Surfaces.on(background)
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        onSecondary = Color.White,
        tertiary = accent,
        background = background,
        onBackground = TextPrimary,
        surface = background,
        onSurface = TextPrimary,
        surfaceVariant = background,
        onSurfaceVariant = TextSecondary,
        surfaceContainer = background,
        outline = Inactive,
        outlineVariant = surfaces.divider
    )
    CompositionLocalProvider(LocalSurfaces provides surfaces) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
