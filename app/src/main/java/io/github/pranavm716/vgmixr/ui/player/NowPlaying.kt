package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.components.MarqueeText

/**
 * Now playing's header, as Spotify's: a chevron that closes the sheet, and "Playing from" over
 * where the track plays from; tapping that opens the family. As tall as the mini player, which
 * cross-fades into it at the top of the sheet.
 */
@Composable
fun NowPlayingHeader(
    onClose: () -> Unit,
    onOpenFamily: (familyId: String) -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val playingFrom = (state as? NowPlayingUiState.Visible)?.playingFrom
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = stringResource(R.string.close_now_playing)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (playingFrom is PlayingFromUi.Family) {
                        Modifier.clickable { onOpenFamily(playingFrom.familyId) }
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (playingFrom != null) {
                Text(
                    stringResource(R.string.playing_from),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    when (playingFrom) {
                        is PlayingFromUi.Family -> playingFrom.name
                        PlayingFromUi.Queue -> stringResource(R.string.playing_from_queue)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Balances the chevron, so the context sits in the middle.
        Spacer(Modifier.width(48.dp))
    }
}

/**
 * Now playing's body, Shuttle's look: the art, the title over its game chips, the seek
 * bar between the elapsed time and the length, previous / play-pause / next, and the queue button.
 */
@Composable
fun NowPlayingScreen(onOpenQueue: () -> Unit, viewModel: NowPlayingViewModel = hiltViewModel()) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val visible = state as? NowPlayingUiState.Visible ?: return
    val background = MaterialTheme.colorScheme.background
    val tint = gameTint(visible.chips)
    Column(
        Modifier
            .fillMaxSize()
            // The track's game colors, faint at the top and gone by 40% down, carrying the mini
            // player's tint into now playing.
            .drawWithCache {
                val fade = Brush.verticalGradient(
                    0f to Color.Transparent,
                    TINT_FADE_END to background,
                    endY = size.height
                )
                onDrawBehind {
                    drawRect(tint)
                    drawRect(fade)
                }
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            // Below the header, which the sheet draws on top.
            .padding(top = 66.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        ArtTile(Modifier.padding(horizontal = ART_MARGIN).fillMaxWidth().aspectRatio(1f))
        // The title and chips sit halfway between the art and the controls: equal gaps, plus a
        // nudge for the room the text and the slider leave around what you see.
        Spacer(Modifier.weight(INFO_GAP))
        Column(
            Modifier.offset(y = INFO_NUDGE),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A title too wide scrolls, fading at both edges.
            MarqueeText(
                visible.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = SIDE_MARGIN)
            )
            Spacer(Modifier.height(12.dp))
            GameChips(visible.chips)
        }
        Spacer(Modifier.weight(INFO_GAP))
        Column(Modifier.padding(horizontal = SIDE_MARGIN)) {
            SeekRow(viewModel)
            Spacer(Modifier.height(16.dp))
            Controls(visible.isPlaying, visible.isLoading, viewModel)
        }
        Spacer(Modifier.weight(BOTTOM_GAP))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SIDE_MARGIN),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenQueue) {
                Icon(
                    painterResource(R.drawable.ic_queue),
                    contentDescription = stringResource(R.string.queue)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekRow(viewModel: NowPlayingViewModel) {
    val progress = viewModel.progress.collectAsStateWithLifecycle().value
    val duration = progress.durationMs?.takeIf { it > 0 }
    // While dragging, the thumb and the elapsed time follow the finger; the seek happens on release.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val fraction = dragged ?: progress.fraction()
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimeText(formatTime(if (duration != null) (fraction * duration).toLong() else 0))
        Slider(
            value = fraction,
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                val target = dragged
                if (target != null &&
                    duration != null
                ) {
                    viewModel.onSeek((target * duration).toLong())
                }
                dragged = null
            },
            enabled = duration != null,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            thumb = {
                Box(Modifier.size(16.dp).background(accent, CircleShape))
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = accent,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp
                )
            }
        )
        TimeText(duration?.let(::formatTime) ?: "–:––")
    }
}

@Composable
private fun TimeText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Controls(isPlaying: Boolean, isLoading: Boolean, viewModel: NowPlayingViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = viewModel::onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(36.dp)
            )
        }
        Box(
            Modifier
                .size(68.dp)
                // Clipped first, so the ripple stays inside the circle.
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = viewModel::onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )

                else -> Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(
                        if (isPlaying) R.string.pause else R.string.play
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        IconButton(onClick = viewModel::onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

private fun PlaybackProgress.fraction(): Float {
    val duration = durationMs?.takeIf { it > 0 } ?: return 0f
    return (positionMs.toFloat() / duration).coerceIn(0f, 1f)
}

// The art takes most of the width, a little less than Shuttle's; the tint is gone 40% down.
// The art's distance from the screen edges: half of the 40dp it had, so it fills more of the width.
private val ART_MARGIN = 20.dp

// Measured on the phone: with equal gaps, the title's letters sat 11dp closer to the art than the
// chips to the slider's thumb, since the slider leaves room above its thumb.
private val INFO_NUDGE = 6.dp

// The margin of everything else.
private val SIDE_MARGIN = 24.dp

// How the free space splits: equal gaps above and below the title and chips, so they center
// between the art and the controls, and a smaller one under the controls, so they sit low.
private const val INFO_GAP = 0.7f
private const val BOTTOM_GAP = 0.6f
private const val TINT_FADE_END = 0.4f
