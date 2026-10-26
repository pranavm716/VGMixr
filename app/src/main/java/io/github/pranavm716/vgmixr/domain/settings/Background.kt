package io.github.pranavm716.vgmixr.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The background colors the app offers, all dark, in the order Settings shows them. */
enum class Background {
    BLACK,
    NAVY,
    TEAL,
    SLATE,
    PLUM;

    companion object {
        val DEFAULT = BLACK
    }
}

/** The background setting, one for the whole app, kept across launches. */
interface BackgroundSetting {
    val background: StateFlow<Background>

    fun set(background: Background)

    companion object {
        /** Always the default: for screens and tests that don't care. */
        val NONE: BackgroundSetting = object : BackgroundSetting {
            override val background: StateFlow<Background> = MutableStateFlow(Background.DEFAULT)

            override fun set(background: Background) = Unit
        }
    }
}
