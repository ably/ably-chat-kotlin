package com.ably.chat

import com.google.gson.JsonObject
import io.ably.lib.types.Message.Operation
import io.ably.lib.types.MessageAction

/**
 * [Headers type for chat messages.
 */
public typealias MessageHeaders = Headers

/**
 * [Metadata] type for chat messages.
 */
public typealias MessageMetadata = Metadata

/**
 * Represents a single message in a chat room.
 */
public interface Message {
    /**
     * The unique identifier of the message.
     * Spec: CHA-M2d
     */
    public val serial: String

    /**
     * The clientId of the user who created the message.
     */
    public val clientId: String

    /**
     * The roomId of the chat room to which the message belongs.
     */
    public val roomId: String

    /**
     * The text of the message.
     */
    public val text: String

    /**
     * The timestamp at which the message was created.
     */
    public val createdAt: Long

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
    public val metadata: MessageMetadata

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
    public val headers: MessageHeaders

    /**
     * The latest action of the message. This can be used to determine if the message was created, updated, or deleted.
     * Spec: CHA-M10
     */
    public val action: MessageAction

    /**
     * A unique identifier for the latest version of this message.
     * Spec: CHA-M10a
     */
    public val version: String

    /**
     * The timestamp at which this version was updated, deleted, or created.
     */
    public val timestamp: Long

    /**
     * The details of the operation that modified the message. This is only set for update and delete actions. It contains
     * information about the operation: the clientId of the user who performed the operation, a description, and metadata.
     */
    public val operation: Operation?
}

public fun Message.copy(
    text: String = this.text,
    headers: MessageHeaders = this.headers,
    metadata: MessageMetadata = this.metadata,
): Message =
    (this as? DefaultMessage)?.copy(
        text = text,
        headers = headers,
        metadata = metadata,
    ) ?: throw clientError("Message interface is not suitable for inheritance")

internal data class DefaultMessage(
    override val serial: String,
    override val clientId: String,
    override val roomId: String,
    override val text: String,
    override val createdAt: Long,
    override val metadata: MessageMetadata,
    override val headers: MessageHeaders,
    override val action: MessageAction,
    override val version: String,
    override val timestamp: Long,
    override val operation: Operation? = null,
) : Message

internal fun buildMessageOperation(jsonObject: JsonObject?): Operation? {
    if (jsonObject == null) {
        return null
    }
    val operation = Operation()
    if (jsonObject.has(MessageOperationProperty.ClientId)) {
        operation.clientId = jsonObject.get(MessageOperationProperty.ClientId).asString
    }
    if (jsonObject.has(MessageOperationProperty.Description)) {
        operation.description = jsonObject.get(MessageOperationProperty.Description).asString
    }
    if (jsonObject.has(MessageOperationProperty.Metadata)) {
        val metadataObject = jsonObject.getAsJsonObject(MessageOperationProperty.Metadata)
        operation.metadata = mutableMapOf()
        for ((key, value) in metadataObject.entrySet()) {
            operation.metadata[key] = value.asString
        }
    }
    return operation
}

internal fun buildMessageOperation(clientId: String, description: String?, metadata: Map<String, String>?): Operation {
    val operation = Operation()
    operation.clientId = clientId
    operation.description = description
    operation.metadata = metadata
    return operation
}

/**
 * MessageProperty object representing the properties of a message.
 */
internal object MessageProperty {
    const val Serial = "serial"
    const val ClientId = "clientId"
    const val RoomId = "roomId"
    const val Text = "text"
    const val CreatedAt = "createdAt"
    const val Metadata = "metadata"
    const val Headers = "headers"
    const val Action = "action"
    const val Version = "version"
    const val Timestamp = "timestamp"
    const val Operation = "operation"
}

/**
 * MessageOperationProperty object representing the properties of a message operation.
 */
internal object MessageOperationProperty {
    const val ClientId = "clientId"
    const val Description = "description"
    const val Metadata = "metadata"
}
