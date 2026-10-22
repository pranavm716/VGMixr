package io.github.pranavm716.vgmixr.data.playlist

import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomPlaylistRepository @Inject constructor(dao: UserDataDao) : PlaylistRepository {
    override val playlists: Flow<List<PlaylistSummary>> =
        dao.observePlaylists().map { rows ->
            rows.map {
                PlaylistSummary(it.id, it.name, it.trackCount, it.id == FAVORITES_PLAYLIST_ID)
            }
        }
}
