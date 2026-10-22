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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueue
import io.github.pranavm716.vgmixr.ui.components.UnplayableIcon
import io.github.pranavm716.vgmixr.ui.components.unplayable
import io.github.pranavm716.vgmixr.ui.player.gameTint
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import kotlinx.coroutines.flow.first

// The header's art tile and the track numbers share one leading column, so their centers line up.
private val StartPadding = 16.dp
private val LeadingColumn = 40.dp
private val LeadingGap = 16.dp

// The playing row's tint: the accent over the background, faint as in Shuttle.
private const val PLAYING_TINT = 0.12f

@Composable
fun FamilyTab(viewModel: FamilyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FamilyScreen(
        state,
        onTrackClick = viewModel::onTrackClick,
        onQueueTrack = viewModel::onQueueTrack
    )
}

/** A family's tracks, styled after Shuttle's album screen; the art tile stands in for "back". */
@Composable
fun FamilyScreen(
    state: FamilyUiState,
    onTrackClick: (String) -> Unit,
    onQueueTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        FamilyUiState.Loading -> Unit

        FamilyUiState.NotFound -> NotFound(modifier.statusBarsPadding())

        is FamilyUiState.Loaded -> Column(modifier.fillMaxSize()) {
            Header(state.name, state.trackCount, state.games)
            val listState = rememberLazyListState()
            ScrollToPlayingOnce(listState, state.playing)
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.tracks, key = { it.id }) { track ->
                    val playing = state.playing?.takeIf { it.trackId == track.id }
                    val row = @Composable {
                        TrackRow(
                            track,
                            playing = playing != null,
                            loading = playing?.loading == true,
                            onClick = { onTrackClick(track.id) }
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

/**
 * The family's name and track count, tinted with its games' colors as the mini player is. The tint
 * runs up behind the status bar.
 */
@Composable
private fun Header(name: String, trackCount: Int, games: List<ChipUi>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(gameTint(games))
            .statusBarsPadding()
            .padding(start = StartPadding, top = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtTile(LeadingColumn)
        Spacer(Modifier.width(LeadingGap))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
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

/**
 * Opening a family whose track is playing shows that track mid-screen, once: coming back to the
 * screen keeps where the user scrolled.
 */
@Composable
private fun ScrollToPlayingOnce(listState: LazyListState, playing: PlayingRowUi?) {
    var done by rememberSaveable { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { TrackRowHeight.roundToPx() }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        done = true
        val index = playing?.index ?: return@LaunchedEffect
        val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        listState.scrollToItem(index, scrollOffset = -(viewport - rowPx) / 2)
    }
}

private val TrackRowHeight = 48.dp

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
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TrackRowHeight)
            .then(if (playing) Modifier.background(accent.copy(alpha = PLAYING_TINT)) else Modifier)
            .clickable(onClick = onClick)
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
            onQueueTrack = {}
        )
    }
}
