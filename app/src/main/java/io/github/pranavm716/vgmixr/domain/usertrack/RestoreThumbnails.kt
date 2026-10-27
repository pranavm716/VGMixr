package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.offline.Connectivity
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/**
 * Brings back user tracks' missing thumbnails: one whose download failed when it was added (the
 * connection dropped), or whose file is gone (`backup push` copies only the database). Each one
 * is downloaded again from its video id whenever the phone is online.
 */
class RestoreThumbnails @Inject constructor(
    private val catalog: CatalogRepository,
    private val thumbnails: ThumbnailStore,
    private val connectivity: Connectivity
) {
    /** Runs until cancelled, fetching again each time the connection comes back. */
    suspend operator fun invoke() {
        connectivity.online.collectLatest { online ->
            if (!online) return@collectLatest
            catalog.catalog.first().userTracks
                .filter { it.thumbnail == null }
                .mapNotNull { UserTrackId.videoId(it.id) }
                .forEach { thumbnails.download(it) }
        }
    }
}
