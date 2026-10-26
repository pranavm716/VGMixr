package io.github.pranavm716.vgmixr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.OfflineUsage
import io.github.pranavm716.vgmixr.domain.offline.RecentCutoff
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import io.github.pranavm716.vgmixr.domain.settings.Background
import io.github.pranavm716.vgmixr.domain.settings.BackgroundSetting
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Settings' content: the accents and backgrounds to pick from, with the current ones. */
data class SettingsUiState(
    val accents: List<Accent> = Accent.entries,
    val accent: Accent = Accent.DEFAULT,
    val backgrounds: List<Background> = Background.entries,
    val background: Background = Background.DEFAULT
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accentSetting: AccentSetting,
    offline: OfflineStore = OfflineStore.NONE,
    private val recentCutoff: RecentCutoff = RecentCutoff.NONE,
    private val backgroundSetting: BackgroundSetting = BackgroundSetting.NONE
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(accentSetting.accent, backgroundSetting.background) { accent, background ->
            SettingsUiState(accent = accent, background = background)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(
                accent = accentSetting.accent.value,
                background = backgroundSetting.background.value
            )
        )

    fun onAccentClick(accent: Accent) = accentSetting.set(accent)

    fun onBackgroundClick(background: Background) = backgroundSetting.set(background)

    /** Space taken by downloaded and cached tracks; null until read. */
    val storage: StateFlow<OfflineUsage?> =
        offline.usage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Deletes the cached recent plays; plays from now on count again. */
    fun onClearRecent() = recentCutoff.clearNow()
}
