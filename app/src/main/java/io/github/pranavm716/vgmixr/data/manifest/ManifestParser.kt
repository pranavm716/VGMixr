package io.github.pranavm716.vgmixr.data.manifest

import io.github.pranavm716.vgmixr.domain.manifest.ManifestError
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Loop
import io.github.pranavm716.vgmixr.domain.model.Manifest
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.model.Track
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Parses manifest JSON (docs/manifest.md) into a valid [Manifest] or its validation errors. */
class ManifestParser @Inject constructor() {
    // Unknown keys are allowed: adding an optional field doesn't bump schemaVersion.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ManifestParseResult {
        val dto = try {
            val element = json.parseToJsonElement(text)
            // Checked before decoding: another version may have a different shape, and
            // "unsupported" is more useful than "malformed".
            schemaVersion(element)?.let { version ->
                if (version != SUPPORTED_SCHEMA_VERSION) {
                    return invalid(ManifestError.SchemaVersionUnsupported(version))
                }
            }
            json.decodeFromJsonElement(ManifestDto.serializer(), element)
        } catch (e: IllegalArgumentException) {
            // SerializationException is a subclass: bad JSON, a missing field or a wrong type.
            return invalid(ManifestError.Malformed(e.message.orEmpty()))
        }
        val errors = ManifestValidator.validate(dto)
        return if (errors.isEmpty()) {
            ManifestParseResult.Valid(dto.toDomain())
        } else {
            ManifestParseResult.Invalid(errors)
        }
    }

    /** The version if it's a JSON integer; anything else is left for decoding to reject. */
    private fun schemaVersion(element: JsonElement): Int? =
        ((element as? JsonObject)?.get("schemaVersion") as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.intOrNull

    private fun invalid(error: ManifestError) = ManifestParseResult.Invalid(listOf(error))

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

private fun ManifestDto.toDomain() = Manifest(
    generatedAt = Instant.parse(generatedAt),
    games = games.map { it.toDomain() },
    tracks = tracks.map { it.toDomain() }
)

private fun GameDto.toDomain() = Game(
    id = id,
    name = name,
    shortName = shortName,
    color = color,
    generation = generation,
    platform = platform,
    familyId = familyId
)

private fun TrackDto.toDomain() = Track(
    id = id,
    familyId = family,
    games = games,
    title = title,
    order = order,
    sources = sources.map { it.toDomain() },
    replaces = replaces
)

private fun SourceDto.toDomain(): Source = when (this) {
    is LocalSourceDto -> Source.Local(path, loop?.toDomain())
    is StreamSourceDto -> Source.Stream(videoId, startMs, endMs)
    is UnknownSourceDto -> error("Unknown source type $type passed validation")
}

private fun LoopDto.toDomain() = Loop(sampleRate, introEnd, loopEnd)
