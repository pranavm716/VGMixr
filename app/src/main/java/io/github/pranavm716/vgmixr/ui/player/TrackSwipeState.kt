package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A sideways swipe between the current track and its neighbours, shared by the mini player and
 * now playing: [offset] follows the finger, and [settle] decides on release. Toward a side with no
 * neighbour (before the first track, past the last) the content stays put and [stretch]es a
 * little toward the finger instead.
 */
class TrackSwipeState {
    var offset by mutableFloatStateOf(0f)
        private set

    /**
     * How much wider the current track's content is drawn, as a share of its width: positive
     * toward the right, negative toward the left; 0 unless swiped toward no neighbour. See
     * [trackStretch].
     */
    var stretch by mutableFloatStateOf(0f)
        private set

    /** The swiped area's width; neighbours sit one width away. */
    var widthPx by mutableFloatStateOf(0f)

    var hasPrevious = false
    var hasNext = false

    // How far the finger has moved: the offset toward a neighbour, else what the stretch follows.
    private var raw = 0f

    val draggable = DraggableState { delta ->
        raw += delta
        val open = if (raw < 0) hasNext else hasPrevious
        if (open || widthPx <= 0f) {
            offset = raw
            stretch = 0f
        } else {
            offset = 0f
            stretch = resisted(raw / widthPx, MAX_STRETCH)
        }
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
            if (stretch != 0f) animate(stretch, 0f) { value, _ -> stretch = value }
            if (offset != 0f) animate(offset, 0f) { value, _ -> offset = value }
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
 * Draws the current track's content [TrackSwipeState.stretch]ed: wider, held at the edge away from
 * the finger, so it reaches toward it.
 */
fun Modifier.trackStretch(swipe: TrackSwipeState) = graphicsLayer {
    val stretch = swipe.stretch
    scaleX = 1 + abs(stretch)
    transformOrigin = TransformOrigin(if (stretch > 0) 0f else 1f, 0.5f)
}

/**
 * A rubber band: [raw] finger travel gives about half as much at first, then less and less,
 * never reaching [limit].
 */
internal fun resisted(raw: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    return raw.sign * limit * (1 - 1 / (abs(raw) * RESISTANCE / limit + 1))
}

// A swipe this fast (px/s) counts even if it was short.
private const val FLING_VELOCITY = 1500f

// Toward no neighbour, the content grows at most a tenth wider.
internal const val MAX_STRETCH = 0.1f
private const val RESISTANCE = 0.55f
