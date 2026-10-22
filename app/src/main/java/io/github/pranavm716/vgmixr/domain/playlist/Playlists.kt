package io.github.pranavm716.vgmixr.domain.playlist

import kotlinx.coroutines.flow.Flow

/** One row of the Playlists tab. [isFavorites]: the built-in Favorites playlist. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val isFavorites: Boolean
)

/** A playlist's page: its tracks, newest first. */
data class Playlist(
    val id: Long,
    val name: String,
    val isFavorites: Boolean,
    val trackIds: List<String>
)

interface PlaylistRepository {
    /** Every playlist with its track count: Favorites first, then in order of creation. */
    val playlists: Flow<List<PlaylistSummary>>

    /** Playlist [id] with its tracks, or null if there is none. */
    fun playlist(id: Long): Flow<Playlist?>

    /** The ids of the tracks in Favorites. */
    val favorites: Flow<Set<String>>

    /** Adds [trackId] to Favorites or takes it out; adding a favorite again changes nothing. */
    suspend fun setFavorite(trackId: String, favorite: Boolean)
}
