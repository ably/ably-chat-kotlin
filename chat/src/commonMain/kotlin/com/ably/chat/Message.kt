package com.ably.chat

import com.ably.chat.json.JsonObject
import io.ably.lib.types.MessageAction as PubSubMessageAction

/**
 * Represents a single message in a chat room.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
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
    public val metadata: JsonObject

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
    public val headers: Map<String, String>

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
    public val reactions: MessageReactionSummary

    /**
     * A server-provided string extracted from a JWT claim, if available.
     * This is a read-only value set by the server based on channel-specific JWT claims.
     * Spec: CHA-M2h
     */
    public val userClaim: String?
}

/**
 * Contains the details regarding the current version of the message - including when it was updated and by whom.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface MessageVersion {

    /**
     * A unique identifier for the latest version of this message.
     */
    public val serial: String

    /**
     * The timestamp at which this version was updated, deleted, or created.
     */
    public val timestamp: Long

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
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface MessageReactionSummary {
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

/**
 * Represents the latest action of a message.
 */
public enum class MessageAction {
    /**
     * Represents the action of creating a new message within the system.
     * This action is used when a new message is initialized and stored.
     */
    MessageCreate,

    /**
     * Represents the action of updating an existing message within the system.
     * This action is used when modifications are made to a previously stored message.
     */
    MessageUpdate,

    /**
     * Represents the action of deleting an existing message.
     * This action is used when a message is softly removed.
     */
    MessageDelete,
}

/**
 * Returns optional to preserve binary compatibility if new enum values are added to the pubsub message action enum.
 */
internal fun PubSubMessageAction.toMessageAction(): MessageAction? = when (this) {
    PubSubMessageAction.MESSAGE_CREATE -> MessageAction.MessageCreate
    PubSubMessageAction.MESSAGE_UPDATE -> MessageAction.MessageUpdate
    PubSubMessageAction.MESSAGE_DELETE -> MessageAction.MessageDelete
    else -> null
}

public fun Message.copy(
    text: String = this.text,
    headers: Map<String, String> = this.headers,
    metadata: JsonObject = this.metadata,
    userClaim: String? = this.userClaim,
): Message =
    (this as? DefaultMessage)?.copy(
        text = text,
        headers = headers,
        metadata = metadata,
        userClaim = userClaim,
    ) ?: throw clientError("unable to copy message; Message interface is not suitable for inheritance", ErrorCode.InvalidArgument)

public fun Message.with(
    event: ChatMessageEvent,
): Message {
    checkMessageSerial(event.message.serial)

    if (event.type == ChatMessageEventType.Created) { // CHA-M11a
        throw clientError(
            "unable to apply message event; MessageEvent.message.action must be MESSAGE_UPDATE or MESSAGE_DELETE",
            ErrorCode.InvalidArgument,
        )
    }

    if (event.message.version <= this.version) return this

    return (event.message as? DefaultMessage)?.copy(
        reactions = reactions,
    ) ?: throw clientError("unable to apply message event; Message interface is not suitable for inheritance", ErrorCode.InvalidArgument)
}

internal operator fun MessageVersion.compareTo(other: MessageVersion): Int = compareValuesBy(this, other) { it.serial }

public fun Message.with(
    event: MessageReactionSummaryEvent,
): Message {
    checkMessageSerial(event.messageSerial)

    return (this as? DefaultMessage)?.copy(
        reactions = DefaultMessageReactionSummary(
            unique = event.reactions.unique,
            distinct = event.reactions.distinct,
            multiple = event.reactions.multiple,
        ),
    ) ?: throw clientError("unable to apply reaction event; Message interface is not suitable for inheritance", ErrorCode.InvalidArgument)
}

private fun Message.checkMessageSerial(serial: String) {
    // message event (update or delete)
    if (serial != this.serial) {
        throw clientError("unable to apply event; cannot apply event for a different message", ErrorCode.InvalidArgument)
    }
}

internal data class DefaultMessage(
    override val serial: String,
    override val clientId: String,
    override val text: String,
    override val timestamp: Long,
    override val metadata: JsonObject,
    override val headers: Map<String, String>,
    override val action: MessageAction,
    override val version: MessageVersion,
    override val reactions: MessageReactionSummary = DefaultMessageReactionSummary(),
    override val userClaim: String? = null,
) : Message

internal data class DefaultMessageReactionSummary(
    override val unique: Map<String, SummaryClientIdList> = mapOf(),
    override val distinct: Map<String, SummaryClientIdList> = mapOf(),
    override val multiple: Map<String, SummaryClientIdCounts> = mapOf(),
) : MessageReactionSummary

internal data class DefaultMessageVersion(
    override val serial: String,
    override val timestamp: Long,
    override val clientId: String? = null,
    override val description: String? = null,
    override val metadata: Map<String, String>? = null,
) : MessageVersion

internal fun buildMessageReactions(jsonObject: JsonObject?): MessageReactionSummary {
    if (jsonObject == null) return DefaultMessageReactionSummary()

    val uniqueJson = jsonObject[MessageReactionsProperty.Unique]
    val distinctJson = jsonObject[MessageReactionsProperty.Distinct]
    val multipleJson = jsonObject[MessageReactionsProperty.Multiple]

    return DefaultMessageReactionSummary(
        unique = parseSummaryUniqueV1(uniqueJson),
        distinct = parseSummaryDistinctV1(distinctJson),
        multiple = parseSummaryMultipleV1(multipleJson),
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
    const val UserClaim = "userClaim"
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
