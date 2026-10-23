package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AccentSetting] in SharedPreferences, by the accent's name. One instance, so a pick in Settings
 * reaches the theme at once. A name the app no longer offers reads as the default.
 */
@Singleton
class PrefsAccentSetting @Inject constructor(@ApplicationContext context: Context) :
    AccentSetting {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val current = MutableStateFlow(
        Accent.entries.firstOrNull { it.name == prefs.getString(ACCENT, null) } ?: Accent.DEFAULT
    )

    override val accent: StateFlow<Accent> = current.asStateFlow()

    override fun set(accent: Accent) {
        prefs.edit { putString(ACCENT, accent.name) }
        current.value = accent
    }

    private companion object {
        const val PREFS = "settings"
        const val ACCENT = "accent"
    }
}
