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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface GamesUiState {
    data object Loading : GamesUiState

    /** [importFailed]: the bundled manifest didn't import, so this is the previous catalog. */
    data class Loaded(val families: List<FamilySummary>, val importFailed: Boolean) : GamesUiState
}

@HiltViewModel
class GamesViewModel @Inject constructor(
    repository: CatalogRepository,
    sync: CatalogSync,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    val uiState: StateFlow<GamesUiState> =
        combine(
            repository.catalog.map { it.familySummaries() }.flowOn(dispatcher),
            sync.status.map { it is CatalogImportResult.Failed }
        ) { families, importFailed -> GamesUiState.Loaded(families, importFailed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState.Loading)
}
