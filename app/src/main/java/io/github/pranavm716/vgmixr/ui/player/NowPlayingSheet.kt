package io.github.pranavm716.vgmixr.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Upward velocity (px/s) past which a release opens the sheet whatever its position. */
const val SHEET_FLING_VELOCITY = 1500f

/**
 * Whether a released sheet settles open: a flick decides by its direction, otherwise the sheet
 * opens from halfway up. [velocity] is in px/s, negative upward.
 */
fun settleTarget(expansion: Float, velocity: Float, flingVelocity: Float): Boolean = when {
    velocity <= -flingVelocity -> true
    velocity >= flingVelocity -> false
    else -> expansion >= 0.5f
}

/**
 * The now-playing sheet's position, shared by the mini player that grows into it and the sheet
 * itself: [expansion] runs from 0 (only the mini player) to 1 (now playing fills the screen).
 * [onSettled] reports where a release or animation is heading, open or not.
 */
@Stable
class NowPlayingSheetState(
    private val scope: CoroutineScope,
    private val onSettled: (open: Boolean) -> Unit
) {
    var expansion by mutableFloatStateOf(0f)
        private set

    /** The mini player's bounds on screen, where the sheet starts and ends. */
    var collapsedBounds by mutableStateOf(Rect.Zero)

    /** The navbar's height; it slides down out of view by [expansion] of it as the sheet opens. */
    var navBarHeightPx by mutableFloatStateOf(0f)

    private var animation: Job? = null

    /** Moves the sheet with a drag of [deltaPx] (negative upward). */
    fun dragBy(deltaPx: Float) {
        animation?.cancel()
        val travel = collapsedBounds.top.takeIf { it > 0f } ?: return
        expansion = (expansion - deltaPx / travel).coerceIn(0f, 1f)
    }

    /** Lets the sheet go with [velocity] (px/s, negative upward). */
    fun settle(velocity: Float) {
        val open = settleTarget(expansion, velocity, SHEET_FLING_VELOCITY)
        onSettled(open)
        animateTo(open, ReleaseSpec)
    }

    /** Closes the sheet, as back does. */
    fun close() {
        onSettled(false)
        animateTo(false)
    }

    /**
     * Animates the sheet open or closed: by default at the gentler pace of a tap, the notification
     * or back; a release after a drag passes the quicker [ReleaseSpec]. An animation already heading
     * the same way is left to finish.
     */
    fun animateTo(open: Boolean, spec: AnimationSpec<Float> = TapSpec) {
        val target = if (open) 1f else 0f
        if (expansion == target) return
        if (heading == target && animation?.isActive == true) return
        animation?.cancel()
        heading = target
        animation = scope.launch {
            animate(expansion, target, animationSpec = spec) { value, _ -> expansion = value }
        }
    }

    private var heading: Float? = null

    private companion object {
        // A tap opens or closes the sheet at an easy pace; a release finishes a drag quicker.
        val TapSpec: AnimationSpec<Float> = tween(500, easing = FastOutSlowInEasing)
        val ReleaseSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)
    }
}

/**
 * Now playing, as a solid panel sliding up out of the mini player, as in Shuttle: its top edge goes
 * from the mini player's top to the screen's, its bottom rests on the navbar, which slides down out
 * of view as it opens. At the top, [collapsed] (the mini player's look) cross-fades into [header] in
 * the same place; [content], below, is solid all the way. Dragging it down or going back closes it.
 */
@Composable
fun NowPlayingSheet(
    state: NowPlayingSheetState,
    collapsed: @Composable () -> Unit,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val shown by remember { derivedStateOf { state.expansion > 0f } }
    if (!shown) return
    val showCollapsed by remember { derivedStateOf { state.expansion < COLLAPSED_FADE_END } }
    BackHandler { state.close() }
    // Outside the Scaffold, so text needs the theme's content color given here.
    val textColor = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // The drag lives on this full-screen layer, which stays put: on the sheet, which moves with the
    // finger, each step would also move the sheet and only part of the drag would register. While
    // the sheet is out, taps stay on it instead of reaching the list behind.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) {}
            .draggable(
                state = rememberDraggableState { state.dragBy(it) },
                orientation = Orientation.Vertical,
                onDragStopped = { state.settle(it) }
            )
    ) {
        val screen = constraints.maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, outer ->
                    val f = state.expansion
                    val bounds = state.collapsedBounds
                    val collapsedTop = if (bounds == Rect.Zero) screen.toFloat() else bounds.top
                    val top = (collapsedTop * (1 - f)).roundToInt()
                    // Rests on the navbar, which slides down by f of its height.
                    val bottom = (screen - state.navBarHeightPx * (1 - f)).roundToInt()
                    val sheet = measurable.measure(
                        Constraints.fixed(outer.maxWidth, (bottom - top).coerceAtLeast(0))
                    )
                    layout(outer.maxWidth, screen) { sheet.place(0, top) }
                }
                .clipToBounds()
                .background(MaterialTheme.colorScheme.background)
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                // The now-playing screen at the screen's full size, solid, moving with the top edge.
                Box(
                    Modifier.layout { measurable, inner ->
                        val placeable =
                            measurable.measure(Constraints.fixed(inner.maxWidth, screen))
                        layout(inner.maxWidth, inner.maxHeight) { placeable.place(0, 0) }
                    }
                ) { content() }
                // The top row: the mini player's look turning into the header in the same place,
                // easing down under the status bar as the sheet reaches the top.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = statusBarPx * state.expansion }
                ) {
                    Box(
                        Modifier.graphicsLayer {
                            alpha =
                                ((state.expansion - HEADER_FADE_START) / (1 - HEADER_FADE_START))
                                    .coerceIn(0f, 1f)
                        }
                    ) { header() }
                    if (showCollapsed) {
                        Box(
                            Modifier.graphicsLayer {
                                alpha = (1 - state.expansion / COLLAPSED_FADE_END).coerceIn(0f, 1f)
                            }
                        ) { collapsed() }
                    }
                }
            }
        }
    }
}

// The mini player's look fades out over the first half; the header fades in from 30%.
private const val COLLAPSED_FADE_END = 0.5f
private const val HEADER_FADE_START = 0.3f
