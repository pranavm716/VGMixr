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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun AddTracksTab(onBack: () -> Unit, viewModel: AddTracksViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The field's text is kept here, so typing never waits on the ViewModel.
    var query by rememberSaveable { mutableStateOf("") }
    AddTracksScreen(
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
 * "Add to <playlist>": the Search tab's field under a back arrow, and the matches as search lists
 * them, each ending in a plus that adds it to the playlist (a check once it's in; tapping the check
 * takes it out). Tapping a row itself does nothing, so nothing plays while picking. The keyboard
 * comes up on opening; dragging the results hides it.
 */
@Composable
fun AddTracksScreen(
    state: AddTracksUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
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
                stringResource(R.string.add_to_playlist_title, state.playlistName),
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
            LazyColumn(Modifier.fillMaxSize().nestedScroll(hideOnDrag)) {
                items(state.results, key = { it.id }) { row ->
                    TrackTileRow(
                        row.title,
                        row.chips,
                        row.unplayable,
                        current = false,
                        onClick = null,
                        trailing = { AddButton(row.added, onClick = { onToggle(row.id) }) }
                    )
                }
            }
        }
    }
}

/** A plus, or the accent check once the track is in the playlist. */
@Composable
private fun AddButton(added: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (added) R.drawable.ic_check else R.drawable.ic_add),
            contentDescription = stringResource(
                if (added) R.string.remove_from_this_playlist else R.string.add_to_this_playlist
            ),
            tint = if (added) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Preview
@Composable
private fun AddTracksScreenPreview() {
    val ruby = ChipUi("R", "#CD2236")
    val sapphire = ChipUi("S", "#3D51A7")
    VGMixrTheme {
        AddTracksScreen(
            AddTracksUiState(
                playlistName = "Hype",
                results = listOf(
                    AddTrackRowUi("a", "Route 101", listOf(ruby, sapphire), added = true),
                    AddTrackRowUi("b", "Route 102", listOf(ruby))
                )
            ),
            query = "route",
            onQueryChange = {},
            onToggle = {},
            onBack = {}
        )
    }
}
