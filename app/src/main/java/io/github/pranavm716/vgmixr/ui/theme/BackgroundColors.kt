package io.github.pranavm716.vgmixr.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.settings.Background as BackgroundChoice

/**
 * Each background's color. All dark, so light text and the white icons read on them; slate and
 * teal are darker than the icon mockup's, so every accent still stands out (2:1 at least, red on
 * teal the closest).
 */
val BackgroundChoice.color: Color
    get() = when (this) {
        BackgroundChoice.BLACK -> Background
        BackgroundChoice.NAVY -> Color(0xFF1A2440)
        BackgroundChoice.TEAL -> Color(0xFF0B3836)
        BackgroundChoice.SLATE -> Color(0xFF262B36)
        BackgroundChoice.PLUM -> Color(0xFF2B1B3F)
    }

/** The background's name, read out for its circle in Settings. */
@get:StringRes
val BackgroundChoice.label: Int
    get() = when (this) {
        BackgroundChoice.BLACK -> R.string.background_black
        BackgroundChoice.NAVY -> R.string.background_navy
        BackgroundChoice.TEAL -> R.string.background_teal
        BackgroundChoice.SLATE -> R.string.background_slate
        BackgroundChoice.PLUM -> R.string.background_plum
    }

/**
 * The shades a step above the background: dialogs, the blank art tile and dividers, each the
 * background mixed a little toward white (on black, the #1E1E1E, #202020 and #2A2A2A the app had).
 */
@Immutable
data class Surfaces(
    val dialog: Color,
    val artPlaceholder: Color,
    val divider: Color,
    val field: Color
) {
    companion object {
        fun on(background: Color) = Surfaces(
            dialog = lerp(background, Color.White, 0.055f),
            artPlaceholder = lerp(background, Color.White, 0.065f),
            divider = lerp(background, Color.White, 0.1f),
            field = lerp(background, Color.White, 0.08f)
        )
    }
}

val LocalSurfaces = staticCompositionLocalOf { Surfaces.on(Background) }

/** The current background's step-up shades. */
object VGMixrColors {
    val dialog: Color
        @Composable @ReadOnlyComposable
        get() = LocalSurfaces.current.dialog

    val artPlaceholder: Color
        @Composable @ReadOnlyComposable
        get() = LocalSurfaces.current.artPlaceholder

    /** The search pill's fill. */
    val field: Color
        @Composable @ReadOnlyComposable
        get() = LocalSurfaces.current.field
}
