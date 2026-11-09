package io.github.twonet.selfish.domain.persist

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * A list that drops the elements it can't read instead of failing as a whole,
 * so one corrupt grid item costs only that item (NFR-REL-03, DESIGN §4.7).
 * It writes exactly like a plain list. Use it on a property with
 * `@Serializable(with = LenientListSerializer::class)`; it reads JSON only.
 */
class LenientListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val strict = ListSerializer(element)

    override val descriptor: SerialDescriptor = strict.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) = strict.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val json = decoder as? JsonDecoder ?: throw SerializationException("LenientListSerializer reads JSON only")
        val array = json.decodeJsonElement() as? JsonArray ?: throw SerializationException("Expected a JSON array")
        return array.mapNotNull { item ->
            try {
                json.json.decodeFromJsonElement(element, item)
            } catch (_: Exception) {
                // Wrong shape, unknown type, failed validation: skip this one.
                null
            }
        }
    }
}
