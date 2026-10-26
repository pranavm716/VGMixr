package io.github.pranavm716.vgmixr.domain.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The app's player. Implemented with Media3; UI and ViewModels only see this interface. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /**
     * Plays track [index] of [trackIds], a list tapped in [context]; the user's queue plays after
     * it.
     */
    fun playFrom(trackIds: List<String>, index: Int, context: QueueContext)

    /**
     * Plays all of [trackIds], a list long-pressed in [context]: in order from its first track, or
     * with shuffle on in a shuffled order, from a random one (see [PlayQueue.playAll]).
     */
    fun playAll(trackIds: List<String>, context: QueueContext)

    /**
     * The collection tapped in [context] is now sorted as [trackIds]. If it is the one playing,
     * the queue follows the new order (see [PlayQueue.resort]); otherwise nothing changes.
     */
    fun resort(trackIds: List<String>, context: QueueContext)

    /** Adds [trackIds] to the end of the user's queue ("Next in queue"). */
    fun addToQueue(trackIds: List<String>)

    fun togglePlayPause()

    /**
     * Turns shuffle on or off: it shuffles "Next from" or puts it back in list order (see
     * [PlayQueue.shuffle], [PlayQueue.unshuffle]).
     */
    fun setShuffle(on: Boolean)

    /** Plays the next queue item, also when paused. */
    fun skipToNext()

    /** Plays the previous queue item (never restarting the current one), also when paused. */
    fun skipToPrevious()

    /**
     * Restarts the current track once it has played past a few seconds, else plays the previous
     * queue item, as the notification's previous button does. Also when paused.
     */
    fun restartOrSkipToPrevious()

    /** Moves within the current track; 0 is its start, also for a slice of an album video. */
    fun seekTo(positionMs: Long)

    /** Plays queue entry [index], by the queue screen's rules (see [PlayQueue.jumpTo]). */
    fun jumpTo(index: Int)

    /** Removes upcoming queue entry [index], from "Next in queue" or "Next from". */
    fun removeFromQueue(index: Int)

    /** Empties "Next in queue". */
    fun clearQueue()

    /**
     * Try again on a track whose sources are all broken: looks its videos up again and un-greys it
     * only if one works now.
     */
    suspend fun tryAgain(trackId: String): TryAgainResult

    /** The current track's position, a few times a second while collected. */
    val progress: Flow<PlaybackProgress>

    /** Things the user hears about once, as they happen, such as a source failing. */
    val events: Flow<PlaybackEvent>
}

/** [durationMs] is null until known; a slice of an album video reports the slice's length. */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long?)

/**
 * [queue] is the player's queue. [trackId] is the current track, playing or paused. [isLoading] is
 * true while it is about to play but waits for its stream. [stoppedBy] is set when a failure that
 * affects every track stopped playback. [shuffle] is the shuffle setting.
 */
data class PlaybackState(
    val queue: PlayQueue = PlayQueue.EMPTY,
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val stoppedBy: ResolveFailure? = null,
    val shuffle: Boolean = false
)
