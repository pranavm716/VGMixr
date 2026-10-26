package io.github.pranavm716.vgmixr.data.offline

import io.github.pranavm716.vgmixr.data.catalog.RoomCatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.offline.Connectivity
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.offline
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The catalog the app sees: Room's, with tracks that can't play offline dimmed while there is no
 * connection (roadmap step 13), so every list greys them and taps skip them.
 */
class OfflineCatalogRepository @Inject constructor(
    room: RoomCatalogRepository,
    connectivity: Connectivity,
    store: OfflineStore
) : CatalogRepository {
    override val catalog: Flow<Catalog> =
        combine(room.catalog, connectivity.online, store.playableOffline) {
                catalog,
                online,
                onPhone
            ->
            if (online) catalog else catalog.offline(onPhone)
        }.distinctUntilChanged()
}
