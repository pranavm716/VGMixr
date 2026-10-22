package io.github.pranavm716.vgmixr.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState

    data class Loaded(val playlists: List<PlaylistSummary>) : PlaylistsUiState
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(repository: PlaylistRepository) : ViewModel() {
    val uiState: StateFlow<PlaylistsUiState> =
        repository.playlists.map { PlaylistsUiState.Loaded(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PlaylistsUiState.Loading
            )
}
