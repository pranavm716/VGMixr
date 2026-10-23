package io.github.pranavm716.vgmixr.domain.settings

import kotlinx.coroutines.flow.StateFlow

/** The accent colors the app offers, in the order Settings shows them. */
enum class Accent {
    RED,
    ORANGE,
    GOLD,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK;

    companion object {
        val DEFAULT = RED
    }
}

/** The accent setting, one for the whole app, kept across launches. */
interface AccentSetting {
    val accent: StateFlow<Accent>

    fun set(accent: Accent)
}
