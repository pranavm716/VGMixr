package io.github.pranavm716.vgmixr.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.search.SearchIndex
import io.github.pranavm716.vgmixr.domain.search.search
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.search.SEARCH_DELAY_MS
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
data class EditPlaylistUiState(
    val playlistName: String = "",
    val results: List<EditPlaylistRowUi> = emptyList(),
    val noResults: Boolean = false,
    // The query [results] answer ("" for the playlist's own tracks), so the list can tell new
    // results from a toggle or a removal.
    val resultsQuery: String = ""
)

/**
 * A match as search lists it, with all of its game chips. [added]: the playlist has it, so its
 * button shows a check instead of a plus.
 */
data class EditPlaylistRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val unplayable: Boolean = false,
    val added: Boolean = false,
    val thumbnail: String? = null,
    val subtitle: String? = null,
    val familyId: String? = null
)

/** Finds tracks with the Search tab's search and adds them to (or takes them out of) a playlist. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository,
    private val playlists: PlaylistRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : ViewModel() {
    // The EditPlaylistRoute's argument, stored by type-safe navigation under its property name.
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    // The name the playlist page already showed, so the title is whole on the first frame instead
    // of waiting for the playlist to load.
    private val openedName: String = savedStateHandle["playlistName"] ?: ""

    private val query = MutableStateFlow("")

    // As in the Search tab: searched once typing pauses, an emptied field clears at once. Text
    // alone ranks: a track worth adding is rarely in another playlist already, and history would
    // mostly lift tracks the playlist has.
    private val matches = combine(
        query.debounce { if (it.isBlank()) 0L else SEARCH_DELAY_MS },
        repository.catalog.map(::SearchIndex)
    ) { query, index -> Matches(query, search(query, index)) }
        .flowOn(dispatcher)

    // An empty field lists the playlist's own tracks (newest first, all checked), so unchecking one
    // takes its row away; a typed query lists the matches instead.
    val uiState: StateFlow<EditPlaylistUiState> =
        combine(matches, playlists.playlist(playlistId), repository.catalog) {
                matches,
                playlist,
                catalog
            ->
            val ids = playlist?.trackIds.orEmpty()
            val added = ids.toSet()
            val tracks = if (matches.query.isBlank()) {
                ids.mapNotNull(catalog::track)
            } else {
                matches.tracks
            }
            EditPlaylistUiState(
                playlistName = playlist?.name ?: openedName,
                results = tracks.map { it.toRow(it.id in added) },
                noResults = matches.query.isNotBlank() && matches.tracks.isEmpty(),
                resultsQuery = matches.query
            )
        }.flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                EditPlaylistUiState(playlistName = openedName)
            )

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

private fun CatalogTrack.toRow(added: Boolean) = EditPlaylistRowUi(
    id = id,
    title = title,
    chips = games.map { ChipUi(it.shortName, it.color) },
    unplayable = dimmed,
    added = added,
    thumbnail = thumbnail,
    subtitle = channel,
    familyId = familyId
)
