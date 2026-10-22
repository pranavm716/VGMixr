package io.github.pranavm716.vgmixr.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.playableFamilyTrackIds
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.search.SearchIndex
import io.github.pranavm716.vgmixr.domain.search.search
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [results] are the matches, best first; [noResults] is set when a typed query matches nothing.
 * An empty field shows neither.
 */
data class SearchUiState(
    val results: List<SearchRowUi> = emptyList(),
    val noResults: Boolean = false
)

/**
 * A result as the queue shows a track: title over all of its game chips. [unplayable] greys a
 * track whose sources are all broken; [playing] marks the current track.
 */
data class SearchRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val unplayable: Boolean = false,
    val playing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CatalogRepository,
    playHistory: PlayHistory,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    private val query = MutableStateFlow("")

    // Searched on every keystroke; a newer keystroke cancels a search still running.
    private val matches = combine(
        query,
        repository.catalog.map(::SearchIndex),
        playHistory.plays
    ) { query, index, plays -> Triple(query, index, plays) }
        .mapLatest { (query, index, plays) ->
            Matches(query, search(query, index, plays, clock.instant()))
        }
        .flowOn(dispatcher)

    val uiState: StateFlow<SearchUiState> =
        combine(
            matches,
            playback.state.map {
                it.trackId
            }.distinctUntilChanged()
        ) { matches, playing ->
            SearchUiState(
                results = matches.tracks.map { it.toRow(playing) },
                noResults = matches.query.isNotBlank() && matches.tracks.isEmpty()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    /** The search starts empty each time the tab is opened. */
    fun onLeave() = onQueryChange("")

    /**
     * Plays the track's family from it, as a tap in the family list does. A track whose sources
     * are all broken says so instead, on every tap.
     */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val catalog = repository.catalog.first()
            val track = catalog.tracks.firstOrNull { it.id == trackId } ?: return@launch
            val unplayable = track.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
                return@launch
            }
            val ids = catalog.playableFamilyTrackIds(track.familyId)
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Family(track.familyId))
        }
    }

    /** Adds one track to "Next in queue"; a greyed track can't be queued. */
    fun onQueueTrack(trackId: String) {
        viewModelScope.launch {
            val track = repository.catalog.first().tracks.firstOrNull { it.id == trackId }
            if (track == null || track.unplayable != null) return@launch
            playback.addToQueue(listOf(trackId))
            messages.post(UserMessage.AddedToQueue(1))
        }
    }
}

/** A search's [tracks] together with the [query] they answer, so the two never disagree. */
private class Matches(val query: String, val tracks: List<CatalogTrack>)

private fun CatalogTrack.toRow(playingId: String?) = SearchRowUi(
    id = id,
    title = title,
    chips = games.map { ChipUi(it.shortName, it.color) },
    unplayable = unplayable != null,
    playing = id == playingId
)
