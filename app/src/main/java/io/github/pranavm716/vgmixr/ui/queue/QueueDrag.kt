package io.github.pranavm716.vgmixr.ui.queue

/** A queued row as laid out: its queue entry [index] and where it spans on screen. */
data class RowSpan(val index: Int, val top: Float, val bottom: Float)

/**
 * The queued row the dragged one ([dragged]) should take the place of: the other row its center
 * ([centerY]) is over, or null over its own place or past the queued rows (so it stays inside
 * "Next in queue").
 */
fun hoveredRow(centerY: Float, rows: List<RowSpan>, dragged: Int): Int? =
    rows.firstOrNull { it.index != dragged && centerY >= it.top && centerY < it.bottom }?.index

/**
 * How far to scroll per frame while a finger at [fingerY] drags a row: toward the top within
 * [edge] of [top], toward the bottom within [edge] of [bottom], up to [max] at (or past) the edge.
 */
fun autoScrollSpeed(fingerY: Float, top: Float, bottom: Float, edge: Float, max: Float): Float =
    when {
        fingerY < top + edge -> -max * ((top + edge - fingerY) / edge).coerceAtMost(1f)
        fingerY > bottom - edge -> max * ((fingerY - (bottom - edge)) / edge).coerceAtMost(1f)
        else -> 0f
    }

/**
 * Whether to keep scrolling by [speed]: only while the queue's row at that end ([edgeRow], its last
 * row going down, its first going up; null when off screen) is still partly out of the viewport
 * ([top] to [bottom]). Past it the dragged row, held inside the queue, would scroll out of view.
 */
fun canAutoScroll(speed: Float, edgeRow: RowSpan?, top: Float, bottom: Float): Boolean = when {
    edgeRow == null -> true
    speed > 0f -> edgeRow.bottom > bottom
    speed < 0f -> edgeRow.top < top
    else -> false
}

/**
 * The list keys of the queued rows, playing [trackIds] in order: each by its track and which copy
 * of it it is, so moving one row doesn't re-key the rows it passes (keys by queue position would,
 * and the list would animate them all again).
 */
fun queuedKeys(trackIds: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return trackIds.map { id ->
        val copy = seen.getOrDefault(id, 0)
        seen[id] = copy + 1
        "upnext-$id-$copy"
    }
}

/** How far to scroll so [row] is wholly inside the viewport ([top] to [bottom]); 0 if it is. */
fun scrollIntoView(row: RowSpan, top: Float, bottom: Float): Float = when {
    row.top < top -> row.top - top
    row.bottom > bottom -> row.bottom - bottom
    else -> 0f
}

/**
 * Where to draw a dragged row laid out at [top], [size] tall, that the finger has moved by
 * [offset]: following it, but held wholly inside the viewport ([viewTop] to [viewBottom]), since
 * the list cuts off anything past its edges.
 */
fun drawnOffset(offset: Float, top: Float, size: Float, viewTop: Float, viewBottom: Float): Float =
    offset.coerceIn(viewTop - top, viewBottom - size - top)
