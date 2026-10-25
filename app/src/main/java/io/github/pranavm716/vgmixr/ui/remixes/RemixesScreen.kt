package io.github.pranavm716.vgmixr.ui.remixes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnReselect
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.components.centerOn
import io.github.pranavm716.vgmixr.ui.theme.DialogBackground
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

/**
 * The Remixes tab. [scrollsToPlaying] counts now playing's requests to show the playing track; each
 * new one scrolls it mid-screen, as a family opened from now playing does.
 */
@Composable
fun RemixesTab(
    reselects: Int,
    scrollsToPlaying: Int,
    onAddToPlaylist: (trackId: String) -> Unit,
    viewModel: RemixesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(reselects, listState)
    var handledScrolls by rememberSaveable { mutableIntStateOf(0) }
    val rowPx = with(LocalDensity.current) { TileRowHeight.roundToPx() }
    LaunchedEffect(scrollsToPlaying) {
        if (scrollsToPlaying == handledScrolls) return@LaunchedEffect
        handledScrolls = scrollsToPlaying
        val loaded = snapshotFlow { state }.filterIsInstance<RemixesUiState.Loaded>().first()
        loaded.playingIndex?.let { listState.centerOn(it, rowPx) }
    }
    val deleting = (state as? RemixesUiState.Loaded)?.deleting == true
    BackHandler(enabled = deleting, onBack = viewModel::onDoneDeleting)
    RemixesScreen(
        state,
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack,
        onTrackLongClick = onAddToPlaylist,
        onStartDeleting = viewModel::onStartDeleting,
        onDoneDeleting = viewModel::onDoneDeleting,
        onDelete = viewModel::onDelete,
        listState = listState
    )
}

/**
 * The Remixes tab: the tracks added from YouTube, newest first, each with its video's thumbnail,
 * its original's game chips and its channel. A tap plays Remixes from that track, a swipe right
 * queues it and a long press opens Add to playlist for it. The menu by the title holds Link (not
 * wired yet) and Delete, which gives every row a delete button until Done (or back).
 */
@Composable
fun RemixesScreen(
    state: RemixesUiState,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    onTrackLongClick: (String) -> Unit,
    onStartDeleting: () -> Unit,
    onDoneDeleting: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    Column(modifier.fillMaxSize()) {
        val loaded = state as? RemixesUiState.Loaded
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(stringResource(R.string.playing_from_remixes), Modifier.weight(1f))
            when {
                loaded == null || loaded.rows.isEmpty() -> Unit

                loaded.deleting -> TextButton(
                    onClick = onDoneDeleting,
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(stringResource(R.string.done)) }

                else -> RemixesMenu(onStartDeleting, Modifier.padding(end = 4.dp))
            }
        }
        if (state !is RemixesUiState.Loaded) return@Column
        if (state.rows.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.remixes_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(state.rows, key = { it.id }) { row ->
                if (state.deleting) {
                    DeletableRow(row, onDelete = { onDelete(row.id) })
                    return@items
                }
                val tile = @Composable {
                    TrackTileRow(
                        title = row.title,
                        chips = row.chips,
                        unplayable = row.unplayable,
                        current = row.playing,
                        onClick = { onTrackClick(row.id) },
                        onLongClick = { onTrackLongClick(row.id) },
                        thumbnail = row.thumbnail,
                        subtitle = row.channel
                    )
                }
                // A greyed track can't be queued, so its row doesn't swipe.
                if (row.unplayable) {
                    tile()
                } else {
                    SwipeToQueue(onQueue = { onQueueTrack(row.id) }, content = tile)
                }
            }
        }
    }
}

/** The header's menu: Link (not wired yet) and Delete. */
@Composable
private fun RemixesMenu(onStartDeleting: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
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
                text = { Text(stringResource(R.string.link)) },
                onClick = { expanded = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    onStartDeleting()
                }
            )
        }
    }
}

/** A row in delete mode: only its trash button reacts, deleting the track at once. */
@Composable
private fun DeletableRow(row: RemixRowUi, onDelete: () -> Unit) {
    TrackTileRow(
        title = row.title,
        chips = row.chips,
        unplayable = row.unplayable,
        current = row.playing,
        onClick = null,
        thumbnail = row.thumbnail,
        subtitle = row.channel,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete_named, row.title)
                )
            }
        }
    )
}

@Preview
@Composable
private fun RemixesScreenPreview() {
    VGMixrTheme {
        RemixesScreen(
            RemixesUiState.Loaded(
                listOf(
                    RemixRowUi(
                        "yt:a",
                        "Pokémon Gold and Silver - Olivine Lighthouse (Remix)",
                        listOf(ChipUi("G", "#DAA520"), ChipUi("S", "#C0C0C0")),
                        channel = "GlitchxCity",
                        thumbnail = null
                    ),
                    RemixRowUi("yt:b", "Some Remix", emptyList(), "Someone", null, playing = true)
                )
            ),
            onTrackClick = {},
            onQueueTrack = {},
            onTrackLongClick = {},
            onStartDeleting = {},
            onDoneDeleting = {},
            onDelete = {}
        )
    }
}
