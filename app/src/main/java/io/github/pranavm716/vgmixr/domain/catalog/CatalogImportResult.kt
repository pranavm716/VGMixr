package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.domain.manifest.ManifestError
import java.time.Instant

/** The outcome of importing the bundled manifest into the catalog. */
sealed interface CatalogImportResult {
    data class Imported(val generatedAt: Instant) : CatalogImportResult

    /** The manifest's `generatedAt` matches the last successful import, so nothing was written. */
    data object Unchanged : CatalogImportResult

    /** Nothing was written and the previous catalog is intact. [errors] is never empty. */
    data class Failed(val errors: List<ImportError>) : CatalogImportResult
}

sealed interface ImportError {
    /** No bundled manifest to read. */
    data object ManifestMissing : ImportError

    data class Invalid(val error: ManifestError) : ImportError

    /** Writing threw, and the transaction rolled back. */
    data class WriteFailed(val message: String) : ImportError
}
