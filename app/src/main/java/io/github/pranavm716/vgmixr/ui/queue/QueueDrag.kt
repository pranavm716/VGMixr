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
 * The list keys of the queued rows, playing [newTracks] in order, carried over from the rows
 * before ([old], track to key) so a row that stays keeps its key: a removal or a move must not
 * re-key the rows it passes, or the list animates them again (and a row slid aside to be removed
 * would hand its state to another copy of its track). Rows kept in place are matched by a longest
 * common run of tracks; a row that moved takes an unmatched key of its track; new rows get [fresh].
 */
fun carryKeys(
    old: List<Pair<String, String>>,
    newTracks: List<String>,
    fresh: () -> String
): List<String> {
    val oldTracks = old.map { it.first }
    // lcs[i][j]: the longest common run of oldTracks from i and newTracks from j.
    val lcs = Array(oldTracks.size + 1) { IntArray(newTracks.size + 1) }
    for (i in oldTracks.indices.reversed()) {
        for (j in newTracks.indices.reversed()) {
            lcs[i][j] = if (oldTracks[i] == newTracks[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }
    val keys = arrayOfNulls<String>(newTracks.size)
    val used = BooleanArray(old.size)
    var i = 0
    var j = 0
    while (i < oldTracks.size && j < newTracks.size) {
        when {
            oldTracks[i] == newTracks[j] -> {
                keys[j] = old[i].second
                used[i] = true
                i++
                j++
            }

            lcs[i + 1][j] >= lcs[i][j + 1] -> i++

            else -> j++
        }
    }
    return newTracks.mapIndexed { at, track ->
        keys[at] ?: old.indices.firstOrNull { !used[it] && oldTracks[it] == track }
            ?.let {
                used[it] = true
                old[it].second
            }
            ?: fresh()
    }
}

/** Keeps the queued rows' keys from one queue to the next (see [carryKeys]). */
class QueueKeys {
    private var last: List<Pair<String, String>> = emptyList()
    private var lastQueue: Any? = null
    private var count = 0

    /** The key of each row of [queue] (entry index and track id), by entry index. */
    fun keysFor(queue: Any, rows: List<Pair<Int, String>>): Map<Int, String> {
        if (queue !== lastQueue) {
            val keys = carryKeys(last, rows.map { it.second }) { "upnext-${count++}" }
            last = rows.map { it.second }.zip(keys)
            lastQueue = queue
        }
        return rows.map { it.first }.zip(last.map { it.second }).toMap()
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
