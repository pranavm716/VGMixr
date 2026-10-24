package io.github.pranavm716.vgmixr.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.components.CollectionRow
import io.github.pranavm716.vgmixr.ui.components.RowTileSize
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnReselect
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun PlaylistsTab(
    onPlaylistClick: (Long) -> Unit,
    reselects: Int,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(reselects, listState)
    // A new playlist opens at once, as a tap on its row would.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistsEvent.Open -> onPlaylistClick(event.playlistId)
            }
        }
    }
    PlaylistsScreen(
        state,
        onPlaylistClick,
        onQueuePlaylist = viewModel::onQueuePlaylist,
        onPlayPlaylist = viewModel::onPlayPlaylist,
        onCreatePlaylist = viewModel::onCreatePlaylist,
        listState = listState
    )
}

/**
 * The Playlists tab: Favorites, then the user's playlists, in rows like the Games tab's. A swipe
 * right queues a playlist's tracks, as it queues a family's. The plus by the title asks for a name
 * and makes a new playlist.
 */
@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    onPlaylistClick: (Long) -> Unit,
    onQueuePlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    Column(modifier.fillMaxSize()) {
        var naming by rememberSaveable { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader("Playlists", Modifier.weight(1f))
            IconButton(onClick = { naming = true }, Modifier.padding(end = 4.dp)) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.new_playlist)
                )
            }
        }
        if (naming) {
            PlaylistNameDialog(
                title = stringResource(R.string.new_playlist),
                confirmLabel = stringResource(R.string.create),
                onConfirm = { name ->
                    naming = false
                    onCreatePlaylist(name)
                },
                onDismiss = { naming = false }
            )
        }
        if (state is PlaylistsUiState.Loaded) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.playlists, key = { it.id }) { playlist ->
                    SwipeToQueue(onQueue = { onQueuePlaylist(playlist.id) }) {
                        CollectionRow(
                            playlist.name,
                            playlist.trackCount,
                            art = { PlaylistArt(playlist.isFavorites, RowTileSize) },
                            onClick = { onPlaylistClick(playlist.id) },
                            onLongClick = { onPlayPlaylist(playlist.id) }
                        )
                    }
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
            ),
            onPlaylistClick = {},
            onQueuePlaylist = {},
            onPlayPlaylist = {},
            onCreatePlaylist = {}
        )
    }
}
