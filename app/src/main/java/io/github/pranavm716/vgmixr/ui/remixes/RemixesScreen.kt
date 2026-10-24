package io.github.pranavm716.vgmixr.ui.remixes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.ScrollToTopOnReselect
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun RemixesTab(reselects: Int, viewModel: RemixesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(reselects, listState)
    RemixesScreen(state, onTrackClick = viewModel::onTrackClick, listState = listState)
}

/**
 * The Remixes tab: the tracks added from YouTube, newest first, each with its video's thumbnail,
 * its original's game chips and its channel. A tap plays Remixes from that track.
 */
@Composable
fun RemixesScreen(
    state: RemixesUiState,
    onTrackClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.playing_from_remixes))
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
                TrackTileRow(
                    title = row.title,
                    chips = row.chips,
                    unplayable = row.unplayable,
                    current = row.playing,
                    onClick = { onTrackClick(row.id) },
                    thumbnail = row.thumbnail,
                    subtitle = row.channel
                )
            }
        }
    }
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
            onTrackClick = {}
        )
    }
}
