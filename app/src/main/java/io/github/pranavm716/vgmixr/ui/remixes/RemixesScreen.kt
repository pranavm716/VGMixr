package io.github.pranavm716.vgmixr.ui.remixes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.SORT_ROW_KEY
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnReselect
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnSortChange
import io.github.pranavm716.vgmixr.ui.components.SortRow
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.components.centerOn
import io.github.pranavm716.vgmixr.ui.theme.DialogBackground
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Remixes tab. [scrollsToPlaying] counts now playing's requests to show the playing track; each
 * new one scrolls it mid-screen, as a family opened from now playing does.
 */
@Composable
fun RemixesTab(
    reselects: Int,
    scrollsToPlaying: Int,
    onAddToPlaylist: (trackId: String) -> Unit,
    onOpenLink: (trackId: String) -> Unit,
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
        // The sort row comes first, so the tracks start at index 1.
        loaded.playingIndex?.let { listState.centerOn(it + 1, rowPx) }
    }
    val inMode = (state as? RemixesUiState.Loaded)?.mode.let {
        it != null &&
            it != RemixesMode.NORMAL
    }
    BackHandler(enabled = inMode, onBack = viewModel::onDone)
    RemixesScreen(
        state,
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack,
        onTrackLongClick = onAddToPlaylist,
        onStartLinking = viewModel::onStartLinking,
        onStartDeleting = viewModel::onStartDeleting,
        onDone = viewModel::onDone,
        onLink = onOpenLink,
        onDelete = viewModel::onDelete,
        listState = listState,
        onSortKey = viewModel::onSortKey,
        onReverseSort = viewModel::onReverseSort
    )
}

/**
 * The Remixes tab: the tracks added from YouTube, newest first, each with its video's thumbnail,
 * its original's game chips and its channel. A tap plays Remixes from that track, a swipe right
 * queues it and a long press opens Add to playlist for it. The menu by the title holds Link, which
 * gives every row a chevron and opens its Link page on a tap, and Delete, which gives every row a
 * trash can that deletes it; either lasts until Done (or back). The buttons slide in from the
 * right edge.
 */
@Composable
fun RemixesScreen(
    state: RemixesUiState,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    onTrackLongClick: (String) -> Unit,
    onStartLinking: () -> Unit,
    onStartDeleting: () -> Unit,
    onDone: () -> Unit,
    onLink: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onSortKey: (SortKey) -> Unit = {},
    onReverseSort: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        val loaded = state as? RemixesUiState.Loaded
        ScrollToTopOnSortChange(loaded?.sort, listState)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tapping the title scrolls the list back to the top.
            ScreenHeader(
                stringResource(R.string.playing_from_remixes),
                Modifier
                    .weight(1f)
                    .clickable(interactionSource = null, indication = null) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
            )
            when {
                loaded == null || loaded.rows.isEmpty() -> Unit

                loaded.mode != RemixesMode.NORMAL -> TextButton(
                    onClick = onDone,
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(stringResource(R.string.done)) }

                else -> RemixesMenu(onStartLinking, onStartDeleting, Modifier.padding(end = 4.dp))
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
            item(key = SORT_ROW_KEY) { SortRow(state.sort, onSortKey, onReverseSort) }
            items(state.rows, key = { it.id }) { row ->
                val mode = state.mode
                // One row in every mode, so its button can slide in. A greyed track can't be
                // queued, and in a mode nothing swipes.
                SwipeToQueue(
                    onQueue = { onQueueTrack(row.id) },
                    enabled = mode == RemixesMode.NORMAL && !row.unplayable
                ) {
                    TrackTileRow(
                        title = row.title,
                        chips = row.chips,
                        unplayable = row.unplayable,
                        current = row.playing,
                        onClick = when (mode) {
                            RemixesMode.NORMAL -> ({ onTrackClick(row.id) })
                            RemixesMode.LINK -> ({ onLink(row.id) })
                            RemixesMode.DELETE -> null
                        },
                        onLongClick = if (mode == RemixesMode.NORMAL) {
                            { onTrackLongClick(row.id) }
                        } else {
                            null
                        },
                        thumbnail = row.thumbnail,
                        subtitle = row.channel,
                        trailing = { ModeButton(mode, row.title, onDelete = { onDelete(row.id) }) }
                    )
                }
            }
        }
    }
}

/** The header's menu: Link and Delete. */
@Composable
private fun RemixesMenu(
    onStartLinking: () -> Unit,
    onStartDeleting: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                onClick = {
                    expanded = false
                    onStartLinking()
                }
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

/**
 * A row's end in [mode]: nothing normally; in link mode a chevron (the row's tap opens the Link
 * page); in delete mode a trash can, the only thing that deletes. It slides in from the right edge,
 * and the room it takes grows with it, so the title gives way smoothly.
 */
@Composable
private fun ModeButton(mode: RemixesMode, title: String, onDelete: () -> Unit) {
    // Kept while sliding out, so the leaving button doesn't change mid-slide.
    var shown by remember { mutableStateOf(mode) }
    if (mode != RemixesMode.NORMAL) shown = mode
    // With no button, the row's end keeps the 12dp more a row without one has.
    val endGap by animateDpAsState(
        if (mode ==
            RemixesMode.NORMAL
        ) {
            12.dp
        } else {
            0.dp
        },
        tween(MODE_SLIDE_MS)
    )
    Row {
        AnimatedVisibility(
            visible = mode != RemixesMode.NORMAL,
            enter =
                slideInHorizontally(ModeSlideInt) {
                    it
                } + expandHorizontally(tween(MODE_SLIDE_MS)) +
                    fadeIn(ModeSlide),
            exit =
                slideOutHorizontally(ModeSlideInt) {
                    it
                } + shrinkHorizontally(tween(MODE_SLIDE_MS)) +
                    fadeOut(ModeSlide)
        ) {
            when (shown) {
                RemixesMode.DELETE -> IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.delete_named, title)
                    )
                }

                // The row's tap does the linking; the chevron only shows where it leads.
                else -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                }
            }
        }
        Spacer(Modifier.width(endGap))
    }
}

private const val MODE_SLIDE_MS = 250
private val ModeSlide = tween<Float>(MODE_SLIDE_MS)
private val ModeSlideInt = tween<IntOffset>(MODE_SLIDE_MS)

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
            onStartLinking = {},
            onStartDeleting = {},
            onDone = {},
            onLink = {},
            onDelete = {}
        )
    }
}
