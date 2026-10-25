package io.github.pranavm716.vgmixr.ui.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.Art
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.CollectionPage
import io.github.pranavm716.vgmixr.ui.components.FavoritesHeaderArt
import io.github.pranavm716.vgmixr.ui.components.FullWidth
import io.github.pranavm716.vgmixr.ui.components.HeaderImage
import io.github.pranavm716.vgmixr.ui.components.PlainBarTint
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.components.playlistArtAsset
import io.github.pranavm716.vgmixr.ui.components.rememberArt
import io.github.pranavm716.vgmixr.ui.player.colorTint
import io.github.pranavm716.vgmixr.ui.playlists.PlaylistNameDialog
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder
import io.github.pranavm716.vgmixr.ui.theme.DialogBackground
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun PlaylistTab(
    onEditPlaylist: (name: String) -> Unit,
    onClose: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A deleted playlist's page closes, back to the playlist list.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlaylistEvent.Closed -> onClose()
            }
        }
    }
    PlaylistScreen(
        state,
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack,
        onEditPlaylist = onEditPlaylist,
        onRename = viewModel::onRename,
        onDelete = viewModel::onDelete
    )
}

/**
 * A playlist's tracks under a header like a family's (its art, Favorites' heart), listed as
 * search lists them: the family's tile, the title over all of its game chips. The header's menu
 * edits the playlist, renames it and deletes it (Favorites: edit only); deleting one with tracks
 * asks first.
 */
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    onEditPlaylist: (name: String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        PlaylistUiState.Loading -> Unit

        PlaylistUiState.NotFound ->
            Message(stringResource(R.string.playlist_not_found), modifier.statusBarsPadding())

        is PlaylistUiState.Loaded -> Box(modifier.fillMaxSize()) {
            if (state.rows.isEmpty()) {
                Message(stringResource(R.string.playlist_empty))
            }
            // Favorites is the accent with its heart; other playlists show their bundled art, or
            // the blank tile's gray without it. The shut bar takes the accent, the art's average
            // color, or no color.
            val art = rememberArt(
                if (state.isFavorites) null else playlistArtAsset(state.name),
                FullWidth
            )
            val accent = MaterialTheme.colorScheme.primary
            val barTint = when {
                state.isFavorites -> remember(accent) { colorTint(accent) }
                art is Art.Loaded -> remember(art) { colorTint(art.average) }
                else -> PlainBarTint
            }
            CollectionPage(
                state.name,
                state.trackCount,
                barTint = barTint,
                playingIndex = state.playingIndex,
                rowHeight = TileRowHeight,
                art = {
                    if (state.isFavorites) {
                        FavoritesHeaderArt()
                    } else {
                        HeaderImage(art, missing = SolidColor(ArtPlaceholder))
                    }
                },
                action = {
                    PlaylistMenu(
                        state,
                        onEdit = { onEditPlaylist(state.name) },
                        onRename = onRename,
                        onDelete = onDelete
                    )
                }
            ) {
                items(state.rows, key = { it.id }) { row ->
                    val content = @Composable {
                        TrackTileRow(
                            row.title,
                            row.chips,
                            row.unplayable,
                            current = row.playing,
                            onClick = { onTrackClick(row.id) },
                            thumbnail = row.thumbnail,
                            subtitle = row.subtitle,
                            familyId = row.familyId
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

/**
 * The header's three-dot menu: Edit, then Rename and Delete for the user's playlists. Its dialogs
 * live here too: the name to rename to, and the confirmation for deleting a playlist that has
 * tracks (an empty one goes at once).
 */
@Composable
private fun PlaylistMenu(
    state: PlaylistUiState.Loaded,
    onEdit: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DialogBackground
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                }
            )
            if (!state.isFavorites) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = {
                        expanded = false
                        renaming = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        expanded = false
                        if (state.trackCount > 0) confirmingDelete = true else onDelete()
                    }
                )
            }
        }
    }
    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(R.string.rename_playlist),
            confirmLabel = stringResource(R.string.rename),
            initialName = state.name,
            onConfirm = { name ->
                renaming = false
                onRename(name)
            },
            onDismiss = { renaming = false }
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = DialogBackground,
            title = { Text(stringResource(R.string.delete_playlist_title, state.name)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_playlist_body,
                        state.trackCount,
                        state.trackCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
            onQueueTrack = {},
            onEditPlaylist = {},
            onRename = {},
            onDelete = {}
        )
    }
}
