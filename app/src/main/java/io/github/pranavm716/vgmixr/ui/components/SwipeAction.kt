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

/** Swipe right on [content] to add it to the queue, as Spotify's "Add to queue". */
@Composable
fun SwipeToQueue(
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = SwipeAction(
    icon = R.drawable.ic_queue_add,
    label = stringResource(R.string.add_to_queue),
    fromEnd = false,
    onAction = onQueue,
    modifier = modifier,
    content = content
)

/** Swipe left on [content] to remove it from the queue, behind a trash can like Spotify's. */
@Composable
fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = SwipeAction(
    icon = R.drawable.ic_delete,
    label = stringResource(R.string.remove_from_queue),
    fromEnd = true,
    onAction = onRemove,
    modifier = modifier,
    content = content
)

/**
 * Swipe [content] sideways (right, or left when [fromEnd]) to act on it. The row uncovers the
 * accent with [icon] and stops once it has moved [Threshold], with a haptic tick. Letting go there
 * calls [onAction]; letting go short of it (or after dragging back) doesn't. Either way the row
 * springs straight back. Also offered as an accessibility action named [label].
 */
@Composable
private fun SwipeAction(
    @DrawableRes icon: Int,
    label: String,
    fromEnd: Boolean,
    onAction: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val thresholdPx = with(LocalDensity.current) { Threshold.toPx() }
    val haptics = LocalHapticFeedback.current
    var offset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { delta ->
        offset = if (fromEnd) {
            (offset + delta).coerceIn(-thresholdPx, 0f)
        } else {
            (offset + delta).coerceIn(0f, thresholdPx)
        }
        val reached = kotlin.math.abs(offset) >= thresholdPx
        if (reached && !armed) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        armed = reached
    }
    Box(
        modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(label) {
                    onAction()
                    true
                }
            )
        }
    ) {
        if (offset != 0f) {
            Box(
                Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary),
                contentAlignment = if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(
                    painterResource(icon),
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
                        if (armed) onAction()
                        armed = false
                        animate(offset, 0f) { value, _ -> offset = value }
                    }
                )
                .background(MaterialTheme.colorScheme.background)
        ) { content() }
    }
}
