package io.github.pranavm716.vgmixr.ui.family

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.TrackFilter
import io.github.pranavm716.vgmixr.domain.catalog.filter
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FamilyUiState {
    data object Loading : FamilyUiState

    /** The family isn't in the catalog (e.g. it was retired). */
    data object NotFound : FamilyUiState

    /** [playing] is the current track when it is in this family. */
    data class Loaded(
        val name: String,
        val trackCount: Int,
        val tracks: List<TrackRowUi>,
        val playing: PlayingRowUi? = null
    ) : FamilyUiState
}

/** The current track's row: [index] in the list, and whether it still waits for its stream. */
data class PlayingRowUi(val trackId: String, val index: Int, val loading: Boolean)

/** [number] is the track's position in the list. [chips] is empty for a track in all games. */
data class TrackRowUi(val id: String, val number: Int, val title: String, val chips: List<ChipUi>)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    // The FamilyRoute's argument, stored by type-safe navigation under its property name.
    private val familyId: String = checkNotNull(savedStateHandle["familyId"])

    val uiState: StateFlow<FamilyUiState> =
        repository.catalog
            .map { it.familyState(familyId) }
            .combine(playback.state) { state, playback -> state.withPlaying(playback) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyUiState.Loading)

    /** Plays the family's list from the tapped track, as shown. */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val ids = repository.catalog.first().familyTrackIds(familyId)
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Family(familyId))
        }
    }

    /** Adds one track to "Next in queue". */
    fun onQueueTrack(trackId: String) {
        playback.addToQueue(listOf(trackId))
        messages.post(UserMessage.AddedToQueue(1))
    }
}

private fun FamilyUiState.withPlaying(playback: PlaybackState): FamilyUiState {
    if (this !is FamilyUiState.Loaded) return this
    val index = tracks.indexOfFirst { it.id == playback.trackId }
    val playing = if (index < 0) {
        null
    } else {
        PlayingRowUi(tracks[index].id, index, playback.isLoading)
    }
    return copy(playing = playing)
}

private fun Catalog.familyTrackIds(familyId: String): List<String> =
    filter(TrackFilter(family = familyId)).map { it.id }

private fun Catalog.familyState(familyId: String): FamilyUiState {
    val family = families.firstOrNull { it.id == familyId } ?: return FamilyUiState.NotFound
    val familyGames = games.filter { it.familyId == familyId }.map { it.id }.toSet()
    val tracks = filter(TrackFilter(family = familyId)).mapIndexed { index, track ->
        val playsEverywhere = track.games.map { it.id }.toSet() == familyGames
        TrackRowUi(
            id = track.id,
            number = index + 1,
            title = track.title,
            chips = if (playsEverywhere) {
                emptyList()
            } else {
                track.games.map { ChipUi(it.shortName, it.color) }
            }
        )
    }
    return FamilyUiState.Loaded(family.name, tracks.size, tracks)
}
