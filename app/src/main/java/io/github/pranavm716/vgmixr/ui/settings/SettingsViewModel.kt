package io.github.pranavm716.vgmixr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Settings' content: the accents to pick from, with the current one. */
data class SettingsUiState(
    val accents: List<Accent> = Accent.entries,
    val accent: Accent = Accent.DEFAULT
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val accentSetting: AccentSetting) :
    ViewModel() {
    val uiState: StateFlow<SettingsUiState> = accentSetting.accent
        .map { SettingsUiState(accent = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(accent = accentSetting.accent.value)
        )

    fun onAccentClick(accent: Accent) = accentSetting.set(accent)
}
