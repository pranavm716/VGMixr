package io.github.pranavm716.vgmixr.domain.playback

/**
 * A track in the play queue and where it plays from. [queued] is true for one the user added
 * ("Next in queue").
 */
data class QueueEntry(val trackId: String, val context: QueueContext) {
    val queued: Boolean get() = context == QueueContext.Queue
}

/** Where a queued track plays from, as now playing's "Playing from" says. */
sealed interface QueueContext {
    /** Tapped in a family's track list. */
    data class Family(val familyId: String) : QueueContext

    /** Added with swipe to queue. */
    data object Queue : QueueContext

    /** The context as a short string, to carry it with a player item. */
    val key: String
        get() = when (this) {
            is Family -> "$FAMILY_PREFIX$familyId"
            Queue -> QUEUE_KEY
        }

    companion object {
        private const val FAMILY_PREFIX = "family:"
        private const val QUEUE_KEY = "queue"

        fun fromKey(key: String): QueueContext? = when {
            key == QUEUE_KEY -> Queue
            key.startsWith(FAMILY_PREFIX) -> Family(key.removePrefix(FAMILY_PREFIX))
            else -> null
        }
    }
}

/**
 * The player's queue, Spotify-style: `[earlier tracks…, current, Next in queue…, Next from the
 * family…]`. Tracks the user adds play right after the current one, ahead of the rest of what was
 * tapped. [current] is -1 when the queue is empty. The rules only compute edits; the player applies
 * them.
 */
data class PlayQueue(val entries: List<QueueEntry>, val current: Int) {
    /** "Next in queue": the added tracks waiting after the current one. */
    val upNext: List<QueueEntry> get() = entries.drop(current + 1).takeWhile { it.queued }

    /** "Next from": the rest of the tapped list, after the queue. */
    val nextFromContext: List<QueueEntry> get() = entries.drop(current + 1 + upNext.size)

    /**
     * Tapping track [index] of [trackIds], tapped in [context]: it plays now, the queue still waits
     * right after it, and the rest of [trackIds] follows. The tracks before it stay, so "previous"
     * walks back through them.
     */
    fun playFrom(trackIds: List<String>, context: QueueContext, index: Int): QueueEdit.Replace {
        val tapped = trackIds.map { QueueEntry(it, context) }
        return QueueEdit.Replace(
            tapped.take(index + 1) + upNext + tapped.drop(index + 1),
            startIndex = index
        )
    }

    /**
     * Adding [trackIds] puts them at the end of "Next in queue". With nothing to play (an empty
     * queue, or playback that ended) they start playing instead, since nothing would show they were
     * added.
     */
    fun add(trackIds: List<String>, hasEnded: Boolean): QueueEdit {
        val added = trackIds.map { QueueEntry(it, QueueContext.Queue) }
        return if (entries.isEmpty() || hasEnded) {
            QueueEdit.Replace(added, startIndex = 0)
        } else {
            QueueEdit.Insert(current + 1 + upNext.size, added)
        }
    }

    /**
     * Jumping to entry [index] from the queue screen. Into "Next in queue": the queued tracks
     * before it are dropped. Into "Next from": the waiting queue moves right after it, as
     * Spotify's does, and the tracks skipped become history for "previous". Either way it plays.
     */
    fun jumpTo(index: Int): List<QueueEdit> {
        if (index == current || index !in entries.indices) return emptyList()
        val queueStart = current + 1
        val queueEnd = queueStart + upNext.size
        return when {
            index < current -> listOf(QueueEdit.Seek(index))

            index < queueEnd -> buildList {
                if (index > queueStart) add(QueueEdit.Remove(queueStart, index))
                add(QueueEdit.Seek(queueStart))
            }

            else -> {
                // With the queue block taken out, the target sits upNext.size earlier.
                val target = index - upNext.size
                buildList {
                    if (upNext.isNotEmpty()) add(QueueEdit.Move(queueStart, queueEnd, target + 1))
                    add(QueueEdit.Seek(target))
                }
            }
        }
    }

    /** Removes entry [index] if it waits in "Next in queue"; anything else stays. */
    fun remove(index: Int): List<QueueEdit> =
        if (index in current + 1 until current + 1 + upNext.size) {
            listOf(QueueEdit.Remove(index, index + 1))
        } else {
            emptyList()
        }

    /** Empties "Next in queue"; the current track keeps playing. */
    fun clearQueue(): List<QueueEdit> = if (upNext.isEmpty()) {
        emptyList()
    } else {
        listOf(QueueEdit.Remove(current + 1, current + 1 + upNext.size))
    }

    /** The queue after [edits], applied in order as the player applies them. */
    fun applying(edits: List<QueueEdit>): PlayQueue = edits.fold(this) { queue, edit ->
        queue.applying(edit)
    }

    private fun applying(edit: QueueEdit): PlayQueue {
        // Tracks the current entry by marking it, so moves and removals can't lose it.
        val marked = entries.mapIndexed { i, entry -> entry to (i == current) }
        val result: List<Pair<QueueEntry, Boolean>> = when (edit) {
            is QueueEdit.Replace -> return PlayQueue(edit.entries, edit.startIndex)

            is QueueEdit.Seek -> return copy(current = edit.index)

            is QueueEdit.Insert ->
                marked.take(edit.index) + edit.entries.map { it to false } + marked.drop(edit.index)

            is QueueEdit.Remove -> marked.take(edit.from) + marked.drop(edit.to)

            is QueueEdit.Move -> {
                val moving = marked.subList(edit.from, edit.to)
                val rest = marked.take(edit.from) + marked.drop(edit.to)
                rest.take(edit.newIndex) + moving + rest.drop(edit.newIndex)
            }
        }
        return PlayQueue(result.map { it.first }, result.indexOfFirst { it.second })
    }

    companion object {
        val EMPTY = PlayQueue(emptyList(), current = -1)
    }
}

/** A change to the player's queue. */
sealed interface QueueEdit {
    /** Replace the whole queue and play [startIndex] from its start. */
    data class Replace(val entries: List<QueueEntry>, val startIndex: Int) : QueueEdit

    /** Insert [entries] at [index] without interrupting playback. */
    data class Insert(val index: Int, val entries: List<QueueEntry>) : QueueEdit

    /** Move entries [from] until [to] so the first lands at [newIndex] of the list without them. */
    data class Move(val from: Int, val to: Int, val newIndex: Int) : QueueEdit

    /** Remove entries [from] until [to]. */
    data class Remove(val from: Int, val to: Int) : QueueEdit

    /** Play entry [index] from its start. */
    data class Seek(val index: Int) : QueueEdit
}
