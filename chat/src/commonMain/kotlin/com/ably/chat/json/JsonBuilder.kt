package com.ably.chat.json

import com.ably.chat.annotations.ChatDsl

/**
 * Builder class for constructing JSON objects in a structured and type-safe way.
 *
 * This class provides methods to add key-value pairs to a JSON object, supporting various types
 * of values such as primitives, nulls, arrays, and nested objects.
 *
 * Methods in this class follow a builder pattern and return the instance of the builder itself
 * to allow for method chaining. When the configuration is complete, the `build` method can be
 * called to create an immutable [JsonObject].
 *
 * Usage of this class is typically done within the context of a DSL, enabled by the `@ChatDsl` annotation.
 */
public class JsonObjectBuilder(initialFields: Map<String, JsonValue> = emptyMap()) {
    private val fields = initialFields.toMutableMap()

    public fun put(key: String, value: Any?): JsonObjectBuilder {
        fields[key] = JsonValue.of(value)
        return this
    }

    public fun putArray(key: String, builder: JsonArrayBuilder.() -> Unit): JsonObjectBuilder {
        fields[key] = jsonArray(builder = builder)
        return this
    }

    public fun putObject(key: String, builder: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
        fields[key] = jsonObject(builder = builder)
        return this
    }

    internal fun build(): JsonObject = JsonObject(fields.toMap())
}

/**
 * Builder class for creating JSON arrays in a structured and type-safe manner.
 *
 * Provides methods to add various types of elements, such as primitive values, nulls,
 * other JSON arrays, and JSON objects, to a JSON array being constructed.
 */
public class JsonArrayBuilder(initialValues: List<JsonValue> = emptyList()) {
    private val values = initialValues.toMutableList()

    public fun add(value: Any?): JsonArrayBuilder {
        values.add(JsonValue.of(value))
        return this
    }

    public fun addArray(builder: JsonArrayBuilder.() -> Unit): JsonArrayBuilder {
        values.add(jsonArray(builder = builder))
        return this
    }

    public fun addObject(builder: JsonObjectBuilder.() -> Unit): JsonArrayBuilder {
        values.add(jsonObject(builder = builder))
        return this
    }

    internal fun build(): JsonArray = JsonArray(values.toList())
}

/**
 * Creates a new [JsonObject] using the provided initial fields and a lambda for adding or modifying fields.
 * This function is part of a DSL for building JSON objects in a structured and type-safe way.
 *
 * @param initialFields The initial fields to populate the JSON object with. Defaults to an empty map.
 * @param builder A lambda that defines additional fields or modifications to construct the JSON object.
 * @return A new [JsonObject] containing the specified fields and modifications.
 */
@ChatDsl
public fun jsonObject(initialFields: Map<String, JsonValue> = emptyMap(), builder: JsonObjectBuilder.() -> Unit): JsonObject =
    JsonObjectBuilder(initialFields).apply(builder).build()

/**
 * Creates a [JsonArray] using the given initial values and builder lambda.
 *
 * This function constructs a `JsonArray` by initializing it with the provided list of
 * `JsonValue` objects (if any) and then applying the given DSL builder to further configure
 * its contents.
 *
 * The builder is executed within the context of a [JsonArrayBuilder], which provides methods
 * to add various types of elements to the array.
 *
 * @param initialValues an optional list of initial [JsonValue] objects to populate the array. Defaults to an empty list.
 * @param builder a DSL builder lambda used to configure the contents of the JSON array.
 * @return a [JsonArray] instance containing the elements defined by the initial values and the builder.
 */
@ChatDsl
public fun jsonArray(initialValues: List<JsonValue> = emptyList(), builder: JsonArrayBuilder.() -> Unit): JsonArray =
    JsonArrayBuilder(initialValues).apply(builder).build()
