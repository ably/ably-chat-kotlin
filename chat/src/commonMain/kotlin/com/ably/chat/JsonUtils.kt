package com.ably.chat

import com.ably.chat.json.JsonBoolean
import com.ably.chat.json.JsonNumber
import com.ably.chat.json.JsonObject
import com.ably.chat.json.JsonString
import com.ably.chat.json.JsonValue
import com.ably.chat.json.jsonObject
import io.ably.lib.http.HttpCore
import io.ably.lib.http.HttpUtils
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo

internal fun JsonValue.tryAsString(): String? = when (this) {
    is JsonString -> this.value
    is JsonNumber -> this.value.toString()
    is JsonBoolean -> this.value.toString()
    else -> null
}

internal fun JsonValue.tryAsLong(): Long? = when (this) {
    is JsonString -> safeCastToNumber { this.value.toLong() }
    is JsonNumber -> this.value.toLong()
    else -> null
}

internal fun JsonValue.tryAsInt(): Int? = when (this) {
    is JsonString -> safeCastToNumber { this.value.toInt() }
    is JsonNumber -> this.value.toInt()
    else -> null
}

internal fun JsonValue.tryAsJsonObject(): JsonObject? = when (this) {
    is JsonObject -> this
    else -> null
}

internal fun JsonValue?.toRequestBody(useBinaryProtocol: Boolean = false): HttpCore.RequestBody =
    HttpUtils.requestBodyFromGson(this?.toGson(), useBinaryProtocol)

internal fun Map<String, String>.toJson() = jsonObject {
    forEach { (key, value) -> put(key, value) }
}

internal fun JsonValue.toMap(): Map<String, String> = when (this) {
    is JsonObject -> buildMap {
        this@toMap.forEach {
            it.value.tryAsString()?.let { value -> put(it.key, value) }
        }
    }

    else -> emptyMap()
}

internal fun JsonValue.requireJsonObject(): JsonObject {
    return this as? JsonObject
        ?: throw AblyException.fromErrorInfo(
            ErrorInfo("Response value expected to be JsonObject, got primitive instead", HttpStatusCode.InternalServerError),
        )
}

internal fun JsonValue.requireString(memberName: String): String {
    val memberElement = requireField(memberName)
    return memberElement.tryAsString() ?: throw serverError(
        "Required string field \"$memberName\" is not a valid string",
    )
}

internal fun JsonValue.requireLong(memberName: String): Long {
    val memberElement = requireField(memberName)
    return memberElement.tryAsLong() ?: throw serverError(
        "Required numeric field \"$memberName\" is not a valid number",
    )
}

internal fun JsonValue.requireInt(memberName: String): Int {
    val memberElement = requireField(memberName)
    return memberElement.tryAsInt() ?: throw serverError(
        "Required numeric field \"$memberName\" is not a valid number",
    )
}

internal fun JsonValue.requireField(memberName: String): JsonValue = requireJsonObject()[memberName]
    ?: throw serverError(
        "Required field \"$memberName\" is missing",
    )

private inline fun <T : Number> safeCastToNumber(block: () -> T): T? {
    return try {
        block()
    } catch (_: NumberFormatException) {
        null
    }
}
