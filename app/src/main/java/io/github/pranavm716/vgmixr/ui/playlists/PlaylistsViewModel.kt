package io.github.pranavm716.vgmixr.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.domain.playlist.playableTrackIds
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState

    data class Loaded(val playlists: List<PlaylistSummary>) : PlaylistsUiState
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages
) : ViewModel() {
    val uiState: StateFlow<PlaylistsUiState> =
        repository.playlists.map { PlaylistsUiState.Loaded(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PlaylistsUiState.Loading
            )

    /**
     * Adds a playlist's tracks, in its order (newest first), to "Next in queue", leaving out those
     * that can't play.
     */
    fun onQueuePlaylist(playlistId: Long) {
        viewModelScope.launch {
            val playlist = repository.playlist(playlistId).first() ?: return@launch
            val ids = playlist.playableTrackIds(catalog.catalog.first())
            if (ids.isEmpty()) return@launch
            playback.addToQueue(ids)
            messages.post(UserMessage.AddedToQueue(ids.size))
        }
    }
}
