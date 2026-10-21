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
}
