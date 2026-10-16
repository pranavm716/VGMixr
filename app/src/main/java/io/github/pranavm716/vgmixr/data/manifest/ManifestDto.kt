@file:UseSerializers(
    StrictIntSerializer::class,
    StrictLongSerializer::class,
    StrictDoubleSerializer::class
)

package io.github.pranavm716.vgmixr.data.manifest

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The manifest exactly as written in JSON (docs/manifest.md). A field with no default is
// required: missing, it fails decoding as `malformed`. Optional fields default to null, or to
// empty where omitted and empty mean the same thing.

@Serializable
internal data class ManifestDto(
    val schemaVersion: Int,
    val generatedAt: String,
    val games: List<GameDto>,
    val tracks: List<TrackDto>
)

@Serializable
internal data class GameDto(
    val id: String,
    val name: String,
    val shortName: String,
    val color: String,
    val generation: Int? = null,
    val platform: String,
    val family: String? = null
)

@Serializable
internal data class TrackDto(
    val id: String,
    val family: String,
    // Null (omitted) means all of the family's games; empty is a validation error.
    val games: List<String>? = null,
    val title: String,
    val category: String,
    val order: Int,
    val sources: List<SourceDto>,
    val replaces: List<String> = emptyList()
)

@Serializable
internal data class LoopDto(val sampleRate: Int, val introEnd: Int, val loopEnd: Int)

@Serializable(with = SourceDtoSerializer::class)
internal sealed interface SourceDto

@Serializable
internal data class LocalSourceDto(val path: String, val loop: LoopDto? = null) : SourceDto

@Serializable
internal data class StreamSourceDto(
    val videoId: String,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val confidence: Double? = null,
    // Not allowed on streams. Declared so the validator sees it instead of the decoder dropping it.
    val loop: LoopDto? = null
) : SourceDto

/** A source whose `type` the app doesn't know: a validation error, not a decoding one. */
@Serializable
internal data class UnknownSourceDto(val type: String) : SourceDto

/**
 * Picks a source's class by its `type`. The default sealed-class polymorphism throws on an
 * unknown `type`, which would report `malformed` instead of `source-type-unknown`.
 */
internal object SourceDtoSerializer : JsonContentPolymorphicSerializer<SourceDto>(
    SourceDto::class
) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SourceDto> {
        // Safe casts: jsonObject and jsonPrimitive throw IllegalArgumentException, which the
        // parser wouldn't report as malformed.
        val type = ((element as? JsonObject)?.get("type") as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?: throw SerializationException("A source must be an object with a string type")
        return when (type) {
            "local" -> LocalSourceDto.serializer()
            "stream" -> StreamSourceDto.serializer()
            else -> UnknownSourceDto.serializer()
        }
    }
}
