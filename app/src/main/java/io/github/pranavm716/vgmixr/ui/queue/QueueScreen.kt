package io.github.pranavm716.vgmixr.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueueOrRemove
import io.github.pranavm716.vgmixr.ui.components.SwipeToRemove
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import kotlinx.coroutines.launch

/**
 * The queue, Spotify's two tiers: now playing, "Next in queue" (swipe left to remove a track,
 * drag its handle to move it within the queue, or clear them all) and "Next from" the family (swipe right to queue a track, left to remove
 * it). Tapping a track plays it.
 */
@Composable
fun QueueScreen(onClose: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drag = rememberQueueDragState(listState)
    val haptics = LocalHapticFeedback.current
    // Playing a track from the list brings the list back to the top, where it now plays.
    val jump: (Int) -> Unit = { index ->
        viewModel.onJump(index)
        scope.launch { listState.animateScrollToItem(0) }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.queue), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            state.nowPlaying?.let { row ->
                section(R.string.now_playing)
                item(key = "now-${row.index}") {
                    QueueRow(row, current = true, onClick = {})
                }
            }
            if (state.upNext.isNotEmpty()) {
                item(key = "upnext-header") {
                    SectionHeader(stringResource(R.string.next_in_queue)) {
                        TextButton(onClick = viewModel::onClear) {
                            Text(stringResource(R.string.clear_queue))
                        }
                    }
                }
                // Shown in the dragged order while a row moves, and until the player has moved it.
                val shown = drag.orderFor(state.upNext)
                    ?.mapNotNull { index -> state.upNext.firstOrNull { it.index == index } }
                    ?.takeIf { it.size == state.upNext.size }
                    ?: state.upNext
                val keys = state.upNext.map { it.index }
                    .zip(queuedKeys(state.upNext.map { it.trackId }))
                    .toMap()
                items(shown, key = { keys.getValue(it.index) }) { row ->
                    val lifted = drag.liftedKey == keys.getValue(row.index)
                    // The rows around it, for the Move up and Move down accessibility actions.
                    val above = shown.getOrNull(shown.indexOf(row) - 1)
                    val below = shown.getOrNull(shown.indexOf(row) + 1)
                    Box(
                        if (lifted) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = drag.drawn()
                                    shadowElevation = 8.dp.toPx() * drag.lift
                                }
                        } else {
                            // Rows slide into place, but don't fade: a removed row, slid aside
                            // over the accent, would linger as a band of it.
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                        }
                    ) {
                        SwipeToRemove(onRemove = { viewModel.onRemove(row.index) }) {
                            QueueRow(row, current = false, onClick = { jump(row.index) }) {
                                DragHandle(
                                    onStart = { touchY ->
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                        drag.start(
                                            row.index,
                                            shown.map { it.index to keys.getValue(it.index) },
                                            touchY,
                                            state.upNext
                                        )
                                    },
                                    onDrag = drag::dragBy,
                                    onEnd = {
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        drag.end()?.let { (from, to) -> viewModel.onMove(from, to) }
                                    },
                                    onMoveUp = above?.let {
                                        { viewModel.onMove(row.index, it.index) }
                                    },
                                    onMoveDown = below?.let {
                                        { viewModel.onMove(row.index, it.index) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (state.nextFrom.isNotEmpty()) {
                item(key = "nextfrom-header") {
                    SectionHeader(
                        state.nextFromName?.let { stringResource(R.string.next_from, it) }
                            ?: stringResource(R.string.next_in_queue)
                    )
                }
                items(state.nextFrom, key = { "nextfrom-${it.index}-${it.trackId}" }) { row ->
                    // A greyed track can still be removed, but not queued.
                    if (row.unplayable) {
                        SwipeToRemove(onRemove = { viewModel.onRemove(row.index) }) {
                            QueueRow(row, current = false, onClick = { jump(row.index) })
                        }
                    } else {
                        SwipeToQueueOrRemove(
                            onQueue = { viewModel.onQueue(row.index) },
                            onRemove = { viewModel.onRemove(row.index) }
                        ) {
                            QueueRow(row, current = false, onClick = { jump(row.index) })
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: Int) {
    item(key = "header-$title") { SectionHeader(stringResource(title)) }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        action()
    }
}

@Composable
private fun QueueRow(
    row: QueueRowUi,
    current: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) = TrackTileRow(
    row.title,
    row.chips,
    row.unplayable,
    current,
    onClick,
    trailing = trailing,
    thumbnail = row.thumbnail,
    subtitle = row.subtitle,
    familyId = row.familyId
)

/**
 * A queued row's handle: dragging it moves the row ([onStart] gets the touch's height in the row,
 * [onDrag] each vertical step, [onEnd] the release or cancel). Move up and down are offered for
 * accessibility where there is a place to move to.
 */
@Composable
private fun DragHandle(
    onStart: (touchY: Float) -> Unit,
    onDrag: (dy: Float) -> Unit,
    onEnd: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    var handleTop by remember { mutableFloatStateOf(0f) }
    // The gesture outlives recompositions, and a row keeps its handle when it moves: read the
    // latest callbacks, which know the row's current place.
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier
            .size(48.dp)
            .onPlaced { handleTop = it.positionInParent().y }
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let {
                        CustomAccessibilityAction(moveUp) {
                            it()
                            true
                        }
                    },
                    onMoveDown?.let {
                        CustomAccessibilityAction(moveDown) {
                            it()
                            true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start(handleTop + it.y) },
                    onDragEnd = { end() },
                    onDragCancel = { end() }
                ) { change, amount ->
                    change.consume()
                    drag(amount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_drag_handle),
            contentDescription = stringResource(R.string.drag_to_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
