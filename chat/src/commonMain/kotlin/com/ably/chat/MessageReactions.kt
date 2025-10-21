package com.ably.chat

import com.ably.chat.annotations.InternalChatApi
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.RealtimeAnnotations
import io.ably.lib.types.Annotation
import io.ably.lib.types.AnnotationAction
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import io.ably.lib.types.MessageAction as PubSubMessageAction

/**
 * Add, delete, and subscribe to message reactions.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface MessageReactions {
    /**
     * Sends a reaction to a specific message.
     *
     * @param messageSerial The unique serial identifier of the message to which the reaction should be added.
     * @param name The name of the reaction, such as an emoji or predefined reaction identifier.
     * @param type The type of the reaction behavior, represented by [MessageReactionType]. If not specified,
     * the default type configured in the room's [MessagesOptions.defaultMessageReactionType] will be used.
     * @param count The number of reactions to apply (for [MessageReactionType.Multiple] only). Defaults to 1.
     */
    public suspend fun send(messageSerial: String, name: String, type: MessageReactionType? = null, count: Int? = null)

    /**
     * Delete a message reaction
     * @param messageSerial The message serial to remove the reaction from
     * @param name The reaction name to delete; ie. the emoji. Required for all reaction types
     * except [MessageReactionType.Unique].
     * @param type The type of reaction, must be one of [MessageReactionType].
     * If not set, the default type will be used which is configured in the
     * [MessagesOptions.defaultMessageReactionType] of the room.
     */
    public suspend fun delete(messageSerial: String, name: String? = null, type: MessageReactionType? = null)

    /**
     * Subscribe to message reaction summaries. Use this to keep message reaction
     * counts up to date efficiently in the UI.
     * @param listener The listener to call when a message reaction summary is received
     * @return A subscription object that should be used to unsubscribe
     */
    public fun subscribe(listener: MessageReactionListener): Subscription

    /**
     * Subscribe to individual reaction events.
     * If you only need to keep track of reaction counts and clients, use
     * subscribe() instead.
     * @param listener The listener to call when a message reaction event is received
     * @return A subscription object that should be used to unsubscribe
     */
    public fun subscribeRaw(listener: MessageRawReactionListener): Subscription

    /**
     * Get the reaction summary for a message filtered by a particular client.
     * @param messageSerial The ID of the message to get the reaction summary for.
     * @param clientId The client to fetch the reaction summary for (leave unset for current client).
     * @return A clipped reaction summary containing only the requested clientId.
     * @example
     * ```kotlin
     * // Subscribe to reaction summaries and check for specific client reactions
     * room.messages.reactions.asFlow().collect { event ->
     *   // For brevity of example, we check unique 👍 (normally iterate for all relevant reactions)
     *   val uniqueLikes = event.summary.unique["👍"]
     *   if (uniqueLikes?.clipped == true && !uniqueLikes.clientIds.contains(myClientId)) {
     *     // summary is clipped and doesn't include myClientId, so we need to fetch a clientSummary
     *     val clientReactions = room.messages.reactions.getClientReactionSummary(
     *       event.messageSerial,
     *       myClientId
     *     )
     *     if (clientReactions.unique["👍"] != null) {
     *       // client has reacted with 👍
     *     }
     *   }
     *   // from here, process the summary as usual
     * }
     * ```
     */
    public suspend fun clientReactions(messageSerial: String, clientId: String? = null): MessageReactionSummary
}

/**
 * @return [MessageReactionSummaryEvent] events as a [Flow]
 */
public fun MessageReactions.asFlow(): Flow<MessageReactionSummaryEvent> = transformCallbackAsFlow {
    subscribe(it)
}

public enum class MessageReactionEventType(public val eventName: String) {
    /**
     * A reaction was added to a message.
     */
    Create("reaction.create"),

    /**
     * A reaction was removed from a message.
     */
    Delete("reaction.delete"),
}

public enum class MessageReactionSummaryEventType(public val eventName: String) {
    /**
     * A reactions summary was updated for a message.
     */
    Summary("reaction.summary"),
}

/**
 * (CHA-MR2) Represents the type of reactions that can be applied to a message. Each reaction type defines
 * unique rules for how reactions from clients are handled and counted towards the reaction summary.
 */
public enum class MessageReactionType(public val type: String) {
    /**
     * (CHA-MR2a) Allows for at most one reaction per client per message. If a client reacts
     * to a message a second time, only the second reaction is counted in the
     * summary.
     *
     * This is similar to reactions on iMessage, Facebook Messenger, or WhatsApp.
     */
    Unique("reaction:unique.v1"),

    /**
     * (CHA-MR2b) Allows for at most one reaction of each type per client per message. It is
     * possible for a client to add multiple reactions to the same message as
     * long as they are different (eg different emojis). Duplicates are not
     * counted in the summary.
     *
     * This is similar to reactions on Slack.
     */
    Distinct("reaction:distinct.v1"),

