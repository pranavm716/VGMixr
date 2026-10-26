package io.github.pranavm716.vgmixr.data.playlist

import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.PlaylistEntity
import io.github.pranavm716.vgmixr.data.db.PlaylistEntryEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.sortSettingOf
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        dao.observePlaylistEntriesNewestFirst(id)
    ) { playlist, entries ->
        playlist?.let {
            Playlist(
                id = it.id,
                name = it.name,
                isFavorites = it.id == FAVORITES_PLAYLIST_ID,
                trackIds = entries.map { entry -> entry.trackId },
                addedAt = entries.associate { entry -> entry.trackId to entry.addedAt },
                sort = sortSettingOf(
                    it.sortKey,
                    it.sortDirection,
                    SortKeys.playlist,
                    SortKeys.playlistDefault
                )
            )
        }
    }

    override suspend fun setSort(id: Long, sort: SortSetting) =
        dao.setPlaylistSort(id, sort.key.name, sort.direction.name)

    override fun playlistsContaining(trackId: String): Flow<Set<Long>> =
        // Room re-emits on any entry change; only a change for this track gets through.
        dao.observePlaylistIdsContaining(trackId).map { it.toSet() }.distinctUntilChanged()

    override val tracksInPlaylists: Flow<Set<String>> =
        // Room re-emits on any entry change; only a change to the set gets through.
        dao.observeTrackIdsInPlaylists().map { it.toSet() }.distinctUntilChanged()

    override val favorites: Flow<Set<String>> =
        dao.observePlaylistTrackIds(FAVORITES_PLAYLIST_ID).map { it.toSet() }

    override suspend fun renamePlaylist(id: Long, name: String) =
        dao.renamePlaylist(id, name.trim())

    override suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    override suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = clock.instant()))

    override suspend fun setFavorite(trackId: String, favorite: Boolean) =
        setInPlaylist(FAVORITES_PLAYLIST_ID, trackId, favorite)

    override suspend fun setInPlaylist(playlistId: Long, trackId: String, inPlaylist: Boolean) {
        if (inPlaylist) {
            dao.insertPlaylistEntryIfAbsent(
                PlaylistEntryEntity(playlistId, trackId, clock.instant())
            )
        } else {
            dao.removeFromPlaylist(playlistId, trackId)
        }
    }
}
