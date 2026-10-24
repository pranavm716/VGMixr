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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.ui.components.ART_ASPECT
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.ChipsLine
import io.github.pranavm716.vgmixr.ui.components.MarqueeText
import io.github.pranavm716.vgmixr.ui.components.PlayPauseIcon
import io.github.pranavm716.vgmixr.ui.components.ThumbnailArt

/**
 * Now playing's header, as Spotify's: a chevron that closes the sheet, and "Playing from" over
 * where the track plays from; tapping that opens the family or playlist. As tall as the mini
 * player, which cross-fades into it at the top of the sheet.
 */
@Composable
fun NowPlayingHeader(
    onClose: () -> Unit,
    onOpenPlayingFrom: (PlayingFromUi) -> Unit,
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
        // The tap target is as wide as the text and as tall as the header, with no ripple.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .then(
                        if (playingFrom != null && playingFrom != PlayingFromUi.Queue) {
                            Modifier.clickable(interactionSource = null, indication = null) {
                                onOpenPlayingFrom(playingFrom)
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayingFromText(playingFrom)
            }
        }
        // Balances the chevron, so the context sits in the middle.
        Spacer(Modifier.width(48.dp))
    }
}

/** "Playing from" over the family's or playlist's name, "Queue" or "Remixes". */
@Composable
private fun PlayingFromText(playingFrom: PlayingFromUi?) {
    if (playingFrom == null) return
    Text(
        stringResource(R.string.playing_from),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
    Text(
        when (playingFrom) {
            is PlayingFromUi.Family -> playingFrom.name
            is PlayingFromUi.Playlist -> playingFrom.name
            PlayingFromUi.Queue -> stringResource(R.string.playing_from_queue)
            PlayingFromUi.Remixes -> stringResource(R.string.playing_from_remixes)
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Now playing's body, Shuttle's look: the 16:9 art, the title over its game chips, the seek
 * bar between the elapsed time and the length, previous / play-pause / next, and under them the
 * row of add to playlist (start), the favorite heart (centered) and the queue button (end). The
 * title, seek bar and controls are one group, sitting low; the art is centered above it, between
 * the header's text and the title's letters (layout E of roadmap 13b's mockups).
 * Tapping the title or chips opens the track's own family ([onOpenFamily]), also when it plays
 * from a playlist, to reach the tracks around it.
 */
@Composable
fun NowPlayingScreen(
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenFamily: (familyId: String) -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
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
        Spacer(Modifier.weight(ABOVE_ART))
        val art = Modifier.padding(horizontal = ART_MARGIN).fillMaxWidth().aspectRatio(ART_ASPECT)
        val thumbnail = visible.thumbnail
        if (thumbnail != null) ThumbnailArt(thumbnail, art) else ArtTile(art)
        Spacer(Modifier.weight(BELOW_ART))
        // A tap target without a ripple, as the header's "Playing from".
        Column(
            Modifier
                .offset(y = INFO_NUDGE)
                .clickable(interactionSource = null, indication = null) {
                    visible.familyId?.let(onOpenFamily)
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A title too wide scrolls, fading at both edges.
            MarqueeText(
                visible.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = SIDE_MARGIN)
            )
            Spacer(Modifier.height(12.dp))
            ChipsLine(visible.chips, visible.subtitle)
        }
        Spacer(Modifier.height(INFO_TO_SEEK))
        Column(Modifier.padding(horizontal = SIDE_MARGIN)) {
            SeekRow(viewModel)
            Spacer(Modifier.height(16.dp))
            Controls(visible.isPlaying, visible.isLoading, visible.shuffle, viewModel)
        }
        Spacer(Modifier.weight(BELOW_CONTROLS))
        Box(Modifier.fillMaxWidth().padding(horizontal = SIDE_MARGIN)) {
            // White in both states, not the accent: filled when the track is a favorite.
            IconButton(onClick = viewModel::onToggleFavorite, Modifier.align(Alignment.Center)) {
                val favorite = visible.isFavorite
                Icon(
                    painterResource(
                        if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                    ),
                    contentDescription = stringResource(
                        if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites
                    ),
                    tint = Color.White
                )
            }
            IconButton(onClick = onAddToPlaylist, Modifier.align(Alignment.CenterStart)) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_to_playlist)
                )
            }
            IconButton(onClick = onOpenQueue, Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    painterResource(R.drawable.ic_queue),
                    contentDescription = stringResource(R.string.queue)
                )
            }
        }
        Spacer(Modifier.height(BOTTOM_MARGIN))
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
private fun Controls(
    isPlaying: Boolean,
    isLoading: Boolean,
    shuffle: Boolean,
    viewModel: NowPlayingViewModel
) {
    // Spotify's row: each control centered a fixed distance from the middle, play/pause on it.
    // Repeat is a placeholder for step 21's repeat/loop button and does nothing yet.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ControlSlot(-OUTER_OFFSET) {
            IconButton(onClick = viewModel::onShuffle, Modifier.requiredSize(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(
                        if (shuffle) R.string.shuffle_on else R.string.shuffle_off
                    ),
                    // The accent when on; off, previous and next's color.
                    tint = if (shuffle) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                    modifier = Modifier.size(SHUFFLE_ICON)
                )
            }
        }
        ControlSlot(-SKIP_OFFSET) {
            IconButton(onClick = viewModel::onPrevious, Modifier.requiredSize(56.dp)) {
                Icon(
                    painterResource(R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.previous),
                    modifier = Modifier.size(SKIP_ICON)
                )
            }
        }
        ControlSlot(0.dp) {
            Box(
                Modifier
                    .size(PLAY_SIZE)
                    // Clipped first, so the ripple stays inside the circle.
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = viewModel::onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(LOADING_SIZE),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )

                    else -> PlayPauseIcon(isPlaying, Modifier.size(PLAY_ICON), tint = Color.White)
                }
            }
        }
        ControlSlot(SKIP_OFFSET) {
            IconButton(onClick = viewModel::onNext, Modifier.requiredSize(56.dp)) {
                Icon(
                    painterResource(R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.next),
                    modifier = Modifier.size(SKIP_ICON)
                )
            }
        }
        ControlSlot(OUTER_OFFSET) {
            Icon(
                painterResource(R.drawable.ic_repeat),
                contentDescription = null,
                modifier = Modifier.requiredSize(REPEAT_ICON)
            )
        }
    }
}

