package io.github.twonet.selfish.domain.persist

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A saved document that carries a schema version and is migrated forward on
 * upgrade (AR-07). Reading never throws, and keeps whatever is still valid
 * (NFR-REL-03, DESIGN §4.7):
 *
 * 1. The text is parsed to a [JsonObject], and [migrations] run from the saved
 *    version up to [currentVersion].
 * 2. It is decoded leniently: unknown keys are ignored, and invalid enum
 *    values and missing fields take their defaults. Lists marked with
 *    [LenientListSerializer] drop just their invalid elements.
 * 3. If decoding still fails, the saved fields are laid over [default] one
 *    at a time, and each one that doesn't decode keeps its default.
 * 4. [repair] restores invariants the types can't express.
 *
 * Text that can't be parsed, isn't an object, or was written by a newer app
 * version has nothing usable: [decodeOrNull] returns `null` and [decode]
 * returns [default].
 *
 * @param migrations `migrations[i]` turns a version `i + 1` object into a
 *   version `i + 2` object. Each must be pure.
 */
class VersionedDocument<T : Any>(
    private val serializer: KSerializer<T>,
    val default: T,
    private val migrations: List<(JsonObject) -> JsonObject> = emptyList(),
    private val repair: (T) -> T = { it },
) {
    /** The version this app writes: one more than the number of migrations. */
    val currentVersion: Int = migrations.size + 1

    /** The saved value, or [default] when nothing usable was saved. */
    fun decode(text: String?): T = decodeOrNull(text) ?: default

    /** The saved value, or `null` when nothing usable was saved, as when nothing was saved at all. */
    fun decodeOrNull(text: String?): T? {
        if (text == null) return null
        val saved = try {
            var obj = json.parseToJsonElement(text).jsonObject
            val version = obj[VERSION_KEY]?.jsonPrimitive?.int ?: 1
            if (version !in 1..currentVersion) return null
            for (migrate in migrations.subList(version - 1, migrations.size)) {
                obj = migrate(obj)
            }
            JsonObject(obj - VERSION_KEY)
        } catch (_: Exception) {
            // Malformed JSON, not an object, a non-numeric version, or a
            // migration tripping over bad data.
            return null
        }
        val value = decodeObject(saved) ?: decodeFieldByField(saved)
        return try {
            repair(value)
        } catch (_: Exception) {
            // A launcher that crashes on its own saved data can't be fixed by
            // the user; starting over is better.
            null
        }
    }

    fun encode(value: T): String {
        val body = json.encodeToJsonElement(serializer, value).jsonObject
        return json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf(VERSION_KEY to JsonPrimitive(currentVersion)) + body),
        )
    }

    /** Lays the saved fields over [default] one at a time, keeping each one that still decodes. */
    private fun decodeFieldByField(saved: JsonObject): T {
        var accepted = json.encodeToJsonElement(serializer, default).jsonObject
        for ((key, value) in saved) {
            val candidate = JsonObject(accepted + (key to value))
            if (decodeObject(candidate) != null) accepted = candidate
        }
        return decodeObject(accepted) ?: default
    }

    private fun decodeObject(obj: JsonObject): T? = try {
        json.decodeFromJsonElement(serializer, obj)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val VERSION_KEY = "schemaVersion"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}
