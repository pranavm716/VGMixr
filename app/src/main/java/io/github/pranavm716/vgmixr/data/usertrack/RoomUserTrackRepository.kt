package io.github.pranavm716.vgmixr.data.usertrack

import io.github.pranavm716.vgmixr.data.db.UserTrackDao
import io.github.pranavm716.vgmixr.data.db.UserTrackEntity
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomUserTrackRepository @Inject constructor(private val dao: UserTrackDao) :
    UserTrackRepository {
    override val userTracks: Flow<List<UserTrack>> =
        dao.observeNewestFirst().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(track: UserTrack): Boolean = dao.insert(track.toEntity()) != -1L

    override suspend fun setRemixOf(id: String, remixOf: String?) = dao.setRemixOf(id, remixOf)

    override suspend fun delete(id: String) = dao.delete(id)
}

internal fun UserTrackEntity.toDomain() = UserTrack(
    videoId = videoId,
    startMs = startMs,
    endMs = endMs,
    title = title,
    channel = channel,
    durationMs = durationMs,
    addedAt = addedAt,
    remixOf = remixOf
)

private fun UserTrack.toEntity() = UserTrackEntity(
    id = id,
    videoId = videoId,
    startMs = startMs,
    endMs = endMs,
    title = title,
    channel = channel,
    durationMs = durationMs,
    addedAt = addedAt,
    remixOf = remixOf
)
