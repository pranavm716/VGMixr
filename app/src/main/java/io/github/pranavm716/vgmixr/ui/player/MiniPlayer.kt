package io.github.pranavm716.vgmixr.ui.player

import androidx.annotation.StringRes
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.components.chipColor
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
                .onSizeChanged { widthPx = it.width },
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
                fun slot(side: Int) = IntOffset((offset + side * widthPx).roundToInt(), 0)
                // The tints slide whole, under everything.
                tracks.forEach { (track, side) -> TrackTint(track) { slot(side) } }
                // The tiles and text slide too, but fade out just before the button, which stays
                // put, so nothing passes under it.
                Box(Modifier.fillMaxSize().fadeOutBefore(ButtonWidth, FadeWidth)) {
                    tracks.forEach { (track, side) -> TrackContent(track) { slot(side) } }
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
private fun TrackContent(track: MiniTrackUi, offset: () -> IntOffset) {
    Row(
        Modifier
            .fillMaxSize()
            .offset { offset() }
            // Room on the right for the play/pause button floating above.
            .padding(start = 16.dp, end = ButtonWidth),
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
            style = MaterialTheme.typography.titleMedium.trimmed(),
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
        // The trim takes the room under the title's descenders too; give the chips some back.
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameChips(state.chips)
            Text(
                " · " + stringResource(state.category.label),
                style = MaterialTheme.typography.bodyMedium.trimmed(),
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
private fun gameTint(chips: List<ChipUi>): Brush {
    val colors = chips.map { chipColor(it.color).copy(alpha = GAME_TINT) }
    return when (colors.size) {
        0 -> SolidColor(Color.Transparent)
        1 -> SolidColor(colors.single())
        else -> Brush.horizontalGradient(colors)
    }
}

private const val GAME_TINT = 0.25f

// Open Sans leaves room above its letters, so a text line sits optically low; trimming the line box
// to the glyphs centers the title and its second line on the bar.
private fun TextStyle.trimmed() = copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

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
