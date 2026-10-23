package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.RowTileGap
import io.github.pranavm716.vgmixr.ui.components.RowTileSize
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.components.ToggleAddButton
import io.github.pranavm716.vgmixr.ui.playlists.PlaylistArt
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun AddToPlaylist(onClose: () -> Unit, viewModel: AddToPlaylistViewModel = hiltViewModel()) {
    // Each opening takes the track playing then.
    LaunchedEffect(Unit) { viewModel.onOpen() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AddToPlaylistScreen(state, onToggle = viewModel::onToggle, onClose = onClose)
}

/**
 * "Add to playlist", over now playing as the queue opens: the queue's top bar with the track's title
 * under it, then every playlist (Favorites first) with its art and name, ending in a plus, or the
 * accent check where the track already is. Only the plus or check reacts.
 */
@Composable
fun AddToPlaylistScreen(
    state: AddToPlaylistUiState,
    onToggle: (Long) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.add_to_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
                Text(
                    state.trackTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.rows, key = { it.id }) { row ->
                PlaylistToggleRow(row, onToggle = { onToggle(row.id) })
            }
        }
    }
}

@Composable
private fun PlaylistToggleRow(row: AddToPlaylistRowUi, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TileRowHeight)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistArt(row.isFavorites, RowTileSize)
        Spacer(Modifier.width(RowTileGap))
        Text(
            row.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        ToggleAddButton(
            row.added,
            onClick = onToggle,
            addLabel = stringResource(R.string.add_to_named_playlist, row.name),
            removeLabel = stringResource(R.string.remove_from_named_playlist, row.name)
        )
    }
}

@Preview
@Composable
private fun AddToPlaylistScreenPreview() {
    VGMixrTheme {
        AddToPlaylistScreen(
            AddToPlaylistUiState(
                trackTitle = "Route 101",
                rows = listOf(
                    AddToPlaylistRowUi(1, "Favorites", isFavorites = true, added = true),
                    AddToPlaylistRowUi(7, "Hype", isFavorites = false, added = false)
                )
            ),
            onToggle = {},
            onClose = {}
        )
    }
}
