package io.github.pranavm716.vgmixr.domain.catalog

import kotlinx.coroutines.flow.StateFlow

/** Imports the bundled manifest on app start. */
interface CatalogSync {
    /** The latest import result, or null until the first import finishes. */
    val status: StateFlow<CatalogImportResult?>
}
