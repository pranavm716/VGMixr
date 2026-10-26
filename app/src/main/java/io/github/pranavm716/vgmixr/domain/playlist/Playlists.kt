package io.github.pranavm716.vgmixr.domain.playlist

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** One row of the Playlists tab. [isFavorites]: the built-in Favorites playlist. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val isFavorites: Boolean
)

/**
 * A playlist's page: its tracks, newest first, with when each was added ([addedAt], by track id)
 * and the page's [sort].
 */
data class Playlist(
    val id: Long,
    val name: String,
    val isFavorites: Boolean,
    val trackIds: List<String>,
    val addedAt: Map<String, Instant> = emptyMap(),
    val sort: SortSetting = SortKeys.playlistDefault
)

interface PlaylistRepository {
    /** Every playlist with its track count: Favorites first, then in order of creation. */
    val playlists: Flow<List<PlaylistSummary>>

    /** Playlist [id] with its tracks, or null if there is none. */
    fun playlist(id: Long): Flow<Playlist?>

    /** The ids of the playlists holding [trackId], Favorites included. */
    fun playlistsContaining(trackId: String): Flow<Set<Long>>

    /** The ids of the tracks in any playlist, Favorites included. */
    val tracksInPlaylists: Flow<Set<String>>

    /** The ids of the tracks in Favorites. */
    val favorites: Flow<Set<String>>

    /** Adds [trackId] to Favorites or takes it out; adding a favorite again changes nothing. */
    suspend fun setFavorite(trackId: String, favorite: Boolean)

    /**
     * Adds [trackId] to playlist [playlistId] or takes it out; adding a track it has changes
     * nothing, so it keeps when it was first added.
     */
    suspend fun setInPlaylist(playlistId: Long, trackId: String, inPlaylist: Boolean)

    /** Renames playlist [id] to [name] (trimmed); Favorites can't be renamed. */
    suspend fun renamePlaylist(id: Long, name: String)

    /** Deletes playlist [id] with its entries; Favorites can't be deleted. */
    suspend fun deletePlaylist(id: Long)

    /** Saves playlist [id]'s sort. */
    suspend fun setSort(id: Long, sort: SortSetting)

    /** Makes an empty playlist named [name] (trimmed) and returns its id. */
    suspend fun createPlaylist(name: String): Long
}

/**
 * The playlist's tracks found in [catalog] that can play, in its order: what a tap plays and a
 * swipe queues. Tracks no longer in the catalog and greyed tracks are left out.
 */
fun Playlist.playableTrackIds(catalog: Catalog): List<String> =
    trackIds.filter { id -> catalog.track(id)?.let { it.unplayable == null } == true }
