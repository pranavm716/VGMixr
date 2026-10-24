package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackId
import kotlin.random.Random

/**
 * A track in the play queue and where it plays from. [queued] is true for one the user added
 * ("Next in queue"). [listIndex] is a tapped track's place in the list it was tapped in (null for a
 * queued one), so turning shuffle off can put the list back in order.
 */
data class QueueEntry(val trackId: String, val context: QueueContext, val listIndex: Int? = null) {
    val queued: Boolean get() = context == QueueContext.Queue
}

/** Where a queued track plays from, as now playing's "Playing from" says. */
sealed interface QueueContext {
    /** Tapped in a family's track list. */
    data class Family(val familyId: String) : QueueContext

    /** Tapped in a playlist. */
    data class Playlist(val playlistId: Long) : QueueContext

    /** Added with swipe to queue. */
    data object Queue : QueueContext

    /** Tapped in the Remixes tab, or a user track tapped anywhere but a playlist. */
    data object Remixes : QueueContext

    /** The context as a short string, to carry it with a player item. */
    val key: String
        get() = when (this) {
            is Family -> "$FAMILY_PREFIX$familyId"
            is Playlist -> "$PLAYLIST_PREFIX$playlistId"
            Queue -> QUEUE_KEY
            Remixes -> REMIXES_KEY
        }

    companion object {
        private const val FAMILY_PREFIX = "family:"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val QUEUE_KEY = "queue"
        private const val REMIXES_KEY = "remixes"

        fun fromKey(key: String): QueueContext? = when {
            key == QUEUE_KEY -> Queue

            key == REMIXES_KEY -> Remixes

            key.startsWith(FAMILY_PREFIX) -> Family(key.removePrefix(FAMILY_PREFIX))

            key.startsWith(PLAYLIST_PREFIX) ->
                key.removePrefix(PLAYLIST_PREFIX).toLongOrNull()?.let(::Playlist)

            else -> null
        }
    }
}

/**
 * Where a track plays from when nothing else says: Remixes for a user track (a remix too, though
 * it has its original's family), else its family.
 */
fun CatalogTrack.homeContext(): QueueContext {
    val familyId = familyId
    return if (familyId == null || UserTrackId.isUserTrack(id)) {
        QueueContext.Remixes
    } else {
        QueueContext.Family(familyId)
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
     * walks back through them. With [shuffle] on, the rest of [trackIds] follows shuffled instead,
     * all of it, so nothing waits behind for "previous".
     */
    fun playFrom(
        trackIds: List<String>,
        context: QueueContext,
        index: Int,
        shuffle: Boolean = false,
        random: Random = Random.Default
    ): QueueEdit.Replace {
        val tapped = trackIds.mapIndexed { i, id -> QueueEntry(id, context, listIndex = i) }
        if (shuffle) {
            val rest = (tapped.take(index) + tapped.drop(index + 1)).shuffled(random)
            return QueueEdit.Replace(listOf(tapped[index]) + upNext + rest, startIndex = 0)
        }
        return QueueEdit.Replace(
            tapped.take(index + 1) + upNext + tapped.drop(index + 1),
            startIndex = index
        )
    }

    /**
     * Playing all of [trackIds] (a long-pressed family or playlist) as tapping its first track does,
     * or with [shuffle] on, a random one: the rest then follows shuffled, so the whole list plays in
     * a shuffled order.
     */
    fun playAll(
        trackIds: List<String>,
        context: QueueContext,
        shuffle: Boolean,
        random: Random = Random.Default
    ): QueueEdit.Replace {
        val first = if (shuffle) random.nextInt(trackIds.size) else 0
        return playFrom(trackIds, context, first, shuffle, random)
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

    /** Removes upcoming entry [index], in "Next in queue" or "Next from"; played ones stay. */
    fun remove(index: Int): List<QueueEdit> = if (index in current + 1 until entries.size) {
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

    /** Where "Next from" starts: after the current track and "Next in queue". */
    private val nextFromStart: Int get() = current + 1 + upNext.size

    /**
     * Turning shuffle on shuffles "Next from" only; "Next in queue" and the tracks played stay as
     * they are.
     */
    fun shuffle(random: Random): List<QueueEdit> {
        if (nextFromContext.size < 2) return emptyList()
        val order = (nextFromStart until entries.size).shuffled(random)
        return listOf(QueueEdit.Rearrange(nextFromStart, entries.size, order))
    }

    /**
     * Turning shuffle off makes "Next from" the whole list after the current track, in list order,
     * tracks that played while shuffled included. If a queued track plays, the list goes on after
     * the last list track that played. Tracks removed from the list stay removed; queued tracks left
     * in "Next from" by going back past them come first.
     */
    fun unshuffle(): List<QueueEdit> {
        val anchor = entries.take(current + 1).lastOrNull { it.listIndex != null }?.listIndex
            ?: return emptyList()
        // Each list position once, at its last place in the queue (the upcoming copy if any).
        val placeOf = entries.withIndex()
            .mapNotNull { (i, entry) -> entry.listIndex?.let { it to i } }
            .toMap()
        // Queued tracks left in "Next from" (by going back past them) stay, ahead of the list.
        val stranded = (nextFromStart until entries.size).filter { entries[it].queued }
        val order = stranded + placeOf.filterKeys { it > anchor }.toSortedMap().values
        val unchanged = order == (nextFromStart until entries.size).toList()
        return if (unchanged) {
            emptyList()
        } else {
            listOf(QueueEdit.Rearrange(nextFromStart, entries.size, order))
        }
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

            is QueueEdit.Rearrange ->
                marked.take(edit.from) + edit.sourceIndices.map { entries[it] to false } +
                    marked.drop(edit.to)

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

    /**
     * Replace entries [from] until [to] with the entries now at [sourceIndices], which may repeat
     * ones elsewhere in the queue. The player reuses its own items for them.
     */
    data class Rearrange(val from: Int, val to: Int, val sourceIndices: List<Int>) : QueueEdit

    /** Play entry [index] from its start. */
    data class Seek(val index: Int) : QueueEdit
}
