package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.settings.Background
import io.github.pranavm716.vgmixr.domain.settings.BackgroundSetting
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [BackgroundSetting] in SharedPreferences, by the background's name, as [PrefsAccentSetting]
 * keeps the accent. A name the app no longer offers reads as the default.
 */
@Singleton
class PrefsBackgroundSetting @Inject constructor(@ApplicationContext context: Context) :
    BackgroundSetting {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val current = MutableStateFlow(
        Background.entries.firstOrNull { it.name == prefs.getString(BACKGROUND, null) }
            ?: Background.DEFAULT
    )

    override val background: StateFlow<Background> = current.asStateFlow()

    override fun set(background: Background) {
        prefs.edit { putString(BACKGROUND, background.name) }
        current.value = background
    }

    private companion object {
        const val PREFS = "settings"
        const val BACKGROUND = "background"
    }
}
