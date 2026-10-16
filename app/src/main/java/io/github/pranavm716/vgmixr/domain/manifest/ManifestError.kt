package io.github.pranavm716.vgmixr.domain.manifest

/**
 * A manifest validation error. [code] is one of the error codes in `docs/fixtures/README.md`,
 * shared with the pipeline's validator. Source errors carry the source's index in its track.
 */
sealed class ManifestError(val code: String) {
    /** Invalid JSON, a required field missing, or a field of the wrong type. */
    data class Malformed(val message: String) : ManifestError("malformed")

    data class SchemaVersionUnsupported(val version: Int) :
        ManifestError("schema-version-unsupported")

    data class GeneratedAtInvalid(val value: String) : ManifestError("generated-at-invalid")

    data class GameIdDuplicate(val gameId: String) : ManifestError("game-id-duplicate")

    /** [value] is the game's id or its `family`, whichever is not a slug. */
    data class GameSlugInvalid(val gameId: String, val value: String) :
        ManifestError("game-slug-invalid")

    /** [field] is `name` or `platform`. */
    data class GameFieldEmpty(val gameId: String, val field: String) :
        ManifestError("game-field-empty")

    data class GameGenerationInvalid(val gameId: String, val generation: Int) :
        ManifestError("game-generation-invalid")

    data class GameShortNameInvalid(val gameId: String, val shortName: String) :
        ManifestError("game-short-name-invalid")

    data class GameColorInvalid(val gameId: String, val color: String) :
        ManifestError("game-color-invalid")

    data class FamilyGenerationMismatch(val familyId: String) :
        ManifestError("family-generation-mismatch")

    data class FamilyIdCollision(val familyId: String) : ManifestError("family-id-collision")

    data class TrackIdDuplicate(val trackId: String) : ManifestError("track-id-duplicate")

    data class TrackIdInvalid(val trackId: String) : ManifestError("track-id-invalid")

    data class TrackTitleEmpty(val trackId: String) : ManifestError("track-title-empty")

    data class TrackOrderInvalid(val trackId: String, val order: Int) :
        ManifestError("track-order-invalid")

    data class TrackFamilyUnknown(val trackId: String, val familyId: String) :
        ManifestError("track-family-unknown")

    data class TrackGamesInvalid(val trackId: String) : ManifestError("track-games-invalid")

    data class TrackSourcesEmpty(val trackId: String) : ManifestError("track-sources-empty")

    data class SourceIdentityDuplicate(val trackId: String, val sourceIndex: Int) :
        ManifestError("source-identity-duplicate")

    data class TrackReplacesInvalid(val trackId: String, val replacedId: String) :
        ManifestError("track-replaces-invalid")

    data class SourceTypeUnknown(val trackId: String, val sourceIndex: Int, val type: String) :
        ManifestError("source-type-unknown")

    data class LocalPathInvalid(val trackId: String, val sourceIndex: Int, val path: String) :
        ManifestError("local-path-invalid")

    /** `videoId` malformed, `startMs` < 0, or `endMs <= startMs`. */
    data class StreamInvalid(val trackId: String, val sourceIndex: Int) :
        ManifestError("stream-invalid")

    data class StreamConfidenceInvalid(
        val trackId: String,
        val sourceIndex: Int,
        val confidence: Double
    ) : ManifestError("stream-confidence-invalid")

    data class StreamLoopPresent(val trackId: String, val sourceIndex: Int) :
        ManifestError("stream-loop-present")

    data class LoopInvalid(val trackId: String, val sourceIndex: Int) :
        ManifestError("loop-invalid")
}
