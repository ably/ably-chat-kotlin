package com.ably.chat

import com.ably.annotations.InternalAPI
import com.ably.chat.OrderBy.NewestFirst
import com.ably.pubsub.RealtimeChannel
import com.google.gson.JsonObject
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import io.ably.lib.realtime.Channel as AblyRealtimeChannel

internal typealias PubSubMessageListener = AblyRealtimeChannel.MessageListener
internal typealias PubSubMessage = io.ably.lib.types.Message

/**
 * This interface is used to interact with messages in a chat room: subscribing
 * to new messages, fetching history, or sending messages.
 *
 * Get an instance via [Room.messages].
 */
public interface Messages {
    /**
     * Get the underlying Ably realtime channel used for the messages in this chat room.
     *
     * @returns the realtime channel
     */
    public val channel: AblyRealtimeChannel

    /**
     * Add, delete, and subscribe to message reactions.
     */
    public val reactions: MessagesReactions

    /**
     * Subscribe to new messages in this chat room.
     * @param listener callback that will be called
     * @return A response object that allows you to control the subscription.
     */
    public fun subscribe(listener: Listener): MessagesSubscription

    /**
     * Get messages that have been previously sent to the chat room, based on the provided options.
     *
     * @param start The start of the time window to query from. See [QueryOptions.start]
     * @param end The end of the time window to query from. See [QueryOptions.end]
     * @param limit The maximum number of messages to return in the response. See [QueryOptions.limit]
     * @param orderBy The order of messages in the query result. See [QueryOptions.orderBy]
     *
     * @return Paginated result of messages. This paginated result can be used to fetch more messages if available.
     */
    public suspend fun history(
        start: Long? = null,
        end: Long? = null,
        limit: Int = 100,
        orderBy: OrderBy = NewestFirst,
    ): PaginatedResult<Message>

    /**
     * Send a message in the chat room.
     *
     * This method uses the Ably Chat API endpoint for sending messages.
     *
     * Note: that the suspending function may resolve before OR after the message is received
     * from the realtime channel. This means you may see the message that was just
     * sent in a callback to `subscribe` before the function resolves.
     * Spec: CHA-M3
     *
     * @param text The text of the message. See [SendMessageParams.text]
     * @param metadata Optional metadata of the message. See [SendMessageParams.metadata]
     * @param headers Optional headers of the message. See [SendMessageParams.headers]
     *
     * @return The message was published.
     */
    public suspend fun send(text: String, metadata: MessageMetadata? = null, headers: MessageHeaders? = null): Message

    /**
     * Update a message in the chat room.
     *
     * This method uses the Ably Chat API REST endpoint for updating messages.
     * It creates a new message with the same serial and a new version.
     * The original message is not modified.
     * Spec: CHA-M8
     *
     * @param updatedMessage The updated copy of the message created using the `message.copy` method.
     * @param operationDescription Optional description for the update action.
     * @param operationMetadata Optional metadata for the update action.
     * @returns updated message.
     */
    public suspend fun update(
        updatedMessage: Message,
        operationDescription: String? = null,
        operationMetadata: OperationMetadata? = null,
    ): Message

    /**
     * Delete a message in the chat room.
     *
     * This method uses the Ably Chat API REST endpoint for deleting messages.
     * It performs a `soft` delete, meaning the message is marked as deleted.
     *
     * Should you wish to restore a deleted message, and providing you have the appropriate permissions,
     * you can simply send an update to the original message.
     * Note: This is subject to change in future versions, whereby a new permissions model will be introduced
     * and a deleted message may not be restorable in this way.
     * Spec: CHA-M9
     *
     * @returns when the message is deleted.
     * @param message - The message to delete.
     * @param operationDescription - Optional description for the delete action.
     * @param operationMetadata - Optional metadata for the delete action.
     * @return A promise that resolves to the deleted message.
     */
    public suspend fun delete(message: Message, operationDescription: String? = null, operationMetadata: OperationMetadata? = null): Message

    /**
     * An interface for listening to new messaging event
     */
    public fun interface Listener {
        /**
         * A function that can be called when the new messaging event happens.
         * @param event The event that happened.
         */
        public fun onEvent(event: ChatMessageEvent)
    }
}

/**
 * @return [ChatMessageEvent] events as a [Flow]
 */
public fun Messages.asFlow(): Flow<ChatMessageEvent> = transformCallbackAsFlow {
    subscribe(it)
}

/**
 * Options for querying messages in a chat room.
 */