    /**
     * (CHA-MR2c) Allows any number of reactions, including repeats, and they are counted in
     * the summary. The reaction payload also includes a count of how many times
     * each reaction should be counted (defaults to 1 if not set).
     *
     * This is similar to the clap feature on Medium or how room reactions work.
     */
    Multiple("reaction:multiple.v1"),
    ;

    internal companion object {
        fun tryFind(type: String) = MessageReactionType.entries.firstOrNull { it.type == type }
    }
}

/**
 * A listener for summary message reaction events.
 */
public typealias MessageReactionListener = (MessageReactionSummaryEvent) -> Unit

/**
 * A listener for individual message reaction events.
 */
public typealias MessageRawReactionListener = (MessageReactionRawEvent) -> Unit

/**
 * Represents a raw message reaction event, such as when a reaction is added or removed from a message.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface MessageReactionRawEvent {
    /**
     * Whether reaction was added or removed
     */
    public val type: MessageReactionEventType

    /**
     * The timestamp of this event
     */
    public val timestamp: Long

    /**
     * The message reaction that was received.
     */
    public val reaction: MessageReaction
}

/**
 * The message reaction that was received.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface MessageReaction {
    /**
     * Serial of the message this reaction is for
     */
    public val messageSerial: String

    /**
     * Type of reaction
     */
    public val type: MessageReactionType

    /**
     * The reaction name (typically an emoji)
     */
    public val name: String

    /**
     * Count of the reaction (only for type Multiple, if set)
     */
    public val count: Int?

    /**
     * The client ID of the user who added/removed the reaction
     */
    public val clientId: String
}

/**
 * Event interface representing a summary of message reactions.
 * This event aggregates different types of reactions (single, distinct, counter) for a specific message.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface MessageReactionSummaryEvent {
    /** The type of the event */
    public val type: MessageReactionSummaryEventType

    /** Reference to the original message's serial number */
    public val messageSerial: String

    /** The message reactions summary. */
    public val reactions: MessageReactionSummary
}

@InternalChatApi
public fun MessageReactionSummaryEvent.mergeWith(reactions: MessageReactionSummary): MessageReactionSummaryEvent =
    DefaultMessageReactionSummaryEvent(
        messageSerial = messageSerial,
        type = type,
        reactions = this.reactions.mergeWith(reactions),
    )

private fun MessageReactionSummary.mergeWith(other: MessageReactionSummary): MessageReactionSummary = DefaultMessageReactionSummary(
    unique = unique.mergeWith(other.unique),
    distinct = distinct.mergeWith(other.distinct),
    multiple = multiple.mergeWith(other.multiple),
)

@JvmName("mergeWithSummaryClientIdList")
private fun Map<String, SummaryClientIdList>.mergeWith(other: Map<String, SummaryClientIdList>): Map<String, SummaryClientIdList> =
    mapValues {
        if (it.value.clipped && !it.value.clientIds.containsAll(other[it.key]?.clientIds ?: listOf())) {
            SummaryClientIdList(
                it.value.total,
                buildSet {
                    addAll(it.value.clientIds)
                    other[it.key]?.clientIds?.let(::addAll)
                }.toList(),
                it.value.clipped,
            )
        } else {
            it.value
        }
    }

@JvmName("mergeWithSummaryClientIdCounts")
private fun Map<String, SummaryClientIdCounts>.mergeWith(other: Map<String, SummaryClientIdCounts>): Map<String, SummaryClientIdCounts> =
    mapValues {
        if (it.value.clipped && !it.value.clientIds.keys.containsAll(other[it.key]?.clientIds?.keys ?: setOf())) {
            SummaryClientIdCounts(
                total = it.value.total,
                clientIds = buildMap {
                    putAll(it.value.clientIds)
                    putAll(other[it.key]?.clientIds ?: emptyMap())
                },
                totalUnidentified = it.value.totalUnidentified,
                clipped = it.value.clipped,
                totalClientIds = it.value.totalClientIds,
            )
        } else {
            it.value
        }
    }

/**
 * @see [MessageReactions.send]
 */
public suspend fun MessageReactions.send(message: Message, name: String, type: MessageReactionType? = null, count: Int = 1): Unit =
    send(
        messageSerial = message.serial,
        name = name,
        type = type,
        count = count,
    )

/**
 * @see [MessageReactions.delete]
 */
public suspend fun MessageReactions.delete(message: Message, name: String? = null, type: MessageReactionType? = null): Unit =
    delete(
        messageSerial = message.serial,
        name = name,
        type = type,
    )

internal data class DefaultMessageReactionRawEvent(
    override val type: MessageReactionEventType,
    override val timestamp: Long,
    override val reaction: MessageReaction,
) : MessageReactionRawEvent

