package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import kotlinx.coroutines.launch

/**
 * Swipe right on [content] to add it to the queue, as Spotify's "Add to queue": past the threshold
 * the row calls [onQueue] and springs back instead of being dismissed. A queue icon behind the row
 * turns the accent past the threshold, with a haptic tick. Also offered as an accessibility action.
 */
@Composable
fun SwipeToQueue(
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * THRESHOLD })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val armed = state.targetValue == SwipeToDismissBoxValue.StartToEnd
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val label = stringResource(R.string.add_to_queue)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(label) {
                    onQueue()
                    true
                }
            )
        },
        enableDismissFromEndToStart = false,
        onDismiss = {
            onQueue()
            scope.launch { state.reset() }
        },
        backgroundContent = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Icon(
                    painterResource(R.drawable.ic_queue_add),
                    contentDescription = null,
                    tint = if (armed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.padding(start = 24.dp)
                )
            }
        }
    ) {
        // Opaque, so the row hides the icon until it moves.
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

// How far the row moves, as a share of its width, before letting go adds it.
private const val THRESHOLD = 0.3f
