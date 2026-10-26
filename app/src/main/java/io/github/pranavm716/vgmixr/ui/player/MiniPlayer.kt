package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.components.PlayPauseIcon
import io.github.pranavm716.vgmixr.ui.components.RowTileGap
import io.github.pranavm716.vgmixr.ui.components.RowTileHeight
import io.github.pranavm716.vgmixr.ui.components.ThumbnailTile
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.components.TrackTitleChips
import io.github.pranavm716.vgmixr.ui.components.tintColor
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import kotlin.math.roundToInt

/**
 * The mini player for the current track, or nothing while no track is loaded. It reports where it
 * sits to [sheet], which grows out of it, and drives the sheet with upward drags.
 */
@Composable
fun MiniPlayerBar(
    sheet: NowPlayingSheetState,
    onOpenNowPlaying: () -> Unit,
    viewModel: MiniPlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visible = state as? MiniPlayerUiState.Visible
    if (visible == null) {
        SideEffect { sheet.collapsedBounds = Rect.Zero }
        return
    }
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    MiniPlayer(
        visible,
        progress = { progress },
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onOpen = onOpenNowPlaying,
        onLift = sheet::dragBy,
        onLiftStopped = sheet::settle,
        modifier = Modifier.onGloballyPositioned { sheet.collapsedBounds = it.boundsInRoot() }
    )
}

/** The mini player's look at the top of the now-playing sheet, fading out as it opens. */
@Composable
fun MiniPlayerInSheet(viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visible = state as? MiniPlayerUiState.Visible ?: return
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    MiniPlayer(
        visible,
        progress = { progress },
        onPlayPause = viewModel::onPlayPause,
        onNext = {},
        onPrevious = {},
        onOpen = {},
        onLift = {},
        onLiftStopped = {},
        interactive = false
    )
}

/**
 * Shuttle's mini player: a progress line along the top, the family's tile, the title (scrolling
 * when too long) over the track's game chips, and play/pause, which spins while the track
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
    onLift: (deltaPx: Float) -> Unit,
    onLiftStopped: (velocity: Float) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val accent = MaterialTheme.colorScheme.primary
    val current by rememberUpdatedState(state)
    val swipe = rememberTrackSwipeState(state.previous != null, state.next != null)
    val lift = rememberDraggableState(onLift)

    val lineHeightPx = with(LocalDensity.current) { 2.dp.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            // The share played, in the accent, drawn over the bar's top edge so it takes no
            // height and the content stays centered; no track behind it and no seeking.
            .drawWithContent {
                drawContent()
                drawRect(accent, size = Size(size.width * progress(), lineHeightPx))
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TileRowHeight)
                // No ripple on the body; only play/pause has one.
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = interactive,
                    onClick = onOpen
                )
                // Up grows the bar into now playing, following the finger.
                .draggable(
                    state = lift,
                    orientation = Orientation.Vertical,
                    enabled = interactive,
                    onDragStopped = { onLiftStopped(it) }
                )
                .draggable(
                    state = swipe.draggable,
                    orientation = Orientation.Horizontal,
                    enabled = interactive,
                    onDragStopped = { velocity ->
                        swipe.settle(velocity, onPrevious, onNext) { current.track.trackId }
                    }
                )
                .onSizeChanged { swipe.widthPx = it.width.toFloat() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The current track, with its neighbours just out of view on either side. Each spans
            // the whole island, tint included, so a neighbour's colors slide in with it; the
            // button floats on top.
            Box(Modifier.fillMaxSize().clipToBounds()) {
                val tracks = listOfNotNull(
                    state.previous?.let { it to -1 },
                    state.track to 0,
                    state.next?.let { it to 1 }
                )
                fun slot(side: Int) =
                    IntOffset((swipe.offset + side * swipe.widthPx).roundToInt(), 0)
                // The tints slide whole, under everything.
                tracks.forEach { (track, side) -> TrackTint(track) { slot(side) } }
                // The tiles and text slide too, but fade out just before the button, which stays
                // put, so nothing passes under it.
                Box(Modifier.fillMaxSize().fadeOutBefore(ButtonWidth, FadeWidth)) {
                    tracks.forEach { (track, side) ->
                        val stretch = if (side == 0) Modifier.trackStretch(swipe) else Modifier
                        TrackContent(track, stretch) { slot(side) }
                    }
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    PlayPauseButton(state.isPlaying, state.isLoading, onPlayPause)
                }
            }
        }
    }
}

@Composable
private fun TrackTint(track: MiniTrackUi, offset: () -> IntOffset) {
    Box(
        Modifier
            .fillMaxSize()
            .offset { offset() }
            .background(gameTint(track.chips))
    )
}

@Composable
private fun TrackContent(
    track: MiniTrackUi,
    stretch: Modifier = Modifier,
    offset: () -> IntOffset
) {
    Row(
        Modifier
            .fillMaxSize()
            .offset { offset() }
            // Room on the right for the play/pause button floating above.
            .padding(start = 16.dp, end = ButtonWidth)
            .then(stretch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbnail = track.thumbnail
        if (thumbnail !=
            null
        ) {
            ThumbnailTile(thumbnail, RowTileHeight)
        } else {
            ArtTile(RowTileHeight, familyId = track.familyId)
        }
        Spacer(Modifier.width(RowTileGap))
        TrackText(track)
    }
}

@Composable
private fun TrackText(state: MiniTrackUi) =
    TrackTitleChips(state.title, state.chips, marquee = true, subtitle = state.subtitle)

@Composable
private fun PlayPauseButton(isPlaying: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )

            else -> PlayPauseIcon(isPlaying, Modifier.size(24.dp))
        }
    }
}

// The play/pause button's slot at the right edge, and the fade in front of it.
private val ButtonWidth = 56.dp
private val FadeWidth = 24.dp

/** Fades the content out over [fade] just left of the [end] strip at the right edge. */
private fun Modifier.fadeOutBefore(end: Dp, fade: Dp) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val stop = size.width - end.toPx()
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Black, Color.Transparent),
                startX = stop - fade.toPx(),
                endX = stop
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * The track's games' colors, faintly: one game gives a flat tint, several a left-to-right gradient
 * through them (an RSE track runs red, blue, green).
 */
// Used by the mini player, the family header and now playing's fade.
internal fun gameTint(chips: List<ChipUi>, alpha: Float = GAME_TINT): Brush {
    val colors = chips.map { tintColor(it.color).copy(alpha = alpha) }
    return when (colors.size) {
        0 -> SolidColor(Color.Transparent)
        1 -> SolidColor(colors.single())
        else -> Brush.horizontalGradient(colors)
    }
}

/** One color's tint, as faint as a game's: a playlist's art's average color, or the accent. */
internal fun colorTint(color: Color): Brush = SolidColor(tintColor(color).copy(alpha = GAME_TINT))

private const val GAME_TINT = 0.25f

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
                    listOf(ChipUi("R", "#CD2236"), ChipUi("S", "#3D51A7"))
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
            onOpen = {},
            onLift = {},
            onLiftStopped = {}
        )
    }
}
