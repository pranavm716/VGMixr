package io.github.pranavm716.vgmixr.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.catalog.FamilySummary
import io.github.pranavm716.vgmixr.domain.catalog.familySummaries
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.settings.SortSettings
import io.github.pranavm716.vgmixr.domain.sort.playableFamilyTrackIds
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

sealed interface GamesUiState {
    data object Loading : GamesUiState

    /** [importFailed]: the bundled manifest didn't import, so this is the previous catalog. */
    data class Loaded(val families: List<FamilySummary>, val importFailed: Boolean) : GamesUiState
}

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val repository: CatalogRepository,
    sync: CatalogSync,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    private val sortSettings: SortSettings,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<GamesUiState> =
        combine(
            repository.catalog.map { it.familySummaries() }.flowOn(dispatcher),
            sync.status.map { it is CatalogImportResult.Failed }
        ) { families, importFailed -> GamesUiState.Loaded(families, importFailed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState.Loading)

    /**
     * Plays the whole family (a long press) in the family pages' sort, leaving out tracks whose
     * sources are all broken.
     */
    fun onPlayFamily(familyId: String) {
        viewModelScope.launch {
            val ids = repository.catalog.first()
                .playableFamilyTrackIds(familyId, sortSettings.games.value)
            if (ids.isNotEmpty()) playback.playAll(ids, QueueContext.Family(familyId))
        }
    }

    /**
     * Adds a family's tracks, in the family pages' sort, to "Next in queue", leaving out those
     * whose sources are all broken.
     */
    fun onQueueFamily(familyId: String) {
        viewModelScope.launch {
            val ids = repository.catalog.first()
                .playableFamilyTrackIds(familyId, sortSettings.games.value)
            if (ids.isEmpty()) return@launch
            playback.addToQueue(ids)
            messages.post(UserMessage.AddedToQueue(ids.size))
        }
    }
}
