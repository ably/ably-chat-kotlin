package com.ably.chat

import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.RealtimeAnnotations
import io.ably.lib.types.Annotation
import io.ably.lib.types.AnnotationAction
import io.ably.lib.types.MessageAction
import io.ably.lib.types.Summary
import io.ably.lib.types.SummaryClientIdCounts
import io.ably.lib.types.SummaryClientIdList
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Add, delete, and subscribe to message reactions.
 */
public interface MessagesReactions {
    /**
     * Sends a reaction to a specific message.
     *
     * @param messageSerial The unique serial identifier of the message to which the reaction should be added.
     * @param name The name of the reaction, such as an emoji or predefined reaction identifier.
     * @param type The type of the reaction behavior, represented by [MessageReactionType]. If not specified,
     * the default type configured in the room's [MessageOptions.defaultMessageReactionType] will be used.
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
     * [MessageOptions.defaultMessageReactionType] of the room.
     */
    public suspend fun delete(messageSerial: String, name: String? = null, type: MessageReactionType? = null)

    /**
     * Subscribe to message reaction summaries. Use this to keep message reaction
     * counts up to date efficiently in the UI.
     * @param listener The listener to call when a message reaction summary is received
     * @return A subscription object that should be used to unsubscribe
     */
    public fun subscribe(listener: Listener): Subscription

    /**
     * Subscribe to individual reaction events.
     * If you only need to keep track of reaction counts and clients, use
     * subscribe() instead.
     * @param listener The listener to call when a message reaction event is received
     * @return A subscription object that should be used to unsubscribe
     */
    public fun subscribeRaw(listener: MessageRawReactionListener): Subscription

    /**
     * An interface for listening to new messaging reaction event
     */
    public fun interface Listener {
        /**
         * A function that can be called when the new messaging event happens.
         * @param event The event that happened.
         */
        public fun onReactionSummary(event: MessageReactionSummaryEvent)
    }

    /**
     * An interface for listening to new messaging reaction event
     */
    public fun interface MessageRawReactionListener {
        /**
         * A function that can be called when the new messaging event happens.
         * @param event The event that happened.
         */
        public fun onRawReaction(event: MessageReactionRawEvent)
    }
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
 */
public interface MessageReactionSummaryEvent {
    /** The type of the event */
    public val type: MessageReactionSummaryEventType

    /** The message reactions summary. */
    public val summary: MessageReactionsSummary
}

/**
 * Represents a summary of reactions associated with a particular message.
 *
 * This interface provides detailed information about the different types of reactions
 * applied to a message, categorized into unique, distinct, and multiple types.
 */
public interface MessageReactionsSummary {
    /** Reference to the original message's serial number */
    public val messageSerial: String

    /** Map of unique-type reactions summaries */
    public val unique: Map<String, SummaryClientIdList>

    /** Map of distinct-type reactions summaries */
    public val distinct: Map<String, SummaryClientIdList>

