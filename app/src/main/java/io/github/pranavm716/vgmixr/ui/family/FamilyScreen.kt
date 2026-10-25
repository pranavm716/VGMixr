package io.github.pranavm716.vgmixr.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.CollectionPage
import io.github.pranavm716.vgmixr.ui.components.FullWidth
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.components.HeaderImage
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.UnplayableIcon
import io.github.pranavm716.vgmixr.ui.components.coverAsset
import io.github.pranavm716.vgmixr.ui.components.rememberArt
import io.github.pranavm716.vgmixr.ui.components.unplayable
import io.github.pranavm716.vgmixr.ui.player.gameTint
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

// The track numbers' column.
private val StartPadding = 16.dp
private val LeadingColumn = 40.dp
private val LeadingGap = 16.dp

// The playing row's tint: the accent over the background, faint as in Shuttle.
private const val PLAYING_TINT = 0.12f

@Composable
fun FamilyTab(onAddToPlaylist: (String) -> Unit, viewModel: FamilyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FamilyScreen(
        state,
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack,
        onTrackLongClick = onAddToPlaylist
    )
}

/** A family's tracks under its cover, which shrinks into a bar as the list scrolls. */
@Composable
fun FamilyScreen(
    state: FamilyUiState,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    onTrackLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        FamilyUiState.Loading -> Unit

        FamilyUiState.NotFound -> NotFound(modifier.statusBarsPadding())

        is FamilyUiState.Loaded -> {
            val cover = rememberArt(state.familyId?.let(::coverAsset), FullWidth)
            // Without a cover, the family's games' colors stand in for it.
            val noCover = remember(state.games) { gameTint(state.games, NO_COVER_TINT) }
            CollectionPage(
                state.name,
                state.trackCount,
                barTint = remember(state.games) { gameTint(state.games) },
                playingIndex = state.playing?.index,
                rowHeight = TrackRowHeight,
                art = { HeaderImage(cover, missing = noCover) },
                modifier = modifier
            ) {
                items(state.tracks, key = { it.id }) { track ->
                    val playing = state.playing?.takeIf { it.trackId == track.id }
                    val row = @Composable {
                        TrackRow(
                            track,
                            playing = playing != null,
                            loading = playing?.loading == true,
                            onClick = { onTrackClick(track.id) },
                            onLongClick = { onTrackLongClick(track.id) }
                        )
                    }
                    // A greyed track can't be queued, so its row doesn't swipe.
                    if (track.unplayable) {
                        row()
                    } else {
                        SwipeToQueue(onQueue = { onQueueTrack(track.id) }, content = row)
                    }
                }
            }
        }
    }
}

private val TrackRowHeight = 48.dp

// A family without a cover shows its games' colors at this strength instead.
private const val NO_COVER_TINT = 0.55f

/**
 * One track: its number in the list, its title and, unless it plays in every game, its games. The
 * current track's row is tinted with the accent, as in Shuttle, and its number turns into a spinner
 * while its stream loads. A track whose sources are all broken is greyed, with a mark at its end.
 */
@Composable
fun TrackRow(
    track: TrackRowUi,
    playing: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TrackRowHeight)
            .then(if (playing) Modifier.background(accent.copy(alpha = PLAYING_TINT)) else Modifier)
            // Ticks on a long press by itself (hapticFeedbackEnabled).
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = StartPadding, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val faint = Modifier.unplayable(track.unplayable)
        Box(Modifier.width(LeadingColumn).then(faint), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = accent,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    track.number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(LeadingGap))
        Text(
            track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(faint)
        )
        if (track.chips.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(faint) { GameChips(track.chips) }
        }
        if (track.unplayable) {
            Spacer(Modifier.width(12.dp))
            UnplayableIcon()
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
                games = listOf(ruby, sapphire, ChipUi("E", "#009A44")),
                tracks = listOf(
                    TrackRowUi("a", 1, "Opening Movie (Ruby & Sapphire)", listOf(ruby, sapphire)),
                    TrackRowUi("b", 2, "Littleroot Town", emptyList()),
                    TrackRowUi("c", 10, "Battle! (Team Magma)", listOf(ruby))
                ),
                playing = PlayingRowUi("b", index = 1, loading = true)
            ),
            onTrackClick = {},
            onQueueTrack = {},
            onTrackLongClick = {}
        )
    }
}
