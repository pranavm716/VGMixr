package io.github.pranavm716.vgmixr.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.CollectionRow
import io.github.pranavm716.vgmixr.ui.components.FavoritesArt
import io.github.pranavm716.vgmixr.ui.components.RowTileSize
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnReselect
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun PlaylistsTab(reselects: Int, viewModel: PlaylistsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(reselects, listState)
    PlaylistsScreen(state, listState = listState)
}

/** The Playlists tab: Favorites, then the user's playlists, in rows like the Games tab's. */
@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Playlists")
        if (state is PlaylistsUiState.Loaded) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.playlists, key = { it.id }) { playlist ->
                    CollectionRow(
                        playlist.name,
                        playlist.trackCount,
                        art = {
                            if (playlist.isFavorites) {
                                FavoritesArt(RowTileSize)
                            } else {
                                ArtTile(RowTileSize)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PlaylistsScreenPreview() {
    VGMixrTheme {
        PlaylistsScreen(
            PlaylistsUiState.Loaded(
                listOf(
                    PlaylistSummary(1, "Favorites", 0, isFavorites = true),
                    PlaylistSummary(2, "Route themes", 1, isFavorites = false),
                    PlaylistSummary(3, "Battle!", 24, isFavorites = false)
                )
            )
        )
    }
}
