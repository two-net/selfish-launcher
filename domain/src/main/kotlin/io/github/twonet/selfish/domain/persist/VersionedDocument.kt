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
 * upgrade (AR-07). Unreadable data falls back to [default] (NFR-REL-03).
 *
 * Reading parses the text to a [JsonObject], runs [migrations] from the saved
 * version up to [currentVersion], then decodes leniently: unknown keys are
 * ignored, invalid enum values and missing fields take their defaults.
 * Text that can't be parsed, or that was written by a newer app version,
 * yields [default] (DESIGN §4.7).
 *
 * @param migrations `migrations[i]` turns a version `i + 1` object into a
 *   version `i + 2` object. Each must be pure.
 */
class VersionedDocument<T>(
    private val serializer: KSerializer<T>,
    val default: T,
    private val migrations: List<(JsonObject) -> JsonObject> = emptyList(),
) {
    /** The version this app writes: one more than the number of migrations. */
    val currentVersion: Int = migrations.size + 1

    fun decode(text: String?): T {
        if (text == null) return default
        return try {
            var obj = json.parseToJsonElement(text).jsonObject
            val version = obj[VERSION_KEY]?.jsonPrimitive?.int ?: 1
            if (version < 1 || version > currentVersion) return default
            for (migrate in migrations.subList(version - 1, migrations.size)) {
                obj = migrate(obj)
            }
            json.decodeFromJsonElement(serializer, JsonObject(obj - VERSION_KEY))
        } catch (_: Exception) {
            // Malformed JSON, wrong shapes, a non-numeric version or a
            // migration tripping over bad data: never crash, use defaults.
            default
        }
    }

    fun encode(value: T): String {
        val body = json.encodeToJsonElement(serializer, value).jsonObject
        return json.encodeToString(
            JsonObject.serializer(),
            JsonObject(mapOf(VERSION_KEY to JsonPrimitive(currentVersion)) + body),
        )
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
