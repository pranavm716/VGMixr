package io.github.pranavm716.vgmixr.data.manifest

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// kotlinx-serialization decodes quoted numbers like "1" into numeric fields. The manifest treats
// those as the wrong type (malformed), as the pipeline's strict validator does.

internal object StrictIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("StrictInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int = decodeNumber(decoder, "an integer") {
        it.intOrNull
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

internal object StrictLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("StrictLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long = decodeNumber(decoder, "an integer") {
        it.longOrNull
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

internal object StrictDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("StrictDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double = decodeNumber(decoder, "a number") {
        it.doubleOrNull
    }

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

private fun <T : Any> decodeNumber(
    decoder: Decoder,
    expected: String,
    convert: (JsonPrimitive) -> T?
): T {
    val jsonDecoder =
        decoder as? JsonDecoder ?: throw SerializationException("Only JSON is supported")
    val element = jsonDecoder.decodeJsonElement()
    val primitive = (element as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }
    return primitive?.let(convert)
        ?: throw SerializationException("Expected $expected, got $element")
}
