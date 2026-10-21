package io.github.pranavm716.vgmixr.ui.player

import androidx.annotation.StringRes
import androidx.compose.animation.core.animate
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The mini player for the current track, or nothing while no track is loaded. */
@Composable
fun MiniPlayerBar(onOpenNowPlaying: () -> Unit, viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visible = state as? MiniPlayerUiState.Visible ?: return
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    MiniPlayer(
        visible,
        progress = { progress },
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onOpen = onOpenNowPlaying
    )
}

/**
 * Shuttle's mini player: a progress line along the top, the family's tile, the title (scrolling
 * when too long) over the track's games and category, and play/pause, which spins while the track
 * loads. Swipe left for the next track and right for the previous one; swipe up or tap to open now
 * playing.
 */
@Composable
fun MiniPlayer(
    state: MiniPlayerUiState.Visible,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val openThresholdPx = with(LocalDensity.current) { OpenThreshold.toPx() }
    val current by rememberUpdatedState(state)
    var widthPx by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var lift by remember { mutableFloatStateOf(0f) }
    val swipe = rememberDraggableState { offset += it }
    val swipeUp = rememberDraggableState { lift += it }

    Column(modifier.fillMaxWidth()) {
        // The share played, in the accent; no track behind it and no seeking.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind { drawRect(accent, size = Size(size.width * progress(), size.height)) }
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                // No ripple on the body; only play/pause has one.
                .clickable(interactionSource = null, indication = null, onClick = onOpen)
                .draggable(
                    state = swipeUp,
                    orientation = Orientation.Vertical,
                    onDragStarted = { lift = 0f },
                    onDragStopped = { velocity ->
                        if (lift < -openThresholdPx || velocity < -FLING_VELOCITY) onOpen()
                        lift = 0f
                    }
                )
                .draggable(
                    state = swipe,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val width = widthPx.toFloat()
                        val before = current.track.trackId
                        val towardNext = offset < -width / 3 || velocity < -FLING_VELOCITY
                        val towardPrevious = offset > width / 3 || velocity > FLING_VELOCITY
                        val skip = when {
                            towardNext && current.next != null -> -width to onNext
                            towardPrevious && current.previous != null -> width to onPrevious
                            else -> null
                        }
                        if (skip == null) {
                            animate(offset, 0f) { value, _ -> offset = value }
                        } else {
                            // The neighbour slides fully in, then becomes the current track in
                            // place once the player has switched (or after a second regardless).
                            animate(offset, skip.first) { value, _ -> offset = value }
                            skip.second()
                            withTimeoutOrNull(1_000) {
                                snapshotFlow { current.track.trackId }.first { it != before }
                            }
                            offset = 0f
                        }
                    }
                )
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The current track, with its neighbours just out of view on either side.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .onSizeChanged { widthPx = it.width }
            ) {
                state.previous?.let {
                    TrackContent(it) { IntOffset((offset - widthPx).roundToInt(), 0) }
                }
                TrackContent(state.track) { IntOffset(offset.roundToInt(), 0) }
                state.next?.let {
                    TrackContent(it) { IntOffset((offset + widthPx).roundToInt(), 0) }
                }
            }
            PlayPauseButton(state.isPlaying, state.isLoading, onPlayPause)
        }
    }
}

@Composable
private fun TrackContent(track: MiniTrackUi, offset: () -> IntOffset) {
    Row(
        Modifier
            .fillMaxSize()
            .offset { offset() }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtTile(44.dp)
        Spacer(Modifier.width(14.dp))
        TrackText(track)
    }
}

@Composable
private fun TrackText(state: MiniTrackUi) {
    Column {
        Text(
            state.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameChips(state.chips)
            Text(
                " · " + stringResource(state.category.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )

            isPlaying -> Icon(
                painterResource(R.drawable.ic_pause),
                contentDescription = stringResource(R.string.pause)
            )

            else -> Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.play)
            )
        }
    }
}

@get:StringRes
val Category.label: Int
    get() = when (this) {
        Category.BATTLE -> R.string.category_battle
        Category.ROUTE -> R.string.category_route
        Category.TOWN -> R.string.category_town
        Category.DUNGEON -> R.string.category_dungeon
        Category.EVENT -> R.string.category_event
        Category.OTHER -> R.string.category_other
    }

// How far up the mini player must be swiped to open now playing.
private val OpenThreshold = 48.dp

// A swipe this fast (px/s) counts even if it was short.
private const val FLING_VELOCITY = 1500f

@Preview
@Composable
private fun MiniPlayerPreview() {
    VGMixrTheme {
        MiniPlayer(
            MiniPlayerUiState.Visible(
                track = MiniTrackUi(
                    "rse-route-101",
                    "rse",
                    "Route 101",
                    listOf(ChipUi("R", "#CD2236"), ChipUi("S", "#3D51A7")),
                    Category.ROUTE
                ),
                previous = null,
                next = null,
                isPlaying = true,
                isLoading = false
            ),
            progress = { 0.3f },
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onOpen = {}
        )
    }
}
