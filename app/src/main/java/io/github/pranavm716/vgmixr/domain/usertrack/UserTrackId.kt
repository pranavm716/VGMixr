package io.github.pranavm716.vgmixr.domain.usertrack

/**
 * A user track's id: `yt:` and its video id. No catalog id has a colon, so the two can't collide,
 * and adding a video again gives it back its playlists and history.
 */
object UserTrackId {
    private const val PREFIX = "yt:"

    fun of(videoId: String): String = PREFIX + videoId

    fun isUserTrack(trackId: String): Boolean = trackId.startsWith(PREFIX)

    /** The video id of user track [trackId], or null for a catalog track. */
    fun videoId(trackId: String): String? = trackId.takeIf(::isUserTrack)?.removePrefix(PREFIX)
}
