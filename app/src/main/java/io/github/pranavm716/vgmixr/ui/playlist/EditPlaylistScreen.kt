package io.github.pranavm716.vgmixr.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.NoSearchResults
import io.github.pranavm716.vgmixr.ui.components.SearchField
import io.github.pranavm716.vgmixr.ui.components.ToggleAddButton
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun EditPlaylistTab(onBack: () -> Unit, viewModel: EditPlaylistViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The field's text is kept here, so typing never waits on the ViewModel.
    var query by rememberSaveable { mutableStateOf("") }
    EditPlaylistScreen(
        state,
        query,
        onQueryChange = {
            query = it
            viewModel.onQueryChange(it)
        },
        onToggle = viewModel::onToggle,
        onBack = onBack
    )
}

/**
 * "Edit <playlist>": the Search tab's field under a back arrow. Under an empty field the playlist's
 * own tracks, all checked, so unchecking one takes it out (and its row away); under a typed query
 * the matches as search lists them, each ending in a plus that adds it (a check once it's in).
 * Tapping a row itself does nothing, so nothing plays while editing. The keyboard comes up only
 * when the field is tapped, so it doesn't cover the list; dragging the list hides it.
 */
@Composable
fun EditPlaylistScreen(
    state: EditPlaylistUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideKeyboard = {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.edit_playlist_title, state.playlistName),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        SearchField(
            query,
            onQueryChange,
            focusRequester,
            onClear = {
                onQueryChange("")
                focusRequester.requestFocus()
                keyboard?.show()
            },
            onSearch = hideKeyboard
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.noResults) {
            NoSearchResults(query.trim())
        } else {
            val listState = rememberLazyListState()
            // Results for a new query start at the top, as the Search tab's do. Otherwise the list
            // keeps its first row in view wherever that row lands in the new results, which can be
            // far down. Keyed by the query, not the rows: unchecking a track under an empty field
            // takes its row away and must leave the list where it is. Requested before this
            // frame's layout, so the old position never shows.
            val shown = remember { ShownQuery() }
            SideEffect {
                if (shown.query != state.resultsQuery) {
                    shown.query = state.resultsQuery
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
                    TrackTileRow(
                        row.title,
                        row.chips,
                        row.unplayable,
                        current = false,
                        onClick = null,
                        thumbnail = row.thumbnail,
                        subtitle = row.subtitle,
                        trailing = {
                            ToggleAddButton(
                                row.added,
                                onClick = { onToggle(row.id) },
                                addLabel = stringResource(R.string.add_to_this_playlist),
                                removeLabel = stringResource(R.string.remove_from_this_playlist)
                            )
                        }
                    )
                }
            }
        }
    }
}

private class ShownQuery {
    var query: String? = null
}

@Preview
@Composable
private fun EditPlaylistScreenPreview() {
    val ruby = ChipUi("R", "#CD2236")
    val sapphire = ChipUi("S", "#3D51A7")
    VGMixrTheme {
        EditPlaylistScreen(
            EditPlaylistUiState(
                playlistName = "Hype",
                results = listOf(
                    EditPlaylistRowUi("a", "Route 101", listOf(ruby, sapphire), added = true),
                    EditPlaylistRowUi("b", "Route 102", listOf(ruby))
                )
            ),
            query = "route",
            onQueryChange = {},
            onToggle = {},
            onBack = {}
        )
    }
}
