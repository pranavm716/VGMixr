package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A sideways swipe between the current track and its neighbours, shared by the mini player and
 * now playing: [offset] follows the finger, and [settle] decides on release. Toward a side with no
 * neighbour (before the first track, past the last) the content stays put, stretching only a
 * little with the finger.
 */
class TrackSwipeState {
    var offset by mutableFloatStateOf(0f)
        private set

    /** The swiped area's width; neighbours sit one width away. */
    var widthPx by mutableFloatStateOf(0f)

    var hasPrevious = false
    var hasNext = false

    // How far the finger has moved, of which offset shows all or, toward no neighbour, a little.
    private var raw = 0f

    val draggable = DraggableState { delta ->
        raw += delta
        val open = if (raw < 0) hasNext else hasPrevious
        offset = if (open) raw else resisted(raw, widthPx / STRETCH_FRACTION)
    }

    /**
     * Past a third of the width, or flung, toward a side with a neighbour: the neighbour slides
     * fully in, the skip runs, and the offset resets once [trackId] has changed (or after a second
     * regardless), so the neighbour becomes the current track in place. Otherwise it springs back.
     */
    suspend fun settle(
        velocity: Float,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        trackId: () -> String?
    ) {
        val before = trackId()
        val towardNext = offset < -widthPx / 3 || velocity < -FLING_VELOCITY
        val towardPrevious = offset > widthPx / 3 || velocity > FLING_VELOCITY
        val skip = when {
            towardNext && hasNext -> -widthPx to onNext
            towardPrevious && hasPrevious -> widthPx to onPrevious
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
        raw = 0f
    }
}

/** Remembers the swipe, telling it each composition which sides have a neighbour. */
@Composable
fun rememberTrackSwipeState(hasPrevious: Boolean, hasNext: Boolean): TrackSwipeState =
    remember { TrackSwipeState() }.also {
        it.hasPrevious = hasPrevious
        it.hasNext = hasNext
    }

/**
 * A rubber band: [rawPx] of finger travel moves the content about half as far at first, then
 * less and less, never reaching [limitPx].
 */
internal fun resisted(rawPx: Float, limitPx: Float): Float {
    if (limitPx <= 0f) return 0f
    return rawPx.sign * limitPx * (1 - 1 / (abs(rawPx) * STRETCH / limitPx + 1))
}

// A swipe this fast (px/s) counts even if it was short.
private const val FLING_VELOCITY = 1500f

// Toward no neighbour, the content stretches at most an eighth of the width.
private const val STRETCH_FRACTION = 8f
private const val STRETCH = 0.55f
