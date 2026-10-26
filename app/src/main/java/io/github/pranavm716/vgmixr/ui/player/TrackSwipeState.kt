package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A sideways swipe between the current track and its neighbours, shared by the mini player and
 * now playing: [offset] follows the finger, and [settle] decides on release.
 */
class TrackSwipeState {
    var offset by mutableFloatStateOf(0f)
        private set

    val draggable = DraggableState { offset += it }

    /**
     * Past a third of [widthPx], or flung, toward a side with a neighbour: the neighbour slides
     * fully in, the skip runs, and the offset resets once [trackId] has changed (or after a second
     * regardless), so the neighbour becomes the current track in place. Otherwise it springs back.
     * A null [onPrevious] or [onNext] means no neighbour on that side.
     */
    suspend fun settle(
        velocity: Float,
        widthPx: Float,
        onPrevious: (() -> Unit)?,
        onNext: (() -> Unit)?,
        trackId: () -> String?
    ) {
        val before = trackId()
        val towardNext = offset < -widthPx / 3 || velocity < -FLING_VELOCITY
        val towardPrevious = offset > widthPx / 3 || velocity > FLING_VELOCITY
        val skip = when {
            towardNext && onNext != null -> -widthPx to onNext
            towardPrevious && onPrevious != null -> widthPx to onPrevious
            else -> null
        }
        if (skip == null) {
            animate(offset, 0f) { value, _ -> offset = value }
        } else {
            animate(offset, skip.first) { value, _ -> offset = value }
            skip.second()
            withTimeoutOrNull(1_000) { snapshotFlow { trackId() }.first { it != before } }
            offset = 0f
        }
    }
}

@Composable
fun rememberTrackSwipeState(): TrackSwipeState = remember { TrackSwipeState() }

// A swipe this fast (px/s) counts even if it was short.
private const val FLING_VELOCITY = 1500f
