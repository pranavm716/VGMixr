package io.github.pranavm716.vgmixr.ui.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.CollectionHeader
import io.github.pranavm716.vgmixr.ui.components.RowTileGap
import io.github.pranavm716.vgmixr.ui.components.RowTileSize
import io.github.pranavm716.vgmixr.ui.components.ScrollToPlayingOnce
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.playlists.PlaylistArt
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun PlaylistTab(viewModel: PlaylistViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistScreen(
        state,
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack
    )
}

/**
 * A playlist's tracks under a header like a family's (on the page's own background), listed as
 * search lists them: the family's tile, the title over all of its game chips.
 */
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        PlaylistUiState.Loading -> Unit

        PlaylistUiState.NotFound ->
            Message(stringResource(R.string.playlist_not_found), modifier.statusBarsPadding())

        is PlaylistUiState.Loaded -> Column(modifier.fillMaxSize()) {
            // The tile and the name line up with the rows' tiles and titles below.
            CollectionHeader(
                state.name,
                state.trackCount,
                art = { PlaylistArt(state.isFavorites, RowTileSize) },
                gap = RowTileGap
            )
            if (state.rows.isEmpty()) {
                Message(
                    stringResource(
                        if (state.isFavorites) R.string.favorites_empty else R.string.playlist_empty
                    )
                )
            }
            val listState = rememberLazyListState()
            ScrollToPlayingOnce(listState, state.playingIndex, TileRowHeight)
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.rows, key = { it.id }) { row ->
                    val content = @Composable {
                        TrackTileRow(
                            row.title,
                            row.chips,
                            row.unplayable,
                            current = row.playing,
                            onClick = { onTrackClick(row.id) }
                        )
                    }
                    // A greyed track can't be queued, so its row doesn't swipe.
                    if (row.unplayable) {
                        content()
                    } else {
                        SwipeToQueue(onQueue = { onQueueTrack(row.id) }, content = content)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun PlaylistScreenPreview() {
    val ruby = ChipUi("R", "#CD2236")
    val sapphire = ChipUi("S", "#3D51A7")
    VGMixrTheme {
        PlaylistScreen(
            PlaylistUiState.Loaded(
                name = "Favorites",
                isFavorites = true,
                rows = listOf(
                    PlaylistRowUi("a", "Route 101", listOf(ruby, sapphire), playing = true),
                    PlaylistRowUi("b", "Battle! (Team Aqua)", listOf(sapphire), unplayable = true),
                    PlaylistRowUi("c", "Opening Movie (Red & Blue)", listOf(ChipUi("R", "#C8372D")))
                ),
                playingIndex = 0
            ),
            onTrackClick = {},
            onQueueTrack = {}
        )
    }
}
