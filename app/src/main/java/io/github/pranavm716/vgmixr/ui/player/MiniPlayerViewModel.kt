package io.github.pranavm716.vgmixr.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MiniPlayerUiState {
    data object Hidden : MiniPlayerUiState

    /**
     * [previous] and [next] are the neighbouring queue items, drawn beside the bar while it is
     * swiped; null at the queue's ends.
     */
    data class Visible(
        val track: MiniTrackUi,
        val previous: MiniTrackUi?,
        val next: MiniTrackUi?,
        val isPlaying: Boolean,
        val isLoading: Boolean
    ) : MiniPlayerUiState
}

/** A track as the mini player shows it. [chips] are all of its games, also when it plays in every game. */
data class MiniTrackUi(
    val trackId: String,
    val familyId: String?,
    val title: String,
    val chips: List<ChipUi>,
    val thumbnail: String? = null,
    val subtitle: String? = null
)

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    repository: CatalogRepository,
    private val playback: PlaybackController
) : ViewModel() {
    val uiState: StateFlow<MiniPlayerUiState> =
        combine(repository.catalog, playback.state) { catalog, state -> catalog.miniPlayer(state) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MiniPlayerUiState.Hidden
            )

    /** The share of the current track played, 0 to 1; kept apart so its ticks redraw only the bar. */
    val progress: StateFlow<Float> =
        playback.progress
            .map { p ->
                val duration = p.durationMs?.takeIf { it > 0 } ?: return@map 0f
                (p.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    fun onPlayPause() = playback.togglePlayPause()

    fun onNext() = playback.skipToNext()

    fun onPrevious() = playback.skipToPrevious()
}

private fun Catalog.miniPlayer(state: PlaybackState): MiniPlayerUiState {
    val track = miniTrack(state.trackId) ?: return MiniPlayerUiState.Hidden
    val queue = state.queue
    return MiniPlayerUiState.Visible(
        track = track,
        previous = miniTrack(queue.entries.getOrNull(queue.current - 1)?.trackId),
        next = miniTrack(queue.entries.getOrNull(queue.current + 1)?.trackId),
        isPlaying = state.isPlaying,
        isLoading = state.isLoading
    )
}

private fun Catalog.miniTrack(trackId: String?): MiniTrackUi? {
    val track = trackId?.let { trackOrRetired(it) } ?: return null
    return MiniTrackUi(
        trackId = track.id,
        familyId = track.familyId,
        title = track.title,
        chips = track.games.map { ChipUi(it.shortName, it.color) },
        thumbnail = track.thumbnail,
        subtitle = track.channel
    )
}
