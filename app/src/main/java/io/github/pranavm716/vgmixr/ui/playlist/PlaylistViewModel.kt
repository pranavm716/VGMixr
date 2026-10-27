package io.github.pranavm716.vgmixr.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.offline.OfflineProgress
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.offlineProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.playablePlaylistTrackIds
import io.github.pranavm716.vgmixr.domain.sort.playlistTracks
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.SortUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState

    data object NotFound : PlaylistUiState

    /** [playingIndex] is the playing track's row, when it is in this playlist. */
    data class Loaded(
        val name: String,
        val isFavorites: Boolean,
        val rows: List<PlaylistRowUi>,
        val playingIndex: Int? = null,
        val sort: SortUi = SortUi(SortKeys.playlistDefault, SortKeys.playlist)
    ) : PlaylistUiState {
        val trackCount: Int get() = rows.size
    }
}

/** One-off actions for the screen. */
sealed interface PlaylistEvent {
    /** The playlist was deleted: leave its page. */
    data object Closed : PlaylistEvent
}

/**
 * A track as search and the queue list it: title over all of its game chips. [unplayable] greys a
 * track whose sources are all broken; [playing] marks the current track. A user track has its
 * [thumbnail]'s path and its channel as [subtitle].
 */
data class PlaylistRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val unplayable: Boolean = false,
    val playing: Boolean = false,
    val thumbnail: String? = null,
    val subtitle: String? = null,
    val familyId: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val playlists: PlaylistRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    offline: OfflineStore = OfflineStore.NONE
) : ViewModel() {
    // The PlaylistRoute's argument, stored by type-safe navigation under its property name.
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    val uiState: StateFlow<PlaylistUiState> =
        combine(
            catalog.catalog,
            playlists.playlist(playlistId),
            playback.state.map { it.trackId }.distinctUntilChanged()
        ) { catalog, playlist, playingId -> catalog.playlistState(playlist, playingId) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState.Loading)

    /** How much of the playlist is downloaded, for the sort row's icon; null while loading. */
    val download: StateFlow<OfflineProgress?> =
        combine(catalog.catalog, playlists.playlist(playlistId), offline.playableOffline) {
                catalog,
                playlist,
                onPhone
            ->
            playlist?.let { offlineProgress(catalog.playlistTracks(it), onPhone) }
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Plays the playlist from the tapped track, in its sort, leaving out tracks that can't play. A
     * track whose sources are all broken says so instead, on every tap.
     */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val catalog = catalog.catalog.first()
            val track = catalog.trackOrRetired(trackId) ?: return@launch
            if (track.retired) {
                messages.post(UserMessage.TrackRetired(track.title))
                return@launch
            }
            if (track.offline == true) {
                messages.post(UserMessage.TrackOffline(track.title))
                return@launch
            }
            val unplayable = track.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
                return@launch
            }
            val playlist = playlists.playlist(playlistId).first() ?: return@launch
            val ids = catalog.playablePlaylistTrackIds(playlist)
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Playlist(playlistId))
        }
    }

    /** The sort menu: picking another key sorts by it, ascending. */
    fun onSortKey(key: SortKey) = changeSort { if (key == it.key) it else it.withKey(key) }

    /** The sort arrow: the same key the other way. */
    fun onReverseSort() = changeSort { it.reversed() }

    /** Saves the playlist's new sort; if it is playing, its "Next from" takes the new order. */
    private fun changeSort(change: (SortSetting) -> SortSetting) {
        viewModelScope.launch {
            val playlist = playlists.playlist(playlistId).first() ?: return@launch
            val sort = change(playlist.sort)
            if (sort == playlist.sort) return@launch
            playlists.setSort(playlistId, sort)
            val ids = catalog.catalog.first().playablePlaylistTrackIds(playlist.copy(sort = sort))
            playback.resort(ids, QueueContext.Playlist(playlistId))
        }
    }

    private val eventChannel = Channel<PlaylistEvent>(Channel.BUFFERED)
    val events: Flow<PlaylistEvent> = eventChannel.receiveAsFlow()

    /** Renames the playlist; a blank name renames nothing. */
    fun onRename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlists.renamePlaylist(playlistId, name) }
    }

    /** Deletes the playlist and its entries, then closes its page. */
    fun onDelete() {
        viewModelScope.launch {
            playlists.deletePlaylist(playlistId)
            eventChannel.send(PlaylistEvent.Closed)
        }
    }

    /** Adds one track to "Next in queue"; a greyed track can't be queued. */
    fun onQueueTrack(trackId: String) {
        viewModelScope.launch {
            val track = catalog.catalog.first().trackOrRetired(trackId)
            if (track == null || track.dimmed) return@launch
            playback.addToQueue(listOf(trackId))
            messages.post(UserMessage.AddedToQueue(1))
        }
    }
}

// Retired tracks are listed greyed; deleted remixes aren't listed.
private fun Catalog.playlistState(playlist: Playlist?, playingId: String?): PlaylistUiState {
    if (playlist == null) return PlaylistUiState.NotFound
    val rows = playlistTracks(playlist).map { track ->
        PlaylistRowUi(
            id = track.id,
            title = track.title,
            chips = track.games.map { ChipUi(it.shortName, it.color) },
            // A retired track that is playing plays to its end, as in the queue.
            unplayable = track.dimmed && !(track.retired && track.id == playingId),
            playing = track.id == playingId,
            thumbnail = track.thumbnail,
            subtitle = track.artist,
            familyId = track.familyId
        )
    }
    return PlaylistUiState.Loaded(
        name = playlist.name,
        isFavorites = playlist.isFavorites,
        rows = rows,
        playingIndex = rows.indexOfFirst { it.playing }.takeIf { it >= 0 },
        sort = SortUi(playlist.sort, SortKeys.playlist)
    )
}