/** A control centered [offset] from the row's middle; its touch target may overflow the row. */
@Composable
private fun ControlSlot(offset: Dp, content: @Composable () -> Unit) {
    Box(Modifier.offset(x = offset), contentAlignment = Alignment.Center) { content() }
}

// Spotify's controls row, measured from a screenshot of its now playing on the phone (360dp
// wide). Each control's center lies a fixed distance from the middle: skips 76dp, shuffle and
// repeat 144dp. Material Symbols' glyphs have other proportions than Spotify's, so each icon is
// sized for its glyph to cover the same area as Spotify's (glyph sizes in dp).
private val SKIP_OFFSET = 76.dp
private val OUTER_OFFSET = 144.dp

// Glyph 21.3 x 21.3 (Spotify's 22.6 x 19.9).
private val SHUFFLE_ICON = 32.dp

// Glyph 23.8 x 22 (Spotify's 21.5 x 23.8).
private val SKIP_ICON = 44.dp

// Glyph 20.3 x 22.5 (Spotify's 21.8 x 20.7).
private val REPEAT_ICON = 27.dp

// The circle is Spotify's; its triangle 15.6 x 19.8 (Spotify's 17.2 x 19.5).
private val PLAY_SIZE = 64.dp
private val PLAY_ICON = 34.dp
private val LOADING_SIZE = 27.dp

private fun PlaybackProgress.fraction(): Float {
    val duration = durationMs?.takeIf { it > 0 } ?: return 0f
    return (positionMs.toFloat() / duration).coerceIn(0f, 1f)
}

// The art takes most of the width, a little less than Shuttle's; the tint is gone 40% down.
// The art's distance from the screen edges: half of the 40dp it had, so it fills more of the width.
private val ART_MARGIN = 20.dp

// Measured on the phone: with equal gaps, the title's letters sat 11dp closer to the art than the
// chips to the slider's thumb, since the slider leaves room above its thumb. The group keeps it.
private val INFO_NUDGE = 6.dp

// The margin of everything else.
private val SIDE_MARGIN = 24.dp

// From the chips to the seek bar, as far as the art was from the title before the art went 16:9.
private val INFO_TO_SEEK = 35.dp

// How the free space splits, in the dp it comes to on the phone (360x780dp): the controls start
// where an equal split around the title would put them, and the art is centered between the
// header's text and the title's letters (each 94dp away), which puts it 4.7dp above the middle of
// its gap, since the header's text sits high in its box and the title's letters low in theirs.
private const val ABOVE_ART = 73.4f
private const val BELOW_ART = 82.74f
private const val BELOW_CONTROLS = 51.06f

// Under the row of add to playlist, favorite and queue: it sits two thirds of its icons' height
// (16dp) above where 8dp put it.
private val BOTTOM_MARGIN = 24.dp
private const val TINT_FADE_END = 0.4f
