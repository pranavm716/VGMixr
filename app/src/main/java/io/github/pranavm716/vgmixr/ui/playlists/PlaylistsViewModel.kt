package io.github.pranavm716.vgmixr.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.domain.playlist.playableTrackIds
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState

    data class Loaded(val playlists: List<PlaylistSummary>) : PlaylistsUiState
}

/** One-off actions for the screen. */
sealed interface PlaylistsEvent {
    /** Open playlist [playlistId], as a tap on its row does. */
    data class Open(val playlistId: Long) : PlaylistsEvent
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

    private val eventChannel = Channel<PlaylistsEvent>(Channel.BUFFERED)
    val events: Flow<PlaylistsEvent> = eventChannel.receiveAsFlow()

    /** Makes a playlist named [name] and opens it; a blank name makes nothing. */
    fun onCreatePlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            eventChannel.send(PlaylistsEvent.Open(repository.createPlaylist(name)))
        }
    }

    /** Plays the whole playlist (a long press), in its order, leaving out tracks that can't play. */
    fun onPlayPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val playlist = repository.playlist(playlistId).first() ?: return@launch
            val ids = playlist.playableTrackIds(catalog.catalog.first())
            if (ids.isNotEmpty()) playback.playAll(ids, QueueContext.Playlist(playlistId))
        }
    }

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
