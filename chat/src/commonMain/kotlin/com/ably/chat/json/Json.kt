package com.ably.chat.json

/**
 * JSON representation
 */
public sealed interface JsonValue {

    public companion object {

        /**
         * Create JsonValue from common Kotlin types
         */
        internal fun of(value: Any?): JsonValue = when (value) {
            null -> JsonNull
            is JsonValue -> value
            is Boolean -> JsonBoolean(value)
            is String -> JsonString(value)
            is Number -> JsonNumber(value)
            is List<*> -> JsonArray(value.map { of(it) })
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { of(it.value) })
            else -> JsonString(value.toString())
        }
    }
}

/**
 * Represents a JSON null value
 */
public data object JsonNull : JsonValue {
    override fun toString(): String = "null"
}

/**
 * Represents a JSON boolean value
 */
public class JsonBoolean(public val value: Boolean) : JsonValue {
    override fun toString(): String = value.toString()
    override fun equals(other: Any?): Boolean = other is JsonBoolean && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

/**
 * Represents a JSON number value
 */
public class JsonNumber(public val value: Number) : JsonValue {
    override fun toString(): String = value.toString()
    override fun equals(other: Any?): Boolean = other is JsonNumber && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

/**
 * Represents a JSON string value
 */
public class JsonString(public val value: String) : JsonValue {
    override fun toString(): String = "\"$value\""
    override fun equals(other: Any?): Boolean = other is JsonString && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

/**
 * Represents a JSON array
 */
public class JsonArray(private val values: List<JsonValue> = listOf()) : JsonValue, List<JsonValue> by values {
    override fun toString(): String = "[${values.joinToString(", ")}]"
    override fun equals(other: Any?): Boolean = other is JsonArray && values == other.values
    override fun hashCode(): Int = values.hashCode()
}

/**
 * Represents a JSON object
 */
public class JsonObject(private val fields: Map<String, JsonValue> = mapOf()) : JsonValue, Map<String, JsonValue> by fields {
    override fun toString(): String = "{${fields.entries.joinToString(", ") { "\"${it.key}\": ${it.value}" }}}"
    override fun equals(other: Any?): Boolean = other is JsonObject && fields == other.fields
    override fun hashCode(): Int = fields.hashCode()
}
