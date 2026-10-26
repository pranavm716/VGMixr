package io.github.pranavm716.vgmixr.data.offline

import io.github.pranavm716.vgmixr.data.db.OfflineDao
import io.github.pranavm716.vgmixr.data.db.OfflineVideoEntity
import io.github.pranavm716.vgmixr.domain.offline.OfflineEntry
import io.github.pranavm716.vgmixr.domain.offline.OfflineKind
import io.github.pranavm716.vgmixr.domain.offline.OfflinePlan
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.OfflineUsage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomOfflineStore @Inject constructor(private val dao: OfflineDao) : OfflineStore {
    override val entries: Flow<Map<String, OfflineEntry>> =
        combine(dao.observeVideos(), dao.observeTracks()) { videos, tracks ->
            val byVideo = tracks.groupBy({ it.videoId }, { it.trackId })
            videos.associate {
                it.videoId to OfflineEntry(
                    it.videoId,
                    OfflineKind.valueOf(it.kind),
                    it.complete,
                    byVideo[it.videoId].orEmpty().toSet()
                )
            }
        }

    override val playableOffline: Flow<Set<String>> = dao.observePlayableTracks().map { it.toSet() }

    override val usage: Flow<OfflineUsage> = dao.observeVideos().map { videos ->
        val done = videos.filter { it.complete }
        OfflineUsage(
            downloadedBytes = done.filter {
                it.kind == OfflineKind.DOWNLOADED.name
            }.sumOf { it.bytes },
            cachedBytes = done.filter { it.kind == OfflineKind.CACHED.name }.sumOf { it.bytes }
        )
    }

    override suspend fun apply(plan: OfflinePlan) {
        if (plan.remove.isNotEmpty()) dao.deleteVideos(plan.remove)
        plan.upsert.forEach {
            dao.put(
                OfflineVideoEntity(it.videoId, it.kind.name, complete = false, bytes = 0),
                it.trackIds
            )
        }
    }

    override suspend fun setComplete(videoId: String, bytes: Long) = dao.setComplete(videoId, bytes)
}