internal data class QueryOptions(
    /**
     * The start of the time window to query from. If provided, the response will include
     * messages with timestamps equal to or greater than this value.
     *
     * @defaultValue The beginning of time
     */
    val start: Long? = null,

    /**
     * The end of the time window to query from. If provided, the response will include
     * messages with timestamps less than this value.
     *
     * @defaultValue Now
     */
    val end: Long? = null,

    /**
     * The maximum number of messages to return in the response.
     */
    val limit: Int = 100,

    /**
     * The order of messages in the query result.
     */
    val orderBy: OrderBy = NewestFirst,
)

/**
 * Payload for a message event.
 */
public interface ChatMessageEvent {
    /**
     * The type of the message event.
     */
    public val type: ChatMessageEventType

    /**
     * The message that was received.
     */
    public val message: Message
}

internal data class DefaultChatMessageEvent(
    override val type: ChatMessageEventType,
    override val message: Message,
) : ChatMessageEvent

/**
 * Params for sending a text message. Only `text` is mandatory.
 */
internal data class SendMessageParams(
    /**
     * The text of the message.
     */
    val text: String,

    /**
     * Optional metadata of the message.
     *
     * The metadata is a map of extra information that can be attached to chat
     * messages. It is not used by Ably and is sent as part of the realtime
     * message payload. Example use cases are setting custom styling like
     * background or text colors or fonts, adding links to external images,
     * emojis, etc.
     *
     * Do not use metadata for authoritative information. There is no server-side
     * validation. When reading the metadata treat it like user input.
     *
     * The key `ably-chat` is reserved and cannot be used. Ably may populate
     * this with different values in the future.
     */
    val metadata: MessageMetadata? = null,

    /**
     * Optional headers of the message.
     *
     * The headers are a flat key-value map and are sent as part of the realtime
     * message's extras inside the `headers` property. They can serve similar
     * purposes as the metadata but they are read by Ably and can be used for
     * features such as
     * [subscription filters](https://faqs.ably.com/subscription-filters).
     *
     * Do not use the headers for authoritative information. There is no
     * server-side validation. When reading the headers treat them like user
     * input.
     *
     * The key prefix `ably-chat` is reserved and cannot be used. Ably may add
     * headers prefixed with `ably-chat` in the future.
     */
    val headers: MessageHeaders? = null,
)

internal fun SendMessageParams.toJsonObject(): JsonObject {
    return JsonObject().apply {
        addProperty("text", text)
        // CHA-M3b
        headers?.let { add("headers", it.toJson()) }
        metadata?.let { add("metadata", it) }
    }
}

/**
 * Params for updating a message. It accepts all parameters that sending a
 * message accepts. Also accepts `description` and `metadata` for the update action.
 *
 * Note that updating a message creates a new message with original serial and a new version.
 */
internal data class UpdateMessageParams(
    val message: SendMessageParams,
    /**
     * Optional description for the message action.
     */
    val description: String?,
    /**
     * Optional metadata that will be added to the update action. Defaults to empty.
     */
    val metadata: OperationMetadata?,
)

internal fun UpdateMessageParams.toJsonObject(): JsonObject {
    return JsonObject().apply {
        add("message", message.toJsonObject())
        description?.let { addProperty(MessageOperationProperty.Description, it) }
        metadata?.let { add(MessageOperationProperty.Metadata, it.toJson()) }
    }
}

/**
 * Parameters for deleting a message.
 */
internal data class DeleteMessageParams(
    /**
     * Optional description for the message action.
     */
    val description: String?,
    /**
     * Optional metadata that will be added to the delete action. Defaults to empty.
     */
    val metadata: OperationMetadata?,
)

internal fun DeleteMessageParams.toJsonObject(): JsonObject {
    return JsonObject().apply {
        description?.let { addProperty(MessageOperationProperty.Description, it) }
        metadata?.let { add(MessageOperationProperty.Metadata, it.toJson()) }
    }
}

/**
 * A response object that allows you to control a message subscription.
 */
