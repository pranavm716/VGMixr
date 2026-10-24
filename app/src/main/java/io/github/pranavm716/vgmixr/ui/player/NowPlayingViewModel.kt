package io.github.pranavm716.vgmixr.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.homeContext
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackId
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NowPlayingUiState {
    data object Hidden : NowPlayingUiState

    /**
     * [chips] are all of the track's games, also when it plays in every game of its family.
     * [familyId] is the track's own family, which tapping the title opens wherever it plays from
     * (none for a user track that remixes no catalog track). A user track's title opens the Remixes
     * tab instead ([opensRemixes]).
     */
    data class Visible(
        val title: String,
        val chips: List<ChipUi>,
        val familyId: String?,
        val playingFrom: PlayingFromUi,
        val isPlaying: Boolean,
        val isLoading: Boolean,
        val isFavorite: Boolean = false,
        val shuffle: Boolean = false,
        val thumbnail: String? = null,
        val subtitle: String? = null,
        val opensRemixes: Boolean = false
    ) : NowPlayingUiState
}

/** Where the current track plays from, for the header's "Playing from". */
sealed interface PlayingFromUi {
    /** Tapped in a family's list; the header opens it. */
    data class Family(val familyId: String, val name: String) : PlayingFromUi

    /** Tapped in a playlist; the header opens it. */
    data class Playlist(val playlistId: Long, val name: String) : PlayingFromUi

    /** Added with swipe to queue. */
    data object Queue : PlayingFromUi

    /** Tapped in the Remixes tab; the header opens it. */
    data object Remixes : PlayingFromUi
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    repository: CatalogRepository,
    private val playback: PlaybackController,
    private val playlists: PlaylistRepository
) : ViewModel() {
    val uiState: StateFlow<NowPlayingUiState> =
        combine(
            repository.catalog,
            playback.state,
            playlists.favorites,
            playlists.playlists
        ) { catalog, state, favorites, playlists ->
            catalog.nowPlaying(state, favorites, playlists)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                NowPlayingUiState.Hidden
            )

    /** The seek bar's position and length, kept apart so its ticks redraw only the seek bar. */
    val progress: StateFlow<PlaybackProgress> =
        playback.progress.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackProgress(positionMs = 0, durationMs = null)
        )

    fun onPlayPause() = playback.togglePlayPause()

    /** Restarts the track a few seconds in, else goes back one (the mini player's swipe always does). */
    fun onPrevious() = playback.restartOrSkipToPrevious()

    fun onNext() = playback.skipToNext()

    fun onShuffle() = playback.setShuffle(!playback.state.value.shuffle)

    fun onSeek(positionMs: Long) = playback.seekTo(positionMs)

    /** Favorites the playing track, or unfavorites it if the heart shows it as one. */
    fun onToggleFavorite() {
        val trackId = playback.state.value.trackId ?: return
        val isFavorite = (uiState.value as? NowPlayingUiState.Visible)?.isFavorite ?: return
        viewModelScope.launch { playlists.setFavorite(trackId, !isFavorite) }
    }
}

private fun Catalog.nowPlaying(
    state: PlaybackState,
    favorites: Set<String>,
    playlists: List<PlaylistSummary>
): NowPlayingUiState {
    val track = state.trackId?.let { track(it) } ?: return NowPlayingUiState.Hidden
    // No context, or a playlist deleted since: the track plays from its family, or from Remixes
    // for a user track.
    val context = state.queue.entries.getOrNull(state.queue.current)?.context
        ?.takeUnless {
            it is QueueContext.Playlist && playlists.none { p -> p.id == it.playlistId }
        }
        ?: track.homeContext()
    val playingFrom = when (context) {
        is QueueContext.Family -> PlayingFromUi.Family(
            context.familyId,
            families.firstOrNull { it.id == context.familyId }?.name.orEmpty()
        )

        is QueueContext.Playlist -> PlayingFromUi.Playlist(
            context.playlistId,
            playlists.firstOrNull { it.id == context.playlistId }?.name.orEmpty()
        )

        QueueContext.Queue -> PlayingFromUi.Queue

        QueueContext.Remixes -> PlayingFromUi.Remixes
    }
    return NowPlayingUiState.Visible(
        title = track.title,
        chips = track.games.map { ChipUi(it.shortName, it.color) },
        familyId = track.familyId,
        thumbnail = track.thumbnail,
        subtitle = track.channel,
        opensRemixes = UserTrackId.isUserTrack(track.id),
        playingFrom = playingFrom,
        isPlaying = state.isPlaying,
        isLoading = state.isLoading,
        isFavorite = track.id in favorites,
        shuffle = state.shuffle
    )
}

/** A time as `m:ss`, or `h:mm:ss` from an hour on. */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val hours = total / 3600
    val minutes = total / 60 % 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
