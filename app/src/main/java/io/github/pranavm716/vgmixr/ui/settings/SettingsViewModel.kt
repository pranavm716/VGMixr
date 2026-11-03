package io.github.pranavm716.vgmixr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.OfflineUsage
import io.github.pranavm716.vgmixr.domain.offline.RecentCutoff
import io.github.pranavm716.vgmixr.domain.playback.effectivePlaylists
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import io.github.pranavm716.vgmixr.domain.settings.Background
import io.github.pranavm716.vgmixr.domain.settings.BackgroundSetting
import io.github.pranavm716.vgmixr.domain.settings.HoldSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val backgroundSetting: BackgroundSetting = BackgroundSetting.NONE,
    hold: HoldSettings = HoldSettings.NONE,
    catalog: CatalogRepository? = null,
    private val playlists: PlaylistRepository? = null
) : ViewModel() {
    private val holdSettings = hold
    private val families = catalog?.catalog?.map { it.families } ?: emptyFlow()
    private val playlistList = playlists?.playlists ?: emptyFlow()

    /** The "Hold to play" pickers: which families and playlists a tab's long press plays. */
    val hold: StateFlow<HoldUiState> = combine(
        families,
        playlistList,
        holdSettings.excludedFamilies,
        holdSettings.playlists
    ) { families, playlists, excluded, selected ->
        HoldUiState(gameChoices(families, excluded), playlistChoices(playlists, selected))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldUiState())

    /** Checks or unchecks family [id] in the Games picker. */
    fun onGameToggle(id: String) {
        val excluded = holdSettings.excludedFamilies.value
        holdSettings.setExcludedFamilies(if (id in excluded) excluded - id else excluded + id)
    }

    /** Checks every family, or none. */
    fun onAllGames(checked: Boolean) {
        viewModelScope.launch {
            holdSettings.setExcludedFamilies(
                if (checked) {
                    emptySet()
                } else {
                    families.first().map {
                        it.id
                    }.toSet()
                }
            )
        }
    }

    /** Checks or unchecks playlist [id] in the Playlists picker; unchecking all means Favorites. */
    fun onPlaylistToggle(id: String) {
        val playlistId = id.toLongOrNull() ?: return
        viewModelScope.launch {
            val current = effectivePlaylists(playlistList.first(), holdSettings.playlists.value)
            holdSettings.setPlaylists(
                if (playlistId in current) current - playlistId else current + playlistId
            )
        }
    }

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