public interface MessagesSubscription : Subscription {
    /**
     *
     * Get the previous messages that were sent to the room before the listener was subscribed.
     *
     * If the client experiences a discontinuity event (i.e. the connection was lost and could not be resumed), the starting point of
     * getPreviousMessages will be reset.
     *
     * Calls to getPreviousMessages will wait for continuity to be restored before resolving.
     *
     * Once continuity is restored, the subscription point will be set to the beginning of this new period of continuity. To
     * ensure that no messages are missed, you should call getPreviousMessages after any period of discontinuity to
     * fill any gaps in the message history.
     *
     * ```kotlin
     * val subscription = room.messages.subscribe {
     *     println("New message received: $it")
     * }
     * var historicalMessages = subscription.getPreviousMessages(limit = 50)
     * println(historicalMessages.items.toString())
     * ```
     *
     * @param start The start of the time window to query from. See [QueryOptions.start]
     * @param end The end of the time window to query from. See [QueryOptions.end]
     * @param limit The maximum number of messages to return in the response. See [QueryOptions.limit]
     * @returns paginated result of messages, in newest-to-oldest order.
     * Spec: CHA-M5j
     */
    public suspend fun historyBeforeSubscribe(start: Long? = null, end: Long? = null, limit: Int = 100): PaginatedResult<Message>
}

internal class DefaultMessagesSubscription(
    private val chatApi: ChatApi,
    private val roomId: String,
    private val subscription: Subscription,
    internal val fromSerialProvider: () -> CompletableDeferred<String>,
    parentLogger: Logger,
) : MessagesSubscription {
    private val logger = parentLogger.withContext(tag = "DefaultMessagesSubscription")

    override fun unsubscribe() {
        logger.trace("unsubscribe(); roomId=$roomId")
        subscription.unsubscribe()
    }

    override suspend fun historyBeforeSubscribe(start: Long?, end: Long?, limit: Int): PaginatedResult<Message> {
        logger.trace("getPreviousMessages(); roomId=$roomId, start=$start, end=$end, limit=$limit")
        val fromSerial = fromSerialProvider().await()
        val queryOptions = QueryOptions(start = start, end = end, limit = limit, orderBy = NewestFirst)
        return chatApi.getMessages(
            roomId = roomId,
            options = queryOptions,
            fromSerial = fromSerial,
        )
    }
}

