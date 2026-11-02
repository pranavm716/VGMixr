package io.github.pranavm716.vgmixr.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val IconInset = 24.dp
private val IconSize = 24.dp

// The row acts once its edge is as far past the icon as the icon is from the screen edge.
private val Threshold = IconInset + IconSize + IconInset

// How far back under the threshold the finger must come to disarm (see [SwipeTracker]).
private val DisarmMargin = 16.dp

/** A swipe side: the icon the row uncovers, its accessibility label, and its action. */
private class Side(
    @param:DrawableRes val icon: Int,
    val label: String,
    val onAction: () -> Unit,
    val exits: Boolean = false
)

/** Swipe right on [content] to add it to the queue, as Spotify's "Add to queue". */
@Composable
fun SwipeToQueue(
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) = SwipeAction(
    start = Side(R.drawable.ic_queue_add, stringResource(R.string.add_to_queue), onQueue),
    end = null,
    modifier = modifier,
    enabled = enabled,
    content = content
)

/** Swipe left on [content] to remove it from the queue, behind a trash can like Spotify. */
@Composable
fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = SwipeAction(
    start = null,
    end = Side(
        R.drawable.ic_delete,
        stringResource(R.string.remove_from_queue),
        onRemove,
        exits = true
    ),
    modifier = modifier,
    content = content
)

/** Swipe right on [content] to add it to the queue, or left to remove it. */
@Composable
fun SwipeToQueueOrRemove(
    onQueue: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = SwipeAction(
    start = Side(R.drawable.ic_queue_add, stringResource(R.string.add_to_queue), onQueue),
    end = Side(
        R.drawable.ic_delete,
        stringResource(R.string.remove_from_queue),
        onRemove,
        exits = true
    ),
    modifier = modifier,
    content = content
)

/**
 * Swipe [content] right for [start] or left for [end] (either may be null). The row uncovers the
 * accent with that side's icon; past [Threshold] there is a haptic tick, and letting go runs the
 * action (short of it, or after dragging back past [DisarmMargin], nothing happens). A row that exits (remove) follows
 * the finger as far as it goes and slides off on release; others stop at the threshold and spring
 * back. Each action is also offered for accessibility.
 */
@Composable
private fun SwipeAction(
    start: Side?,
    end: Side?,
    modifier: Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val tracker = remember(density) {
        with(density) { SwipeTracker(Threshold.toPx(), DisarmMargin.toPx()) }
    }
    val limits = SwipeLimits(start.limit(), end.limit())
    val haptics = LocalHapticFeedback.current
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val drag = rememberDraggableState { delta ->
        if (tracker.drag(delta, limits, width.toFloat())) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        offset = tracker.offset
    }
    val sides = if (enabled) listOfNotNull(start, end) else emptyList()
    Box(
        modifier.onSizeChanged { width = it.width }.semantics {
            customActions = sides.map { side ->
                CustomAccessibilityAction(side.label) {
                    side.onAction()
                    true
                }
            }
        }
    ) {
        val uncovered = when {
            offset > 0f -> start
            offset < 0f -> end
            else -> null
        }
        if (uncovered != null) {
            Box(
                Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary),
                contentAlignment = if (offset < 0f) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(
                    painterResource(uncovered.icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(start = IconInset, end = IconInset)
                        .size(IconSize)
                )
            }
        }
        // Opaque, so the row covers the accent until it moves.
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        val side = if (tracker.armed) (if (offset > 0f) start else end) else null
                        tracker.reset()
                        if (side?.exits == true) {
                            // A removal slides the row off its side before it goes, so it exits.
                            val target = if (offset < 0f) -width.toFloat() else width.toFloat()
                            animate(offset, target) { value, _ -> offset = value }
                            side.onAction()
                            // If the row is still here (nothing was removed), bring it back.
                            delay(EXIT_RESET_MS)
                            offset = 0f
                        } else {
                            side?.onAction?.invoke()
                            animate(offset, 0f) { value, _ -> offset = value }
                        }
                    }
                )
                .background(MaterialTheme.colorScheme.background)
        ) { content() }
    }
}

private const val EXIT_RESET_MS = 600L

private fun Side?.limit() = when {
    this == null -> SwipeLimit.NONE
    exits -> SwipeLimit.EXITS
    else -> SwipeLimit.STOPS
}
