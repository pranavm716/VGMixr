package io.github.pranavm716.vgmixr.data.playlist

import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.PlaylistEntryEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomPlaylistRepository @Inject constructor(
    private val dao: UserDataDao,
    private val clock: Clock
) : PlaylistRepository {
    override val playlists: Flow<List<PlaylistSummary>> =
        dao.observePlaylists().map { rows ->
            rows.map {
                PlaylistSummary(it.id, it.name, it.trackCount, it.id == FAVORITES_PLAYLIST_ID)
            }
        }

    override fun playlist(id: Long): Flow<Playlist?> = combine(
        dao.observePlaylist(id),
        dao.observePlaylistTrackIdsNewestFirst(id)
    ) { playlist, trackIds ->
        playlist?.let { Playlist(it.id, it.name, it.id == FAVORITES_PLAYLIST_ID, trackIds) }
    }

    override val favorites: Flow<Set<String>> =
        dao.observePlaylistTrackIds(FAVORITES_PLAYLIST_ID).map { it.toSet() }

    override suspend fun setFavorite(trackId: String, favorite: Boolean) {
        if (favorite) {
            dao.insertPlaylistEntryIfAbsent(
                PlaylistEntryEntity(FAVORITES_PLAYLIST_ID, trackId, clock.instant())
            )
        } else {
            dao.removeFromPlaylist(FAVORITES_PLAYLIST_ID, trackId)
        }
    }
}
