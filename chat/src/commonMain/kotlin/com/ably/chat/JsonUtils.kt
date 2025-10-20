package com.ably.chat

import com.ably.chat.json.JsonBoolean
import com.ably.chat.json.JsonNumber
import com.ably.chat.json.JsonObject
import com.ably.chat.json.JsonString
import com.ably.chat.json.JsonValue
import com.ably.chat.json.jsonObject
import io.ably.lib.http.HttpCore
import io.ably.lib.http.HttpUtils

internal fun JsonValue.stringOrNull(): String? = when (this) {
    is JsonString -> this.value
    is JsonNumber -> this.value.toString()
    is JsonBoolean -> this.value.toString()
    else -> null
}

internal fun JsonValue.longOrNull(): Long? = when (this) {
    is JsonString -> safeCastToNumber { this.value.toLong() }
    is JsonNumber -> this.value.toLong()
    else -> null
}

internal fun JsonValue.intOrNull(): Int? = when (this) {
    is JsonString -> safeCastToNumber { this.value.toInt() }
    is JsonNumber -> this.value.toInt()
    else -> null
}

internal fun JsonValue.getOrNull(field: String): JsonValue? = when (this) {
    is JsonObject -> get(field)
    else -> null
}

internal fun JsonValue.jsonObjectOrNull(): JsonObject? = when (this) {
    is JsonObject -> this
    else -> null
}

internal fun JsonValue?.toRequestBody(useBinaryProtocol: Boolean = false): HttpCore.RequestBody? =
    this?.let { HttpUtils.requestBodyFromGson(it.toGson(), useBinaryProtocol) }

internal fun Map<String, String>.toJson() = jsonObject {
    forEach { (key, value) -> put(key, value) }
}

internal fun JsonValue.toMap(): Map<String, String> = when (this) {
    is JsonObject -> buildMap {
        this@toMap.forEach {
            it.value.stringOrNull()?.let { value -> put(it.key, value) }
        }
    }

    else -> emptyMap()
}

internal fun JsonValue.requireJsonObject(): JsonObject {
    return this as? JsonObject
        ?: throw serverError("unable to parse response; expected JsonObject but got primitive instead")
}

internal fun JsonValue.requireString(memberName: String): String {
    val memberElement = requireField(memberName)
    return memberElement.stringOrNull() ?: throw serverError(
        "unable to parse response; required string field \"$memberName\" is not a valid string",
    )
}

internal fun JsonValue.requireLong(memberName: String): Long {
    val memberElement = requireField(memberName)
    return memberElement.longOrNull() ?: throw serverError(
        "unable to parse response; required numeric field \"$memberName\" is not a valid number",
    )
}

internal fun JsonValue.requireField(memberName: String): JsonValue = requireJsonObject()[memberName]
    ?: throw serverError(
        "unable to parse response; required field \"$memberName\" is missing",
    )

private inline fun <T : Number> safeCastToNumber(block: () -> T): T? {
    return try {
        block()
    } catch (_: NumberFormatException) {
        null
    }
}
