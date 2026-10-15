package io.github.pranavm716.vgmixr.domain.manifest

import io.github.pranavm716.vgmixr.domain.model.Manifest

sealed interface ManifestParseResult {
    data class Valid(val manifest: Manifest) : ManifestParseResult

    /** Never empty: a manifest with no errors is [Valid]. */
    data class Invalid(val errors: List<ManifestError>) : ManifestParseResult
}
