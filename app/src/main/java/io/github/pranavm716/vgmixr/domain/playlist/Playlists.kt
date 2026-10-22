package io.github.pranavm716.vgmixr.domain.playlist

import kotlinx.coroutines.flow.Flow

/** One row of the Playlists tab. [isFavorites]: the built-in Favorites playlist. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val isFavorites: Boolean
)

interface PlaylistRepository {
    /** Every playlist with its track count: Favorites first, then in order of creation. */
    val playlists: Flow<List<PlaylistSummary>>
}
