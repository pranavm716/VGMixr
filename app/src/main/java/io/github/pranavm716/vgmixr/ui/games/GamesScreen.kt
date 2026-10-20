package io.github.pranavm716.vgmixr.ui.games

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.catalog.FamilySummary
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@Composable
fun GamesTab(viewModel: GamesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Opening a family comes with its track screen; for now a tap only shows the ripple.
    GamesScreen(state, onFamilyClick = {})
}

/** The Games tab: one row per family, styled after Shuttle's album list. */
@Composable
fun GamesScreen(
    state: GamesUiState,
    onFamilyClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Games")
        when (state) {
            GamesUiState.Loading -> Unit

            is GamesUiState.Loaded -> {
                if (state.importFailed) ImportWarning()
                if (state.families.isEmpty()) {
                    Empty()
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.families, key = { it.id }) { family ->
                            FamilyRow(family, onClick = { onFamilyClick(family.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyRow(family: FamilySummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A blank tile until families get covers.
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ArtPlaceholder)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                family.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pluralStringResource(R.plurals.track_count, family.trackCount, family.trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportWarning() {
    Text(
        stringResource(R.string.import_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun Empty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.games_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun GamesScreenPreview() {
    VGMixrTheme {
        GamesScreen(
            GamesUiState.Loaded(
                families = listOf(
                    FamilySummary("rby", "Red / Blue / Yellow", 49),
                    FamilySummary("lgpe", "Let's Go, Pikachu! / Let's Go, Eevee!", 56),
                    FamilySummary("pla", "Legends: Arceus", 1)
                ),
                importFailed = true
            ),
            onFamilyClick = {}
        )
    }
}
