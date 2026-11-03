package io.github.pranavm716.vgmixr.ui.queue

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Within this of the list's top or bottom edge, a dragged row scrolls the list.
private val ScrollEdge = 64.dp

// The fastest the list scrolls, per frame, with the finger at (or past) an edge.
private val ScrollMax = 12.dp

// How long a dropped row takes to glide into its place.
private const val SETTLE_MS = 200

/**
 * A queued row being dragged by its handle. While it moves, [order] holds the queued entries in
 * their shown order and [drawn] where the dragged row is drawn, from its place in it. The order
 * stays after the drop until the queue changes, so the row doesn't jump back before the player
 * has moved it.
 */
@Stable
class QueueDragState internal constructor(
    private val listState: LazyListState,
    private val edgePx: Float,
    private val maxPx: Float
) {
    /** The queue entry index of the row being dragged, or null. */
    var dragged by mutableStateOf<Int?>(null)
        private set

    // How far the finger has moved the dragged row from its place; see [drawn] for where it shows.
    private var offset by mutableFloatStateOf(0f)

    /** The list key of the row drawn lifted: while it's dragged, and while it settles after. */
    var liftedKey by mutableStateOf<String?>(null)
        private set

    /** How lifted that row is, 1 while dragged down to 0 as it lands (its shadow). */
    var lift by mutableFloatStateOf(0f)
        private set

    // Counts drops, so each one starts a settle.
    internal var drops by mutableIntStateOf(0)
        private set

    /** The queued entry indices in the order shown, or null to show the queue as it is. */
    var order by mutableStateOf<List<Int>?>(null)
        private set

    // The queue the order was made from: once the player's queue changes, the order no longer
    // applies, from that very frame (its entry indices may now name other tracks).
    private var base: Any? = null

    private var keys: Map<Int, String> = emptyMap()
    private var fingerY = 0f

    /** Picks up entry [index] of [queued] (entry index to its list key, in the order shown). */
    fun start(index: Int, queued: List<Pair<Int, String>>, touchY: Float, from: Any) {
        keys = queued.toMap()
        if (base !== from) order = null
        base = from
        order = order ?: queued.map { it.first }
        // Picked up again while it settles: from where it's drawn, so it doesn't jump.
        offset = if (liftedKey == keys[index]) drawn() else 0f
        liftedKey = keys[index]
        lift = 1f
        dragged = index
        fingerY = (item(index)?.offset ?: 0) + offset + touchY
    }

    fun dragBy(dy: Float) {
        if (dragged == null) return
        // Held at the list's edges, so the row turns back as soon as the finger does.
        offset = (offset + dy).let {
            offset = it
            drawn()
        }
        fingerY += dy
        swapIfOver()
    }

    /** Ends the drag: the move to make (from, to), or null if the row is back in its place. */
    fun end(): Pair<Int, Int>? {
        val from = dragged ?: return null
        val shown = order ?: return null
        dragged = null
        drops++
        val to = shown.min() + shown.indexOf(from)
        if (to == from) order = null
        return (from to to).takeIf { to != from }
    }

    /** The order to show [queue] in: the dragged order while it applies to [queue], else null. */
    fun orderFor(queue: Any): List<Int>? = order.takeIf { base === queue }

    /** Scrolls the list each frame while the finger is near an edge, until the drag ends. */
    suspend fun autoScroll() {
        while (dragged != null) {
            val info = listState.layoutInfo
            val speed = autoScrollSpeed(
                fingerY,
                info.viewportStartOffset.toFloat(),
                info.viewportEndOffset.toFloat(),
                edgePx,
                maxPx
            )
            val shown = order.orEmpty()
            val edge = (if (speed > 0f) shown.lastOrNull() else shown.firstOrNull())?.let { index ->
                item(index)?.let {
                    RowSpan(index, it.offset.toFloat(), (it.offset + it.size).toFloat())
                }
            }
            val top = info.viewportStartOffset.toFloat()
            val bottom = info.viewportEndOffset.toFloat()
            if (speed != 0f && canAutoScroll(speed, edge, top, bottom)) {
                // The row stays under the finger while the list moves under both.
                val scrolled = listState.scrollBy(speed)
                offset = (offset + scrolled).let {
                    offset = it
                    drawn()
                }
                swapIfOver()
            }
            withFrameNanos { }
        }
    }

    /**
     * Where the lifted row is drawn, from its place: [offset], held inside the list's viewport so
     * the list's edges (the title bar above) never hide it. Read while drawing.
     */
    fun drawn(): Float {
        val key = liftedKey ?: return 0f
        val info = listState.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return offset
        return drawnOffset(
            offset,
            me.offset.toFloat(),
            me.size.toFloat(),
            info.viewportStartOffset.toFloat(),
            info.viewportEndOffset.toFloat()
        )
    }

    /**
     * Lands the dropped row: scrolls just enough to show its place, if it's partly off screen,
     * then glides it there from under the finger as its shadow fades.
     */
    suspend fun settle() {
        val key = liftedKey ?: return
        if (dragged != null) return
        val info = listState.layoutInfo
        info.visibleItemsInfo.firstOrNull { it.key == key }?.let {
            val row = RowSpan(0, it.offset.toFloat(), (it.offset + it.size).toFloat())
            val delta = scrollIntoView(
                row,
                info.viewportStartOffset.toFloat(),
                info.viewportEndOffset.toFloat()
            )
            // The row stays where it's drawn while the list moves its place into view.
            if (delta != 0f) offset += listState.scrollBy(delta)
        }
        val from = drawn()
        animate(0f, 1f, animationSpec = tween(SETTLE_MS)) { progress, _ ->
            offset = from * (1f - progress)
            lift = 1f - progress
        }
        liftedKey = null
    }

    private fun item(index: Int) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keys[index] }

    // Swaps the dragged row with the queued row its center is over, keeping it where it's drawn.
    private fun swapIfOver() {
        val from = dragged ?: return
        val shown = order ?: return
        val me = item(from) ?: return
        val spans = shown.mapNotNull { index ->
            item(index)?.let {
                RowSpan(index, it.offset.toFloat(), (it.offset + it.size).toFloat())
            }
        }
        val center = me.offset + drawn() + me.size / 2f
        val target = hoveredRow(center, spans, from) ?: return
        val targetTop = spans.first { it.index == target }.top
        // The list keeps its place by its first visible row; when that row is one of the two
        // swapping, it would follow it and undo the scroll. Pin the place where it is instead.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        order = shown.toMutableList().apply {
            remove(from)
            add(shown.indexOf(target), from)
        }
        listState.requestScrollToItem(firstIndex, firstOffset)
        offset += me.offset - targetTop
    }
}

@Composable
fun rememberQueueDragState(listState: LazyListState): QueueDragState {
    val density = LocalDensity.current
    val state = remember(listState, density) {
        with(density) { QueueDragState(listState, ScrollEdge.toPx(), ScrollMax.toPx()) }
    }
    LaunchedEffect(state, state.dragged != null) { state.autoScroll() }
    // A new pickup cancels a settle in progress (the key changes) and takes over from there.
    LaunchedEffect(state, state.drops, state.dragged != null) { state.settle() }
    return state
}
