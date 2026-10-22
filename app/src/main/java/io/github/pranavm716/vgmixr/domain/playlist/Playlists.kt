package io.github.pranavm716.vgmixr.domain.playlist

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
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

    /** Makes an empty playlist named [name] (trimmed) and returns its id. */
    suspend fun createPlaylist(name: String): Long
}

/**
 * The playlist's tracks found in [catalog] that can play, in its order: what a tap plays and a
 * swipe queues. Tracks no longer in the catalog and greyed tracks are left out.
 */
fun Playlist.playableTrackIds(catalog: Catalog): List<String> {
    val tracks = catalog.tracks.associateBy { it.id }
    return trackIds.filter { id -> tracks[id]?.let { it.unplayable == null } == true }
}