internal data class DefaultMessageReactionSummaryEvent(
    override val messageSerial: String,
    override val reactions: MessageReactionSummary,
    override val type: MessageReactionSummaryEventType = MessageReactionSummaryEventType.Summary,
) : MessageReactionSummaryEvent

internal data class DefaultMessageReaction(
    override val messageSerial: String,
    override val type: MessageReactionType,
    override val name: String,
    override val count: Int?,
    override val clientId: String,
) : MessageReaction

internal class DefaultMessageReactions(
    private val chatApi: ChatApi,
    private val roomName: String,
    private val channel: Channel,
    private val annotations: RealtimeAnnotations,
    private val options: MessagesOptions,
    parentLogger: Logger,
) : MessageReactions {

    private val logger = parentLogger.withContext("MessageReactions")

    private val reactionsScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val listeners: MutableList<MessageReactionListener> = CopyOnWriteArrayList()
    private val rawEventlisteners: MutableList<MessageRawReactionListener> = CopyOnWriteArrayList()

    private val summaryEventBus = MutableSharedFlow<MessageReactionSummaryEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val rawEventBus = MutableSharedFlow<MessageReactionRawEvent>(
        extraBufferCapacity = UNLIMITED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val summarySubscription: Subscription
    private var annotationSubscription: Subscription? = null

    init {
        reactionsScope.launch {
            summaryEventBus.collect { summaryEvent ->
                listeners.forEach {
                    it.invoke(summaryEvent)
                }
            }
        }

        reactionsScope.launch {
            rawEventBus.collect { rawEvent ->
                rawEventlisteners.forEach {
                    it.invoke(rawEvent)
                }
            }
        }

        val messageSummaryListener = PubSubMessageListener {
            internalMessageSummaryListener(it)
        }

        channel.subscribe(messageSummaryListener)
        summarySubscription = Subscription { channel.unsubscribe(messageSummaryListener) }

        if (options.rawMessageReactions) {
            val annotationListener = RealtimeAnnotations.AnnotationListener {
                internalAnnotationListener(it)
            }
            annotations.subscribe(annotationListener)
            annotationSubscription = Subscription { annotations.unsubscribe(annotationListener) }
        }
    }

    override suspend fun send(
        messageSerial: String,
        name: String,
        type: MessageReactionType?,
        count: Int?,
    ) {
        checkSendArguments(messageSerial, name, type, count)

        val reactionType = type ?: options.defaultMessageReactionType

        logger.trace(
            "MessageReactions.send();",
            context = mapOf(
                "messageSerial" to messageSerial,
                "name" to name,
                "type" to reactionType.name,
                "count" to count,
            ),
        )

        chatApi.sendMessageReaction(
            roomName = roomName,
            messageSerial = messageSerial,
            type = reactionType,
            name = name,
            count = count ?: 1,
        )
    }

    @Suppress("ThrowsCount")
    private fun checkSendArguments(
        messageSerial: String,
        name: String,
        type: MessageReactionType?,
        count: Int?,
    ) {
        // CHA-MR4a1
        checkMessageSerialIsNotEmpty(messageSerial)

        // CHA-MR4b3
        if (count != null && type != MessageReactionType.Multiple) {
            throw clientError("unable to send reaction; count option only supports multiple type", ErrorCode.InvalidArgument)
        }

        // CHA-MR4b3
        if (count != null && count <= 0) {
            throw clientError("unable to send reaction; reaction count should be positive integer", ErrorCode.InvalidArgument)
        }

        if (name.isEmpty()) {
            throw clientError("unable to send reaction; reaction name cannot be empty string", ErrorCode.InvalidArgument)
        }
    }

    override suspend fun delete(messageSerial: String, name: String?, type: MessageReactionType?) {
        // CHA-MR11a1
        checkMessageSerialIsNotEmpty(messageSerial)

        val reactionType = type ?: options.defaultMessageReactionType

        logger.trace(
            "MessageReactions.delete();",
            context = mapOf(
                "messageSerial" to messageSerial,
                "name" to (name ?: ""),
                "type" to reactionType.name,
            ),
        )

        if (reactionType != MessageReactionType.Unique && name.isNullOrEmpty()) {
            throw clientError("unable to delete reaction; cannot delete reaction of type $type without a name", ErrorCode.InvalidArgument)
        }

        chatApi.deleteMessageReaction(
            roomName = roomName,
            messageSerial = messageSerial,
            type = reactionType,
            name = name,
        )
    }

    override fun subscribe(listener: MessageReactionListener): Subscription {
        logger.trace("MessageReactions.subscribe()")
        listeners.add(listener)
        return Subscription {
            logger.trace("MessageReactions.unsubscribe()")
            listeners.remove(listener)
        }
    }

    override fun subscribeRaw(listener: MessageRawReactionListener): Subscription {
        logger.trace("MessageReactions.subscribeRaw()")

        if (!options.rawMessageReactions) {
            throw clientError(
                "unable to subscribe to raw reactions; raw message reactions are not enabled",
                ErrorCode.FeatureNotEnabledInRoom,
            )
        }

        rawEventlisteners.add(listener)
        return Subscription {
            logger.trace("MessageReactions.unsubscribeRaw()")
            rawEventlisteners.remove(listener)
        }
    }

    override suspend fun clientReactions(messageSerial: String, clientId: String?): MessageReactionSummary {
        logger.trace("MessageReactions.clientReactions()", context = mapOf("messageSerial" to messageSerial, "fromClientId" to clientId))
        return chatApi.getClientReactions(roomName, messageSerial, clientId)
    }

    fun dispose() {
        summarySubscription.unsubscribe()
        annotationSubscription?.unsubscribe()
        reactionsScope.cancel()
    }

    private fun checkMessageSerialIsNotEmpty(messageSerial: String) {
        if (messageSerial.isEmpty()) {
            throw clientError("unable to perform reaction operation; messageSerial cannot be empty", ErrorCode.InvalidArgument)
        }
    }

    private fun internalMessageSummaryListener(message: PubSubMessage) {
        logger.trace("MessageReactions.internalSummaryListener();", context = mapOf("message" to message))

        // only process summary events with the serial
        if (message.action !== PubSubMessageAction.MESSAGE_SUMMARY || message.serial == null) {
            message.serial ?: logger.warn(
                "DefaultMessageReactionSummary.internalSummaryListener(); received summary without serial",
                context = mapOf("message" to message),
            )

            return
        }

        if (message.annotations?.summary == null) {
            // This means the summary is now empty, which is valid.
            // Happens when there are no reactions such as after deleting the last reaction.
            summaryEventBus.tryEmit(
                DefaultMessageReactionSummaryEvent(
                    message.serial,
                    DefaultMessageReactionSummary(
                        unique = emptyMap(),
                        distinct = emptyMap(),
                        multiple = emptyMap(),
                    ),
                ),
            )
            return
        }

        val unique = message.annotations?.summary?.get(MessageReactionType.Unique.type)
            .let { parseSummaryUniqueV1(it.tryAsJsonValue()) }
        val distinct = message.annotations?.summary?.get(MessageReactionType.Distinct.type)
            .let { parseSummaryDistinctV1(it.tryAsJsonValue()) }
        val multiple = message.annotations?.summary?.get(MessageReactionType.Multiple.type)
            .let { parseSummaryMultipleV1(it.tryAsJsonValue()) }

        summaryEventBus.tryEmit(
            DefaultMessageReactionSummaryEvent(
                messageSerial = message.serial,
                DefaultMessageReactionSummary(
                    unique = unique,
                    distinct = distinct,
                    multiple = multiple,
                ),
            ),
        )
    }

    private fun internalAnnotationListener(annotation: Annotation) {
        logger.trace("MessageReactions.internalAnnotationListener();", context = mapOf("annotation" to annotation.toString()))

        if (annotation.messageSerial == null) {
            logger.warn(
                "MessageReactions.internalAnnotationListener(); received event with missing messageSerial",
                context = mapOf("annotation" to annotation),
            )
            return
        }

        val reactionType = MessageReactionType.tryFind(annotation.type) ?: run {
            logger.debug(
                "DefaultMessageReactionSummary.internalAnnotationListener(); received event with unknown type",
                context = mapOf("annotation" to annotation),
            )
            return
        }

        val action = annotation.action ?: run {
            logger.debug("DefaultMessageReactionSummary.internalAnnotationListener(); received event with unknown action")
            return
        }

        val eventType = when (action) {
            AnnotationAction.ANNOTATION_CREATE -> MessageReactionEventType.Create
            AnnotationAction.ANNOTATION_DELETE -> MessageReactionEventType.Delete
        }

        val name = annotation.name ?: run {
            if (eventType === MessageReactionEventType.Delete && reactionType === MessageReactionType.Unique) {
                // deletes of type unique are allowed to have no data
                ""
            } else {
                return
            }
        }

        val count = annotation.count ?: (
            if (eventType === MessageReactionEventType.Create && reactionType === MessageReactionType.Multiple) {
                1
            } else {
                null
            }
            )

        val reactionEvent = DefaultMessageReactionRawEvent(
            type = eventType,
            timestamp = annotation.timestamp,
            reaction = DefaultMessageReaction(
                messageSerial = annotation.messageSerial,
                type = reactionType,
                name = name,
                clientId = annotation.clientId,
                count = count,
            ),
        )

        rawEventBus.tryEmit(reactionEvent)
    }
}
