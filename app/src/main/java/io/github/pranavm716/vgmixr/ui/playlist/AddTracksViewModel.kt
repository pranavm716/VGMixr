package io.github.pranavm716.vgmixr.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.search.SearchIndex
import io.github.pranavm716.vgmixr.domain.search.search
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.search.SEARCH_DELAY_MS
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Add to <playlist>": [results] are the search's matches, best first; [noResults] is set when a
 * typed query matches nothing. An empty field shows neither.
 */
data class AddTracksUiState(
    val playlistName: String = "",
    val results: List<AddTrackRowUi> = emptyList(),
    val noResults: Boolean = false
)

/**
 * A match as search lists it, with all of its game chips. [added]: the playlist has it, so its
 * button shows a check instead of a plus.
 */
data class AddTrackRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val unplayable: Boolean = false,
    val added: Boolean = false
)

/** Finds tracks with the Search tab's search and adds them to (or takes them out of) a playlist. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddTracksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CatalogRepository,
    private val playlists: PlaylistRepository,
    playHistory: PlayHistory,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    // The AddTracksRoute's argument, stored by type-safe navigation under its property name.
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val query = MutableStateFlow("")

    // As in the Search tab: searched once typing pauses, an emptied field clears at once.
    private val matches = combine(
        query.debounce { if (it.isBlank()) 0L else SEARCH_DELAY_MS },
        repository.catalog.map(::SearchIndex),
        playHistory.plays
    ) { query, index, plays -> Matches(query, search(query, index, plays, clock.instant())) }
        .flowOn(dispatcher)

    val uiState: StateFlow<AddTracksUiState> =
        combine(matches, playlists.playlist(playlistId)) { matches, playlist ->
            val added = playlist?.trackIds.orEmpty().toSet()
            AddTracksUiState(
                playlistName = playlist?.name.orEmpty(),
                results = matches.tracks.map { it.toRow(it.id in added) },
                noResults = matches.query.isNotBlank() && matches.tracks.isEmpty()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddTracksUiState())

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    /** The plus adds the track; on a track the playlist has, the check takes it out. */
    fun onToggle(trackId: String) {
        val added = uiState.value.results.firstOrNull { it.id == trackId }?.added ?: return
        viewModelScope.launch { playlists.setInPlaylist(playlistId, trackId, !added) }
    }
}

/** A search's [tracks] together with the [query] they answer. */
private class Matches(val query: String, val tracks: List<CatalogTrack>)

private fun CatalogTrack.toRow(added: Boolean) = AddTrackRowUi(
    id = id,
    title = title,
    chips = games.map { ChipUi(it.shortName, it.color) },
    unplayable = unplayable != null,
    added = added
)
