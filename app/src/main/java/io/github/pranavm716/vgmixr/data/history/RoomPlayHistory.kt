package io.github.pranavm716.vgmixr.data.history

import io.github.pranavm716.vgmixr.data.db.PlayHistoryEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.domain.history.Play
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomPlayHistory @Inject constructor(private val dao: UserDataDao, private val clock: Clock) :
    PlayHistory {
    override val plays: Flow<List<Play>> =
        dao.observeHistory().map { rows -> rows.map { Play(it.trackId, it.playedAt) } }

    override suspend fun record(trackId: String) =
        dao.insertHistory(PlayHistoryEntity(trackId = trackId, playedAt = clock.instant()))
}
