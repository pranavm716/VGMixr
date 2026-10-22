package io.github.pranavm716.vgmixr.domain.playback

/** Turns a stream source's video id into a playable URL. URLs expire, so resolve at play time. */
interface StreamResolver {
    suspend fun resolve(videoId: String): StreamResolution
}

sealed interface StreamResolution {
    data class Resolved(val url: String, val mimeType: String, val bitrate: Int) : StreamResolution

    /** This video can't play (removed, private, restricted): try the track's next source. */
    data class Unavailable(val reason: String) : StreamResolution

    /**
     * The video resolves but has no audio stream the player can use, which is how YouTube refusing
     * it to the extractor's client shows: try the track's next source.
     */
    data object NoAudio : StreamResolution

    /** Nothing can resolve right now, whatever the video: stop and show an error. */
    data class Failed(val failure: ResolveFailure) : StreamResolution
}

enum class ResolveFailure { NETWORK, BOT_CHECK, EXTRACTOR }
