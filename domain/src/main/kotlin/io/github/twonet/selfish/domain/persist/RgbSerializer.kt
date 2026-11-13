package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.model.Rgb
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * An optional [Rgb] as a `#RRGGBB` string, which is part of the saved format
 * (DESIGN §4.7). A value that isn't one reads as `null` instead of failing,
 * so a bad color costs only the color: in a list of wallpaper entries a
 * failure would drop the whole entry, and with it the cached image
 * (NFR-REL-03). Use it on a property with
 * `@Serializable(with = OptionalRgbSerializer::class)`; it reads JSON only.
 */
object OptionalRgbSerializer : KSerializer<Rgb?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.twonet.selfish.Rgb", PrimitiveKind.STRING).nullable

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Rgb?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Rgb? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("OptionalRgbSerializer reads JSON only")
        val text = (json.decodeJsonElement() as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return parse(text)
    }

    /** `#RRGGBB` as an [Rgb], or `null` if [text] isn't exactly that. */
    fun parse(text: String): Rgb? {
        if (text.length != 7 || text[0] != '#') return null
        val hex = text.substring(1)
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return Rgb(hex.toInt(16))
    }
}
