package io.github.pranavm716.vgmixr.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Add to playlist": the track it was opened for, and every playlist with a check where it is. */
data class AddToPlaylistUiState(
    val trackTitle: String = "",
    val rows: List<AddToPlaylistRowUi> = emptyList()
)

/** A playlist row: its art (Favorites' heart or the blank tile), name, and plus or check. */
data class AddToPlaylistRowUi(
    val id: Long,
    val name: String,
    val isFavorites: Boolean,
    val added: Boolean
)

/**
 * Adds the playing track to playlists, or takes it out. The track is the one playing when the
 * screen opened ([onOpen]), so a song ending meanwhile doesn't change what the checks apply to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val playlists: PlaylistRepository,
    private val playback: PlaybackController
) : ViewModel() {
    private val trackId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AddToPlaylistUiState> =
        trackId.flatMapLatest { id ->
            if (id == null) {
                flowOf(AddToPlaylistUiState())
            } else {
                combine(
                    repository.catalog,
                    playlists.playlists,
                    playlists.playlistsContaining(id)
                ) { catalog, summaries, containing ->
                    AddToPlaylistUiState(
                        trackTitle = catalog.tracks.firstOrNull { it.id == id }?.title.orEmpty(),
                        rows = summaries.map {
                            AddToPlaylistRowUi(it.id, it.name, it.isFavorites, it.id in containing)
                        }
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddToPlaylistUiState())

    /** Takes the playing track as the one to add; called each time the screen opens. */
    fun onOpen() {
        trackId.value = playback.state.value.trackId
    }

    /** The plus adds the track to playlist [playlistId]; the check takes it out. */
    fun onToggle(playlistId: Long) {
        val id = trackId.value ?: return
        val added = uiState.value.rows.firstOrNull { it.id == playlistId }?.added ?: return
        viewModelScope.launch { playlists.setInPlaylist(playlistId, id, !added) }
    }
}
