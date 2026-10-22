package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.data.db.BrokenStreamDao
import io.github.pranavm716.vgmixr.data.db.BrokenStreamEntity
import io.github.pranavm716.vgmixr.data.db.ImportStateDao
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomBrokenSources @Inject constructor(
    private val dao: BrokenStreamDao,
    private val importStateDao: ImportStateDao,
    private val clock: Clock
) : BrokenSources {
    override val marks: Flow<Map<String, BrokenMark>> = dao.observeAll().map { rows ->
        rows.mapNotNull { it.toMark() }.associateBy { it.videoId }
    }

    override suspend fun mark(
        videoId: String,
        trackId: String,
        reason: BrokenReason,
        detail: String?
    ): Boolean = dao.insert(
        BrokenStreamEntity(
            videoId = videoId,
            trackId = trackId,
            reason = reason.key,
            detail = detail,
            markedAt = clock.instant(),
            catalogGeneratedAt = importStateDao.get()?.generatedAt
        )
    )

    override suspend fun clearTrack(trackId: String) = dao.deleteForTrack(trackId)
}

/** A row whose reason this version doesn't know is left out. */
private fun BrokenStreamEntity.toMark(): BrokenMark? = BrokenReason.fromKey(reason)?.let {
    BrokenMark(videoId, trackId, it, detail, markedAt, catalogGeneratedAt)
}
