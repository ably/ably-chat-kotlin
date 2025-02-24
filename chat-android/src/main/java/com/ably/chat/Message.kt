package com.ably.chat

import com.google.gson.JsonObject
import io.ably.lib.types.Message
import io.ably.lib.types.MessageAction

/**
 * [Headers type for chat messages.
 */
typealias MessageHeaders = Headers

/**
 * [Metadata] type for chat messages.
 */
typealias MessageMetadata = Metadata

/**
 * Represents a single message in a chat room.
 */
data class Message(
    /**
     * The unique identifier of the message.
     * Spec: CHA-M2d
     */
    val serial: String,

    /**
     * The clientId of the user who created the message.
     */
    val clientId: String,

    /**
     * The roomId of the chat room to which the message belongs.
     */
    val roomId: String,

    /**
     * The text of the message.
     */
    val text: String,

    /**
     * The timestamp at which the message was created.
     */
    val createdAt: Long,

    /**
     * The metadata of a chat message. Allows for attaching extra info to a message,
     * which can be used for various features such as animations, effects, or simply
     * to link it to other resources such as images, relative points in time, etc.
     *
     * Metadata is part of the Ably Pub/sub message content and is not read by Ably.
     *
     * This value is always set. If there is no metadata, this is an empty object.
     *
     * Do not use metadata for authoritative information. There is no server-side
     * validation. When reading the metadata treat it like user input.
     */
    val metadata: MessageMetadata?,

    /**
     * The headers of a chat message. Headers enable attaching extra info to a message,
     * which can be used for various features such as linking to a relative point in
     * time of a livestream video or flagging this message as important or pinned.
     *
     * Headers are part of the Ably realtime message extras.headers and they can be used
     * for Filtered Subscriptions and similar.
     *
     * This value is always set. If there are no headers, this is an empty object.
     *
     * Do not use the headers for authoritative information. There is no server-side
     * validation. When reading the headers treat them like user input.
     */
    val headers: MessageHeaders?,

    /**
     * The latest action of the message. This can be used to determine if the message was created, updated, or deleted.
     * Spec: CHA-M10
     */
    val action: MessageAction,

    /**
     * A unique identifier for the latest version of this message.
     * Spec: CHA-M10a
     */
    val version: String,

    /**
     * The timestamp at which this version was updated, deleted, or created.
     */
    val timestamp: Long,

    /**
     * The details of the operation that modified the message. This is only set for update and delete actions. It contains
     * information about the operation: the clientId of the user who performed the operation, a description, and metadata.
     */
    val operation: Message.Operation? = null,
)

fun com.ably.chat.Message.copy(text: String, metadata: MessageMetadata? = null, headers: MessageHeaders? = null): MessageCopy {
    return MessageCopy(
        message = this,
        text = text,
        metadata = metadata,
        headers = headers,
    )
}

data class MessageCopy(
    val message: com.ably.chat.Message,
    val text: String,
    val metadata: MessageMetadata? = null,
    val headers: MessageHeaders? = null,
) {
    val serial: String = message.serial
    val clientId: String = message.clientId
    val roomId: String = message.roomId
    val createdAt: Long = message.createdAt
    val action: MessageAction = message.action
    val version: String = message.version
    val timestamp: Long = message.timestamp
    val operation: Message.Operation? = message.operation
}

fun buildMessageOperation(jsonObject: JsonObject?): Message.Operation? {
    if (jsonObject == null) {
        return null
    }
    val operation = Message.Operation()
    if (jsonObject.has(MessageOperationProperty.CLIENT_ID)) {
        operation.clientId = jsonObject.get(MessageOperationProperty.CLIENT_ID).asString
    }
    if (jsonObject.has(MessageOperationProperty.DESCRIPTION)) {
        operation.description = jsonObject.get(MessageOperationProperty.DESCRIPTION).asString
    }
    if (jsonObject.has(MessageOperationProperty.METADATA)) {
        val metadataObject = jsonObject.getAsJsonObject(MessageOperationProperty.METADATA)
        operation.metadata = mutableMapOf()
        for ((key, value) in metadataObject.entrySet()) {
            operation.metadata[key] = value.asString
        }
    }
    return operation
}

fun buildMessageOperation(clientId: String, description: String?, metadata: Map<String, String>?): Message.Operation {
    val operation = Message.Operation()
    operation.clientId = clientId
    operation.description = description
    operation.metadata = metadata
    return operation
}

/**
 * MessageProperty object representing the properties of a message.
 */
object MessageProperty {
    const val SERIAL = "serial"
    const val CLIENT_ID = "clientId"
    const val ROOM_ID = "roomId"
    const val TEXT = "text"
    const val CREATED_AT = "createdAt"
    const val METADATA = "metadata"
    const val HEADERS = "headers"
    const val ACTION = "action"
    const val VERSION = "version"
    const val TIMESTAMP = "timestamp"
    const val OPERATION = "operation"
}

/**
 * MessageOperationProperty object representing the properties of a message operation.
 */
object MessageOperationProperty {
    const val CLIENT_ID = "clientId"
    const val DESCRIPTION = "description"
    const val METADATA = "metadata"
}