    /** Map of multiple-type reactions summaries */
    public val multiple: Map<String, SummaryClientIdCounts>
}

/**
 * @see [MessagesReactions.send]
 */
public suspend fun MessagesReactions.send(message: Message, name: String, type: MessageReactionType? = null, count: Int = 1) =
    send(
        messageSerial = message.serial,
        name = name,
        type = type,
        count = count,
    )

/**
 * @see [MessagesReactions.delete]
 */
public suspend fun MessagesReactions.delete(message: Message, name: String? = null, type: MessageReactionType? = null) =
    delete(
        messageSerial = message.serial,
        name = name,
        type = type,
    )

internal data class DefaultMessageReactionSummary(
    override val messageSerial: String,
    override val unique: Map<String, SummaryClientIdList> = mapOf(),
    override val distinct: Map<String, SummaryClientIdList> = mapOf(),
    override val multiple: Map<String, SummaryClientIdCounts> = mapOf(),
) : MessageReactionsSummary

internal data class DefaultMessageReactionRawEvent(
    override val type: MessageReactionEventType,
    override val timestamp: Long,
    override val reaction: MessageReaction,
) : MessageReactionRawEvent

internal data class DefaultMessageReactionSummaryEvent(
    override val summary: MessageReactionsSummary,
    override val type: MessageReactionSummaryEventType = MessageReactionSummaryEventType.Summary,
) : MessageReactionSummaryEvent

internal data class DefaultMessageReaction(
    override val messageSerial: String,
    override val type: MessageReactionType,
    override val name: String,
    override val count: Int?,
    override val clientId: String,
) : MessageReaction

internal class DefaultMessagesReactions(
    private val chatApi: ChatApi,
    private val roomName: String,
    private val channel: Channel,
    private val annotations: RealtimeAnnotations,
    private val options: MessageOptions,
    parentLogger: Logger,
) : MessagesReactions {

    private val logger = parentLogger.withContext("MessagesReactions")

    private val reactionsScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    private val listeners: MutableList<MessagesReactions.Listener> = CopyOnWriteArrayList()
    private val rawEventlisteners: MutableList<MessagesReactions.MessageRawReactionListener> = CopyOnWriteArrayList()

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
                    it.onReactionSummary(summaryEvent)
                }
            }
        }

        reactionsScope.launch {
            rawEventBus.collect { rawEvent ->
                rawEventlisteners.forEach {
                    it.onRawReaction(rawEvent)
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
            "MessagesReactions.send();",
            context = mapOf(
                "messageSerial" to messageSerial,
                "name" to name,
                "type" to reactionType.name,
                "count" to count.toString(),
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
            throw clientError("count option only supports multiple type")
        }

        // CHA-MR4b3
        if (count != null && count <= 0) {
            throw clientError("reaction count should be positive integer")
        }

        if (name.isEmpty()) {
            throw clientError("reaction name cannot be empty string")
        }
    }

    override suspend fun delete(messageSerial: String, name: String?, type: MessageReactionType?) {
        // CHA-MR11a1
        checkMessageSerialIsNotEmpty(messageSerial)

        val reactionType = type ?: options.defaultMessageReactionType

        logger.trace(
            "MessagesReactions.delete();",
            context = mapOf(
                "messageSerial" to messageSerial,
                "name" to (name ?: ""),
                "type" to reactionType.name,
            ),
        )

        if (reactionType != MessageReactionType.Unique && name.isNullOrEmpty()) {
            throw clientError("cannot delete reaction of type $type without a name")
        }

        chatApi.deleteMessageReaction(
            roomName = roomName,
            messageSerial = messageSerial,
            type = reactionType,
            name = name,
        )
    }

    override fun subscribe(listener: MessagesReactions.Listener): Subscription {
        logger.trace("MessagesReactions.subscribe()")
        listeners.add(listener)
        return Subscription {
            logger.trace("MessagesReactions.unsubscribe()")
            listeners.remove(listener)
        }
    }

    override fun subscribeRaw(listener: MessagesReactions.MessageRawReactionListener): Subscription {
        logger.trace("MessagesReactions.subscribeRaw()")

        if (!options.rawMessageReactions) {
            throw clientError("raw message reactions are not enabled")
        }

        rawEventlisteners.add(listener)
        return Subscription {
            logger.trace("MessagesReactions.unsubscribeRaw()")
            rawEventlisteners.remove(listener)
        }
    }

    fun dispose() {
        summarySubscription.unsubscribe()
        annotationSubscription?.unsubscribe()
        reactionsScope.cancel()
    }

    private fun checkMessageSerialIsNotEmpty(messageSerial: String) {
        if (messageSerial.isEmpty()) {
            throw clientError("messageSerial cannot be empty")
        }
    }

    private fun internalMessageSummaryListener(message: PubSubMessage) {
        logger.trace("MessagesReactions.internalSummaryListener();", context = mapOf("message" to message.toString()))

        // only process summary events with the serial
        if (message.action !== MessageAction.MESSAGE_SUMMARY || message.serial == null) {
            message.serial ?: logger.warn(
                "DefaultMessageReactions.internalSummaryListener(); received summary without serial",
                context = mapOf("message" to message.toString()),
            )

            return
        }

        if (message.annotations?.summary == null) {
            // This means the summary is now empty, which is valid.
            // Happens when there are no reactions such as after deleting the last reaction.
            summaryEventBus.tryEmit(
                DefaultMessageReactionSummaryEvent(
                    DefaultMessageReactionSummary(
                        messageSerial = message.serial,
                        unique = emptyMap(),
                        distinct = emptyMap(),
                        multiple = emptyMap(),
                    ),
                ),
            )
            return
        }

        val unique = message.annotations?.summary?.get(MessageReactionType.Unique.type)
            ?.let { Summary.asSummaryUniqueV1(it) } ?: mapOf()
        val distinct = message.annotations?.summary?.get(MessageReactionType.Distinct.type)
            ?.let { Summary.asSummaryDistinctV1(it) } ?: mapOf()
        val multiple = message.annotations?.summary?.get(MessageReactionType.Multiple.type)
            ?.let { Summary.asSummaryMultipleV1(it) } ?: mapOf()

        summaryEventBus.tryEmit(
            DefaultMessageReactionSummaryEvent(
                DefaultMessageReactionSummary(
                    messageSerial = message.serial,
                    unique = unique,
                    distinct = distinct,
                    multiple = multiple,
                ),
            ),
        )
    }

    private fun internalAnnotationListener(annotation: Annotation) {
        logger.trace("MessagesReactions.internalAnnotationListener();", context = mapOf("annotation" to annotation.toString()))

        if (annotation.messageSerial == null) {
            logger.warn(
                "MessagesReactions.internalAnnotationListener(); received event with missing messageSerial",
                context = mapOf("annotation" to annotation.toString()),
            )
            return
        }

        val reactionType = MessageReactionType.tryFind(annotation.type) ?: run {
            logger.debug(
                "DefaultMessageReactions.internalAnnotationListener(); received event with unknown type",
                context = mapOf("annotation" to annotation.toString()),
            )
            return
        }

        val action = annotation.action ?: run {
            logger.debug("DefaultMessageReactions.internalAnnotationListener(); received event with unknown action")
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
