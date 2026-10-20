package io.github.pranavm716.vgmixr.domain.playback

import kotlinx.coroutines.flow.StateFlow

/** The app's player. Implemented with Media3; UI and ViewModels only see this interface. */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Replaces the queue with [trackIds] and plays from [startIndex]. */
    fun play(trackIds: List<String>, startIndex: Int = 0)
}

/** [stoppedBy] is set when a failure that affects every track stopped playback. */
data class PlaybackState(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val stoppedBy: ResolveFailure? = null
)
