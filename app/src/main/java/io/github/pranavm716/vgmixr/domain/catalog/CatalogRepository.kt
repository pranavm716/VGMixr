package io.github.pranavm716.vgmixr.domain.catalog

import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    /** The current catalog, re-emitted after each catalog change. */
    val catalog: Flow<Catalog>
}
