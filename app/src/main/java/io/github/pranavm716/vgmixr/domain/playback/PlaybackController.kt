package io.github.pranavm716.vgmixr.domain.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The app's player. Implemented with Media3; UI and ViewModels only see this interface. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Plays track [index] of [trackIds] (a tapped list); the user's queue plays after it. */
    fun playFrom(trackIds: List<String>, index: Int)

    /** Adds [trackIds] to the end of the user's queue ("Next in queue"). */
    fun addToQueue(trackIds: List<String>)

    fun togglePlayPause()

    /** Plays the next queue item, also when paused. */
    fun skipToNext()

    /** Plays the previous queue item (never restarting the current one), also when paused. */
    fun skipToPrevious()

    /** The current track's position, a few times a second while collected. */
    val progress: Flow<PlaybackProgress>
}

/** [durationMs] is null until known; a slice of an album video reports the slice's length. */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long?)

/**
 * [queue] is the player's queue. [trackId] is the current track, playing or paused. [isLoading] is true while it is about to play
 * but waits for its stream. [stoppedBy] is set when a failure that affects every track stopped
 * playback.
 */
data class PlaybackState(
    val queue: PlayQueue = PlayQueue.EMPTY,
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val stoppedBy: ResolveFailure? = null
)
