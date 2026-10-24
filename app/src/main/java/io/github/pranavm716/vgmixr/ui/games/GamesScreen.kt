package io.github.pranavm716.vgmixr.ui.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.catalog.FamilySummary
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.CollectionRow
import io.github.pranavm716.vgmixr.ui.components.RowTileSize
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnReselect
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun GamesTab(
    onFamilyClick: (String) -> Unit,
    reselects: Int,
    viewModel: GamesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(reselects, listState)
    GamesScreen(
        state,
        onFamilyClick,
        onQueueFamily = viewModel::onQueueFamily,
        onPlayFamily = viewModel::onPlayFamily,
        listState = listState
    )
}

/** The Games tab: one row per family, styled after Shuttle's album list. */
@Composable
fun GamesScreen(
    state: GamesUiState,
    onFamilyClick: (String) -> Unit,
    onQueueFamily: (String) -> Unit,
    onPlayFamily: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Games")
        when (state) {
            GamesUiState.Loading -> Unit

            is GamesUiState.Loaded -> {
                if (state.importFailed) ImportWarning()
                if (state.families.isEmpty()) {
                    Empty()
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(state.families, key = { it.id }) { family ->
                            SwipeToQueue(onQueue = { onQueueFamily(family.id) }) {
                                CollectionRow(
                                    family.name,
                                    family.trackCount,
                                    art = { ArtTile(RowTileSize) },
                                    onClick = { onFamilyClick(family.id) },
                                    onLongClick = { onPlayFamily(family.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportWarning() {
    Text(
        stringResource(R.string.import_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun Empty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.games_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun GamesScreenPreview() {
    VGMixrTheme {
        GamesScreen(
            GamesUiState.Loaded(
                families = listOf(
                    FamilySummary("rby", "Red / Blue / Yellow", 49),
                    FamilySummary("lgpe", "Let's Go, Pikachu! / Let's Go, Eevee!", 56),
                    FamilySummary("pla", "Legends: Arceus", 1)
                ),
                importFailed = true
            ),
            onFamilyClick = {},
            onQueueFamily = {},
            onPlayFamily = {}
        )
    }
}
