package io.github.pranavm716.vgmixr.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

// The header's art tile and the track numbers share one leading column, so their centers line up.
private val StartPadding = 16.dp
private val LeadingColumn = 40.dp
private val LeadingGap = 16.dp

@Composable
fun FamilyTab(viewModel: FamilyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FamilyScreen(state)
}

/** A family's tracks, styled after Shuttle's album screen; the art tile stands in for "back". */
@Composable
fun FamilyScreen(state: FamilyUiState, modifier: Modifier = Modifier) {
    when (state) {
        FamilyUiState.Loading -> Unit

        FamilyUiState.NotFound -> NotFound(modifier)

        is FamilyUiState.Loaded -> Column(modifier.fillMaxSize()) {
            Header(state.name, state.trackCount)
            LazyColumn(Modifier.fillMaxSize()) {
                // Played from in step 8; for now a tap only shows the ripple.
                items(state.tracks, key = { it.id }) { TrackRow(it, onClick = {}) }
            }
        }
    }
}

@Composable
private fun Header(name: String, trackCount: Int) {
    Row(
        modifier = Modifier.padding(start = StartPadding, top = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A blank tile until families get covers.
        Box(
            Modifier
                .size(LeadingColumn)
                .background(ArtPlaceholder, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(LeadingGap))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pluralStringResource(R.plurals.track_count, trackCount, trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One track: its number in the list, its title and, unless it plays in every game, its games. */
@Composable
fun TrackRow(track: TrackRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(start = StartPadding, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            track.number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(LeadingColumn)
        )
        Spacer(Modifier.width(LeadingGap))
        Text(
            track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (track.chips.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            GameChips(track.chips)
        }
    }
}

@Composable
private fun NotFound(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.family_not_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun FamilyScreenPreview() {
    val ruby = ChipUi("R", "#CD2236")
    val sapphire = ChipUi("S", "#3D51A7")
    VGMixrTheme {
        FamilyScreen(
            FamilyUiState.Loaded(
                name = "Ruby / Sapphire / Emerald",
                trackCount = 3,
                tracks = listOf(
                    TrackRowUi("a", 1, "Opening Movie (Ruby & Sapphire)", listOf(ruby, sapphire)),
                    TrackRowUi("b", 2, "Littleroot Town", emptyList()),
                    TrackRowUi("c", 10, "Battle! (Team Magma)", listOf(ruby))
                )
            )
        )
    }
}
