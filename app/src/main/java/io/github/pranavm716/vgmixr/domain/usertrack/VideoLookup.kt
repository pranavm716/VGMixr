package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure

/** Looks a YouTube video up once, when it is added: what it is, and whether the app can play it. */
interface VideoLookup {
    suspend fun lookUp(videoId: String): VideoLookupResult
}

/** A video's title and channel, and its length (null when YouTube gives none). */
data class VideoDetails(val title: String, val channel: String, val durationMs: Long?)

sealed interface VideoLookupResult {
    /** The video exists and the player can play its audio. */
    data class Found(val details: VideoDetails) : VideoLookupResult

    /** The video exists, but YouTube gives the app's client no audio it can play. */
    data object NoAudio : VideoLookupResult

    /** The video is removed, private or restricted. */
    data class Unavailable(val reason: String) : VideoLookupResult

    /** Nothing can be looked up right now (network, bot check, extractor). */
    data class Failed(val failure: ResolveFailure) : VideoLookupResult
}
