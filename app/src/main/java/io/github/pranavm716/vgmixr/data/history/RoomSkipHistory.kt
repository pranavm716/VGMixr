package io.github.pranavm716.vgmixr.data.history

import io.github.pranavm716.vgmixr.data.db.SkipEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.domain.history.Skip
import io.github.pranavm716.vgmixr.domain.history.SkipHistory
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSkipHistory @Inject constructor(private val dao: UserDataDao, private val clock: Clock) :
    SkipHistory {
    override suspend fun record(skip: Skip, context: QueueContext?) = dao.insertSkip(
        SkipEntity(
            trackId = skip.trackId,
            skippedAt = clock.instant(),
            positionMs = skip.positionMs,
            context = context?.key
        )
    )
}
