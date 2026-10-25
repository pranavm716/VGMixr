package io.github.pranavm716.vgmixr.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.NoSearchResults
import io.github.pranavm716.vgmixr.ui.components.SearchField
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

/**
 * The Search tab. [reselects] counts taps on the tab while it is open; each one, like opening the
 * tab, focuses the field and brings up the keyboard.
 */
@Composable
fun SearchTab(
    reselects: Int,
    onAddToPlaylist: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The field's text is kept here, so typing never waits on the ViewModel. Leaving the tab
    // disposes it, and the search starts empty next time.
    var query by remember { mutableStateOf("") }
    DisposableEffect(viewModel) { onDispose { viewModel.onLeave() } }
    SearchScreen(
        state = state,
        query = query,
        reselects = reselects,
        onQueryChange = {
            query = it
            viewModel.onQueryChange(it)
        },
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack,
        onTrackLongClick = onAddToPlaylist
    )
}

/**
 * A search field as the top bar, and the results as the queue lists tracks: tile, title, game
 * chips. Back with the keyboard up only hides the keyboard; dragging the results hides it too.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    query: String,
    reselects: Int,
    onQueryChange: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    onTrackLongClick: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(reselects) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val hideKeyboard by rememberUpdatedState {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(
            query,
            onQueryChange,
            focusRequester,
            onClear = {
                onQueryChange("")
                focusRequester.requestFocus()
                keyboard?.show()
            },
            onSearch = { hideKeyboard() }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.noResults) {
            NoSearchResults(query.trim())
        } else {
            val listState = rememberLazyListState()
            // A new set of results starts at its best match. Otherwise the list keeps its first
            // row in view wherever that row lands in the new results, which can be far down. A
            // change of the playing track alone keeps the position. Requested before this
            // frame's layout, so the old position never shows.
            val ids = remember(state.results) { state.results.map { it.id } }
            val shown = remember { ShownResults() }
            SideEffect {
                if (shown.ids != ids) {
                    shown.ids = ids
                    listState.requestScrollToItem(0)
                }
            }
            val hideOnDrag = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (source == NestedScrollSource.UserInput) hideKeyboard()
                        return Offset.Zero
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize().nestedScroll(hideOnDrag), state = listState) {
                items(state.results, key = { it.id }) { row ->
                    val content = @Composable {
                        TrackTileRow(
                            row.title,
                            row.chips,
                            row.unplayable,
                            current = row.playing,
                            onClick = {
                                hideKeyboard()
                                onTrackClick(row.id)
                            },
                            onLongClick = {
                                hideKeyboard()
                                onTrackLongClick(row.id)
                            },
                            familyId = row.familyId,
                            thumbnail = row.thumbnail,
                            subtitle = row.subtitle
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

/** The ids of the results the list last scrolled to the top for. */
private class ShownResults {
    var ids: List<String>? = null
}

@Preview
@Composable
private fun SearchScreenPreview() {
    val ruby = ChipUi("R", "#CD2236")
    val sapphire = ChipUi("S", "#3D51A7")
    VGMixrTheme {
        SearchScreen(
            state = SearchUiState(
                results = listOf(
                    SearchRowUi("a", "Route 101", listOf(ruby, sapphire), playing = true),
                    SearchRowUi("b", "Route 101", listOf(ChipUi("OR", "#B0263A"))),
                    SearchRowUi("c", "Pokégear Radio: Route 101", listOf(ChipUi("HG", "#C9A23F")))
                )
            ),
            query = "rt 101",
            reselects = 0,
            onQueryChange = {},
            onTrackClick = {},
            onQueueTrack = {},
            onTrackLongClick = {}
        )
    }
}