internal class DefaultMessages(
    val room: DefaultRoom,
) : Messages, RoomFeature {

    override val featureName: String = "messages"

    private var channelStateListener: ChannelStateListener

    private val logger = room.logger.withContext(tag = "Messages")

    private val roomId = room.roomId

    private val chatApi = room.chatApi

    internal val channelWrapper: RealtimeChannel = room.channel

    @OptIn(InternalAPI::class)
    override val channel: Channel = channelWrapper.javaChannel // CHA-RC3

    private val channelSerialMap = ConcurrentHashMap<PubSubMessageListener, CompletableDeferred<String>>()

    /**
     * deferredChannelSerial is a thread safe reference to the channel serial.
     * Provides common channel serial for all subscribers once discontinuity is detected.
     */
    private var deferredChannelSerial = CompletableDeferred<String>()

    override val reactions: MessagesReactions
        get() = _reactions

    private val _reactions: DefaultMessagesReactions

    init {
        logger.trace("init(); roomId=$roomId, initializing channelStateListener to update channel serials after discontinuity")
        channelStateListener = ChannelStateListener {
            if (it.current == ChannelState.attached && !it.resumed) {
                updateChannelSerialsAfterDiscontinuity(requireAttachSerial())
            }
        }
        @OptIn(InternalAPI::class)
        val internalChannel = channelWrapper.javaChannel
        internalChannel.on(channelStateListener)

        _reactions = DefaultMessagesReactions(
            chatApi = chatApi,
            roomId = roomId,
            channel = internalChannel,
            annotations = internalChannel.annotations,
            options = room.options.messages,
            parentLogger = logger,
        )
    }

    // CHA-M5c, CHA-M5d - Updated channel serial after discontinuity
    private fun updateChannelSerialsAfterDiscontinuity(value: String) {
        logger.debug("updateChannelSerialsAfterDiscontinuity(); roomId=$roomId, serial=$value")
        if (deferredChannelSerial.isActive) {
            deferredChannelSerial.complete(value)
        } else {
            deferredChannelSerial = CompletableDeferred(value)
        }
        // channel serials updated at the same time for all map entries
        channelSerialMap.replaceAll { _, _ -> deferredChannelSerial }
    }

    override fun subscribe(listener: Messages.Listener): MessagesSubscription {
        logger.trace("subscribe(); roomId=$roomId")
        val messageListener = PubSubMessageListener {
            logger.debug("subscribe(); received message for roomId=$roomId", context = mapOf("message" to it.toString()))
            val pubSubMessage = it ?: throw clientError("Got empty pubsub channel message")
            val eventType = messageActionToEventType[pubSubMessage.action]
                ?: throw clientError("Received Unknown message action ${pubSubMessage.action}")

            val data = parsePubSubMessageData(pubSubMessage.data)
            val chatMessage = DefaultMessage(
                roomId = roomId,
                createdAt = pubSubMessage.createdAt,
                clientId = pubSubMessage.clientId,
                serial = pubSubMessage.serial,
                text = data.text,
                metadata = data.metadata ?: MessageMetadata(),
                headers = pubSubMessage.extras.asJsonObject().get("headers")?.toMap() ?: mapOf(),
                action = pubSubMessage.action,
                version = pubSubMessage.version,
                timestamp = pubSubMessage.timestamp,
                operation = pubSubMessage.operation,
            )
            listener.onEvent(DefaultChatMessageEvent(type = eventType, message = chatMessage))
        }
        channelSerialMap[messageListener] = deferredChannelSerial
        // (CHA-M4d)
        val subscription = channelWrapper.subscribe(PubSubEventName.ChatMessage, messageListener)
        logger.debug("subscribe(); roomId=$roomId, subscribed to messages")
        // (CHA-M5) setting subscription point
        if (channelWrapper.state == ChannelState.attached) {
            channelSerialMap[messageListener] = CompletableDeferred(requireChannelSerial())
        }

        return DefaultMessagesSubscription(
            chatApi = chatApi,
            roomId = roomId,
            subscription = {
                channelSerialMap.remove(messageListener)
                subscription.unsubscribe()
            },
            fromSerialProvider = {
                channelSerialMap[messageListener]
                    ?: throw clientError("This messages subscription instance was already unsubscribed")
            },
            parentLogger = logger,
        )
    }

    override suspend fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> {
        logger.trace("get(); roomId=$roomId, start=$start, end=$end, limit=$limit, orderBy=$orderBy")
        return chatApi.getMessages(
            roomId,
            QueryOptions(start, end, limit, orderBy),
        )
    }

    override suspend fun send(text: String, metadata: MessageMetadata?, headers: MessageHeaders?): Message {
        logger.trace(
            "send(); roomId=$roomId, text=$text",
            context = mapOf("metadata" to metadata.toString(), "headers" to headers.toString()),
        )
        return chatApi.sendMessage(
            roomId,
            SendMessageParams(text, metadata, headers),
        )
    }

    override suspend fun update(
        updatedMessage: Message,
        operationDescription: String?,
        operationMetadata: OperationMetadata?,
    ): Message {
        logger.trace(
            "update(); roomId=$roomId, serial=${updatedMessage.serial}",
            context = mapOf("description" to operationDescription.toString(), "metadata" to operationMetadata.toString()),
        )
        return chatApi.updateMessage(
            updatedMessage,
            UpdateMessageParams(
                message = SendMessageParams(updatedMessage.text, updatedMessage.metadata, updatedMessage.headers),
                description = operationDescription,
                metadata = operationMetadata,
            ),
        )
    }

    override suspend fun delete(
        message: Message,
        operationDescription: String?,
        operationMetadata: OperationMetadata?,
    ): Message {
        logger.trace(
            "delete(); roomId=$roomId, serial=${message.serial}",
            context = mapOf("description" to operationDescription.toString(), "metadata" to operationMetadata.toString()),
        )
        return chatApi.deleteMessage(
            message,
            DeleteMessageParams(
                description = operationDescription,
                metadata = operationMetadata,
            ),
        )
    }

    private fun requireChannelSerial(): String {
        return channelWrapper.properties.channelSerial
            ?: throw clientError("Channel has been attached, but channelSerial is not defined")
    }

    private fun requireAttachSerial(): String {
        return channelWrapper.properties.attachSerial
            ?: throw clientError("Channel has been attached, but attachSerial is not defined")
    }

    override fun dispose() {
        logger.trace("release(); roomId=$roomId")
        @OptIn(InternalAPI::class)
        channelWrapper.javaChannel.off(channelStateListener)
        channelSerialMap.clear()
        _reactions.dispose()
    }
}

/**
 * Parsed data from the Pub/Sub channel's message data field
 */
private data class PubSubMessageData(val text: String, val metadata: MessageMetadata?)

private fun parsePubSubMessageData(data: Any): PubSubMessageData {
    if (data !is JsonObject) {
        throw serverError("Unrecognized Pub/Sub channel's message for `Message.created` event")
    }
    return PubSubMessageData(
        text = data.requireString("text"),
        metadata = data.getAsJsonObject("metadata"),
    )
}
