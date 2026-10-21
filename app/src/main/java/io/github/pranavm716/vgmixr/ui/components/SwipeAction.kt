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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

private val IconInset = 24.dp
private val IconSize = 24.dp

// The row acts once its edge is as far past the icon as the icon is from the screen edge.
private val Threshold = IconInset + IconSize + IconInset

/** A swipe side: the icon the row uncovers, its accessibility label, and its action. */
private class Side(@param:DrawableRes val icon: Int, val label: String, val onAction: () -> Unit)

/** Swipe right on [content] to add it to the queue, as Spotify's "Add to queue". */
@Composable
fun SwipeToQueue(
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = SwipeAction(
    start = Side(R.drawable.ic_queue_add, stringResource(R.string.add_to_queue), onQueue),
    end = null,
    modifier = modifier,
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
    end = Side(R.drawable.ic_delete, stringResource(R.string.remove_from_queue), onRemove),
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
    end = Side(R.drawable.ic_delete, stringResource(R.string.remove_from_queue), onRemove),
    modifier = modifier,
    content = content
)

/**
 * Swipe [content] right for [start] or left for [end] (either may be null). The row uncovers the
 * accent with that side icon and stops once it has moved [Threshold], with a haptic tick. Letting
 * go there runs the action; letting go short of it (or after dragging back) does not. Either way
 * the row springs straight back. Each action is also offered for accessibility.
 */
@Composable
private fun SwipeAction(
    start: Side?,
    end: Side?,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val thresholdPx = with(LocalDensity.current) { Threshold.toPx() }
    val haptics = LocalHapticFeedback.current
    var offset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { delta ->
        val min = if (end != null) -thresholdPx else 0f
        val max = if (start != null) thresholdPx else 0f
        offset = (offset + delta).coerceIn(min, max)
        val reached = kotlin.math.abs(offset) >= thresholdPx
        if (reached && !armed) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        armed = reached
    }
    val sides = listOfNotNull(start, end)
    Box(
        modifier.semantics {
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
                    onDragStopped = {
                        if (armed) (if (offset > 0f) start else end)?.onAction?.invoke()
                        armed = false
                        animate(offset, 0f) { value, _ -> offset = value }
                    }
                )
                .background(MaterialTheme.colorScheme.background)
        ) { content() }
    }
}
