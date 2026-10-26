package io.github.pranavm716.vgmixr.ui.family

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.TrackFilter
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.catalog.filter
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.search.BrowsedFamily
import io.github.pranavm716.vgmixr.domain.settings.SortSettings
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.familyTracks
import io.github.pranavm716.vgmixr.domain.sort.playableFamilyTrackIds
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.SortUi
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

    /**
     * [games] are the family's games, whose colors tint the header. [playing] is the current track
     * when it is in this family.
     */
    data class Loaded(
        val name: String,
        val trackCount: Int,
        val games: List<ChipUi>,
        val tracks: List<TrackRowUi>,
        val playing: PlayingRowUi? = null,
        val familyId: String? = null,
        val sort: SortUi = SortUi(SortKeys.familyDefault, SortKeys.family)
    ) : FamilyUiState
}

/** The current track's row: [index] in the list, and whether it still waits for its stream. */
data class PlayingRowUi(val trackId: String, val index: Int, val loading: Boolean)

/**
 * [number] is the track's place in the family's own order, whatever the sort. [chips] is empty for a track in all games.
 * [unplayable] greys a track whose sources are all broken.
 */
data class TrackRowUi(
    val id: String,
    val number: Int,
    val title: String,
    val chips: List<ChipUi>,
    val unplayable: Boolean = false
)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    browsed: BrowsedFamily,
    private val sortSettings: SortSettings,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    // The FamilyRoute's argument, stored by type-safe navigation under its property name.
    private val familyId: String = checkNotNull(savedStateHandle["familyId"])

    // Search lifts the family browsed last a little.
    init {
        browsed.opened(familyId)
    }

    val uiState: StateFlow<FamilyUiState> =
        combine(repository.catalog, sortSettings.games) { catalog, sort ->
            catalog.familyState(familyId, sort)
        }
            .combine(playback.state) { state, playback -> state.withPlaying(playback) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyUiState.Loading)

    /**
     * Plays the family's list from the tapped track, in its sort. A track whose sources are all
     * broken says so instead, on every tap.
     */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val catalog = repository.catalog.first()
            val track = catalog.track(trackId)
            if (track?.offline == true) {
                messages.post(UserMessage.TrackOffline(track.title))
                return@launch
            }
            val unplayable = track?.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
                return@launch
            }
            val ids = catalog.playableFamilyTrackIds(familyId, sortSettings.games.value)
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Family(familyId))
        }
    }

    /** The sort menu: picking another key sorts by it, ascending. */
    fun onSortKey(key: SortKey) {
        val sort = sortSettings.games.value
        if (key != sort.key) changeSort(sort.withKey(key))
    }

    /** The sort arrow: the same key the other way. */
    fun onReverseSort() = changeSort(sortSettings.games.value.reversed())

    /**
     * Every family page shares the sort, so the family playing (or else this one) follows it: its
     * "Next from" takes the new order.
     */
    private fun changeSort(sort: SortSetting) {
        sortSettings.setGames(sort)
        viewModelScope.launch {
            val playing = playback.state.value.queue.playingContext as? QueueContext.Family
            val context = playing ?: QueueContext.Family(familyId)
            val ids = repository.catalog.first().playableFamilyTrackIds(context.familyId, sort)
            playback.resort(ids, context)
        }
    }

    /** Adds one track to "Next in queue". */
    fun onQueueTrack(trackId: String) {
        viewModelScope.launch {
            val track = repository.catalog.first().track(trackId)
            // A greyed row can't be swiped; this keeps any other path from queueing it.
            if (track == null || track.dimmed) return@launch
            playback.addToQueue(listOf(trackId))
            messages.post(UserMessage.AddedToQueue(1))
        }
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

private fun Catalog.familyState(familyId: String, sort: SortSetting): FamilyUiState {
    val family = families.firstOrNull { it.id == familyId } ?: return FamilyUiState.NotFound
    val gamesInFamily = games.filter { it.familyId == familyId }
    val familyGames = gamesInFamily.map { it.id }.toSet()
    val number = filter(TrackFilter(family = familyId))
        .withIndex()
        .associate { (index, track) -> track.id to index + 1 }
    val tracks = familyTracks(familyId, sort).map { track ->
        val playsEverywhere = track.games.map { it.id }.toSet() == familyGames
        TrackRowUi(
            id = track.id,
            number = number.getValue(track.id),
            title = track.title,
            chips = if (playsEverywhere) {
                emptyList()
            } else {
                track.games.map { ChipUi(it.shortName, it.color) }
            },
            unplayable = track.dimmed
        )
    }
    return FamilyUiState.Loaded(
        family.name,
        tracks.size,
        gamesInFamily.map { ChipUi(it.shortName, it.color) },
        tracks,
        familyId = familyId,
        sort = SortUi(sort, SortKeys.family)
    )
}
