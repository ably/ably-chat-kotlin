package com.ably.chat

import com.ably.chat.json.JsonObject
import io.ably.lib.types.MessageAction
import io.ably.lib.types.Summary
import io.ably.lib.types.SummaryClientIdCounts
import io.ably.lib.types.SummaryClientIdList

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
     * The text of the message.
     */
    public val text: String

    /**
     * The timestamp at which the message was created.
     */
    public val timestamp: Long

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
    public val version: MessageVersion

    /**
     * The reactions summary for this message.
     */
    public val reactions: MessageReactions
}

public interface MessageVersion {

    /**
     * A unique identifier for the latest version of this message.
     */
    public val serial: String?

    /**
     * The timestamp at which this version was updated, deleted, or created.
     */
    public val timestamp: Long?

    /**
     * The optional clientId of the user who performed the update or deletion.
     */
    public val clientId: String?

    /**
     * The optional description for the update or deletion.
     */
    public val description: String?

    /**
     * The optional metadata associated with the update or deletion.
     */
    public val metadata: Map<String, String>?
}

/**
 * Represents a summary of all reactions on a message.
 */
public interface MessageReactions {
    /**
     * Map of reaction to the summary (total and clients) for reactions of type [MessageReactionType.Unique].
     */
    public val unique: Map<String, SummaryClientIdList>

    /**
     * Map of reaction to the summary (total and clients) for reactions of type [MessageReactionType.Distinct].
     */
    public val distinct: Map<String, SummaryClientIdList>

    /**
     * Map of reaction to the summary (total and clients) for reactions of type [MessageReactionType.Multiple].
     */
    public val multiple: Map<String, SummaryClientIdCounts>
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

public fun Message.with(
    event: ChatMessageEvent,
): Message {
    checkMessageSerial(event.message.serial)

    if (event.type == ChatMessageEventType.Created) { // CHA-M11a
        throw clientError("MessageEvent.message.action must be MESSAGE_UPDATE or MESSAGE_DELETE")
    }

    if (event.message.version <= this.version) return this

    return (event.message as? DefaultMessage)?.copy(
        reactions = reactions,
    ) ?: throw clientError("Message interface is not suitable for inheritance")
}

internal operator fun MessageVersion.compareTo(other: MessageVersion): Int {
    // If one our version serials isn't set, cannot compare
    if (serial == null || other.serial == null) {
        return -1
    }

    // Use the comparator to determine the comparison
    return compareValuesBy(this, other, { it.serial })
}

public fun Message.with(
    event: MessageReactionSummaryEvent,
): Message {
    checkMessageSerial(event.summary.messageSerial)

    return (this as? DefaultMessage)?.copy(
        reactions = DefaultMessageReactions(
            unique = event.summary.unique,
            distinct = event.summary.distinct,
            multiple = event.summary.multiple,
        ),
    ) ?: throw clientError("Message interface is not suitable for inheritance")
}

private fun Message.checkMessageSerial(serial: String) {
    // message event (update or delete)
    if (serial != this.serial) {
        throw clientError("cannot apply event for a different message")
    }
}

internal data class DefaultMessage(
    override val serial: String,
    override val clientId: String,
    override val text: String,
    override val timestamp: Long,
    override val metadata: MessageMetadata,
    override val headers: MessageHeaders,
    override val action: MessageAction,
    override val version: MessageVersion,
    override val reactions: MessageReactions = DefaultMessageReactions(),
) : Message

internal data class DefaultMessageReactions(
    override val unique: Map<String, SummaryClientIdList> = mapOf(),
    override val distinct: Map<String, SummaryClientIdList> = mapOf(),
    override val multiple: Map<String, SummaryClientIdCounts> = mapOf(),
) : MessageReactions

internal data class DefaultMessageVersion(
    override val serial: String? = null,
    override val timestamp: Long? = null,
    override val clientId: String? = null,
    override val description: String? = null,
    override val metadata: Map<String, String>? = null,
) : MessageVersion

internal fun buildMessageReactions(jsonObject: JsonObject?): MessageReactions {
    if (jsonObject == null) return DefaultMessageReactions()

    val uniqueJson = jsonObject[MessageReactionsProperty.Unique]
    val distinctJson = jsonObject[MessageReactionsProperty.Distinct]
    val multipleJson = jsonObject[MessageReactionsProperty.Multiple]

    return DefaultMessageReactions(
        unique = uniqueJson?.let { Summary.asSummaryUniqueV1(it.toGson().asJsonObject) } ?: mapOf(),
        distinct = distinctJson?.let { Summary.asSummaryDistinctV1(it.toGson().asJsonObject) } ?: mapOf(),
        multiple = multipleJson?.let { Summary.asSummaryMultipleV1(it.toGson().asJsonObject) } ?: mapOf(),
    )
}

/**
 * MessageProperty object representing the properties of a message.
 */
internal object MessageProperty {
    const val Serial = "serial"
    const val ClientId = "clientId"
    const val Text = "text"
    const val Metadata = "metadata"
    const val Headers = "headers"
    const val Action = "action"
    const val Version = "version"
    const val Timestamp = "timestamp"
    const val Reactions = "reactions"
}

/**
 * MessageVersionProperty object representing the properties of a message version.
 */
internal object MessageVersionProperty {
    const val Serial = "serial"
    const val ClientId = "clientId"
    const val Timestamp = "timestamp"
    const val Description = "description"
    const val Metadata = "metadata"
}

/**
 * MessageReactionsProperty object representing the properties of a message reaction.
 */
internal object MessageReactionsProperty {
    const val Unique = "unique"
    const val Distinct = "distinct"
    const val Multiple = "multiple"
}
