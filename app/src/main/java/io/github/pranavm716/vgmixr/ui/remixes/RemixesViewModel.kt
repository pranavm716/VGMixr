package io.github.pranavm716.vgmixr.ui.remixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RemixesUiState {
    data object Loading : RemixesUiState

    /** The user tracks, newest first; none shows the empty state. */
    data class Loaded(val rows: List<RemixRowUi>) : RemixesUiState
}

/**
 * A user track's row: its title over its original's game chips (none if it remixes nothing) and
 * its [channel]. [thumbnail] is its thumbnail file's path, null until it is downloaded.
 */
data class RemixRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val channel: String?,
    val thumbnail: String?,
    val unplayable: Boolean = false,
    val playing: Boolean = false
)

@HiltViewModel
class RemixesViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<RemixesUiState> =
        combine(
            catalog.catalog,
            playback.state.map { it.trackId }.distinctUntilChanged()
        ) { catalog, playingId ->
            RemixesUiState.Loaded(catalog.userTracks.map { it.toRow(playingId) })
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemixesUiState.Loading)

    /**
     * Plays Remixes from the tapped track, as listed, leaving out tracks that can't play. A track
     * whose video is broken says so instead, on every tap.
     */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val catalog = catalog.catalog.first()
            val track = catalog.track(trackId) ?: return@launch
            val unplayable = track.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
                return@launch
            }
            val ids = catalog.playableUserTrackIds()
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Remixes)
        }
    }

    /** Adds one track to "Next in queue"; a greyed track can't be queued. */
    fun onQueueTrack(trackId: String) {
        viewModelScope.launch {
            val track = catalog.catalog.first().track(trackId)
            if (track == null || track.unplayable != null) return@launch
            playback.addToQueue(listOf(trackId))
            messages.post(UserMessage.AddedToQueue(1))
        }
    }

    private fun CatalogTrack.toRow(playingId: String?) = RemixRowUi(
        id = id,
        title = title,
        chips = games.map { ChipUi(it.shortName, it.color) },
        channel = channel,
        thumbnail = thumbnail,
        unplayable = unplayable != null,
        playing = id == playingId
    )
}

private fun Catalog.playableUserTrackIds(): List<String> =
    userTracks.filter { it.unplayable == null }.map { it.id }
