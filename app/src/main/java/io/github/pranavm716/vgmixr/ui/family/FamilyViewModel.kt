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
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface FamilyUiState {
    data object Loading : FamilyUiState

    /** The family isn't in the catalog (e.g. it was retired). */
    data object NotFound : FamilyUiState

    data class Loaded(val name: String, val trackCount: Int, val tracks: List<TrackRowUi>) :
        FamilyUiState
}

/** [number] is the track's position in the list. [chips] is empty for a track in all games. */
data class TrackRowUi(val id: String, val number: Int, val title: String, val chips: List<ChipUi>)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CatalogRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    // The FamilyRoute's argument, stored by type-safe navigation under its property name.
    private val familyId: String = checkNotNull(savedStateHandle["familyId"])

    val uiState: StateFlow<FamilyUiState> =
        repository.catalog
            .map { it.familyState(familyId) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyUiState.Loading)
}

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
