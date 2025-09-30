package com.ably.chat

import com.ably.chat.json.JsonArray
import com.ably.chat.json.JsonBoolean
import com.ably.chat.json.JsonNull
import com.ably.chat.json.JsonNumber
import com.ably.chat.json.JsonObject
import com.ably.chat.json.JsonString
import com.ably.chat.json.JsonValue
import com.ably.chat.json.jsonArray
import com.ably.chat.json.jsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import kotlin.collections.component1
import kotlin.collections.component2

internal fun JsonValue.toGson(): JsonElement = when (this) {
    is JsonNull -> com.google.gson.JsonNull.INSTANCE
    is JsonString -> JsonPrimitive(this.value)
    is JsonNumber -> JsonPrimitive(this.value)
    is JsonBoolean -> JsonPrimitive(this.value)
    is JsonArray -> {
        val jsonArray = com.google.gson.JsonArray()
        forEach { jsonArray.add(it.toGson()) }
        jsonArray
    }
    is JsonObject -> {
        val jsonObject = com.google.gson.JsonObject()
        forEach { (key, value) -> jsonObject.add(key, value.toGson()) }
        jsonObject
    }
}

private fun JsonElement.toJsonValue(): JsonValue = when {
    isJsonNull -> JsonNull
    isJsonObject -> jsonObject {
        asJsonObject.entrySet().forEach { (key, value) -> put(key, value.toJsonValue()) }
    }

    isJsonArray -> jsonArray {
        asJsonArray.forEach { add(it.toJsonValue()) }
    }

    isJsonPrimitive -> when {
        asJsonPrimitive.isBoolean -> JsonBoolean(asBoolean)
        asJsonPrimitive.isNumber -> JsonNumber(asNumber)
        asJsonPrimitive.isString -> JsonString(asString)
        else -> throw ClassCastException("Unknown type of json primitive: ${this.javaClass}")
    }

    else -> throw ClassCastException("Unknown type of json element: ${this.javaClass}")
}

internal fun Any?.tryAsJsonValue(): JsonValue? = when (this) {
    is JsonElement -> toJsonValue()
    else -> null
}
