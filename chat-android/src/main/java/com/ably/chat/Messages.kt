@file:Suppress("StringLiteralDuplication")

package com.ably.chat

import com.ably.chat.OrderBy.NewestFirst
import com.google.gson.JsonObject
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import io.ably.lib.realtime.Channel as AblyRealtimeChannel

internal typealias PubSubMessageListener = AblyRealtimeChannel.MessageListener
internal typealias PubSubMessage = io.ably.lib.types.Message

/**
 * This interface is used to interact with messages in a chat room: subscribing
 * to new messages, fetching history, or sending messages.
 *
 * Get an instance via [Room.messages].
 */
interface Messages : EmitsDiscontinuities {
    /**
     * Get the underlying Ably realtime channel used for the messages in this chat room.
     *
     * @returns the realtime channel
     */
    val channel: AblyRealtimeChannel

    /**
     * Subscribe to new messages in this chat room.
     * @param listener callback that will be called
     * @return A response object that allows you to control the subscription.
     */
    fun subscribe(listener: Listener): MessagesSubscription

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
    suspend fun get(
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
    suspend fun send(text: String, metadata: MessageMetadata? = null, headers: MessageHeaders? = null): Message

    /**
     * Update a message in the chat room.
     *
     * This method uses the Ably Chat API REST endpoint for updating messages.
     * It creates a new message with the same serial and a new version.
     * The original message is not modified.
     * Spec: CHA-M8
     *
     * @param message The message to update.
     * @param text The new text of the message.
     * @param opDescription Optional description for the update action.
     * @param opMetadata Optional metadata for the update action.
     * @param metadata Optional metadata of the message.
     * @param headers Optional headers of the message.
     * @returns updated message.
     */
    suspend fun update(
        message: Message,
        text: String,
        opDescription: String? = null,
        opMetadata: OperationMetadata? = null,
        metadata: MessageMetadata? = null,
        headers: MessageHeaders? = null,
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
     * @param opDescription - Optional description for the delete action.
     * @param opMetadata - Optional metadata for the delete action.
     * @return A promise that resolves to the deleted message.
     */
    suspend fun delete(message: Message, opDescription: String? = null, opMetadata: OperationMetadata? = null): Message

    /**
     * An interface for listening to new messaging event
     */
    fun interface Listener {
        /**
         * A function that can be called when the new messaging event happens.
         * @param event The event that happened.
         */
        fun onEvent(event: MessageEvent)
    }
}

/**
 * Options for querying messages in a chat room.
 */
data class QueryOptions(
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
data class MessageEvent(
    /**
     * The type of the message event.
     */
    val type: MessageEventType,

    /**
     * The message that was received.
     */
    val message: Message,
)

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
        description?.let { addProperty("description", it) }
        metadata?.let { add("metadata", it.toJson()) }
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
        description?.let { addProperty("description", it) }
        metadata?.let { add("metadata", it.toJson()) }
    }
}

interface MessagesSubscription : Subscription {
    /**
     * (CHA-M5j)
     * Get the previous messages that were sent to the room before the listener was subscribed.
     * @return paginated result of messages, in newest-to-oldest order.
     */
    suspend fun getPreviousMessages(start: Long? = null, end: Long? = null, limit: Int = 100): PaginatedResult<Message>
}

internal class DefaultMessagesSubscription(
    private val chatApi: ChatApi,
    private val roomId: String,
    private val subscription: Subscription,
    internal val fromSerialProvider: () -> CompletableDeferred<String>,
) : MessagesSubscription {
    override fun unsubscribe() {
        subscription.unsubscribe()
    }

    override suspend fun getPreviousMessages(start: Long?, end: Long?, limit: Int): PaginatedResult<Message> {
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
) : Messages, ContributesToRoomLifecycleImpl(room.logger) {

    override val featureName: String = "messages"

    private var channelStateListener: ChannelStateListener

    private val logger = room.logger.withContext(tag = "Messages")

    private val roomId = room.roomId

    private val chatApi = room.chatApi

    private val realtimeChannels = room.realtimeClient.channels

    /**
     * (CHA-M1)
     * the channel name for the chat messages channel.
     */
    private val messagesChannelName = "${room.roomId}::\$chat::\$chatMessages"

    override val channel: Channel = realtimeChannels.get(messagesChannelName, room.options.messagesChannelOptions()) // CHA-RC2f

    override val attachmentErrorCode: ErrorCode = ErrorCode.MessagesAttachmentFailed

    override val detachmentErrorCode: ErrorCode = ErrorCode.MessagesDetachmentFailed

    private val channelSerialMap = ConcurrentHashMap<PubSubMessageListener, CompletableDeferred<String>>()

    /**
     * deferredChannelSerial is a thread safe reference to the channel serial.
     * Provides common channel serial for all subscribers once discontinuity is detected.
     */
    private var deferredChannelSerial = CompletableDeferred<String>()

    init {
        channelStateListener = ChannelStateListener {
            if (it.current == ChannelState.attached && !it.resumed) {
                updateChannelSerialsAfterDiscontinuity(requireAttachSerial())
            }
        }
        channel.on(channelStateListener)
    }

    // CHA-M5c, CHA-M5d - Updated channel serial after discontinuity
    private fun updateChannelSerialsAfterDiscontinuity(value: String) {
        if (deferredChannelSerial.isActive) {
            deferredChannelSerial.complete(value)
        } else {
            deferredChannelSerial = CompletableDeferred(value)
        }
        // channel serials updated at the same time for all map entries
        channelSerialMap.replaceAll { _, _ -> deferredChannelSerial }
    }

    override fun subscribe(listener: Messages.Listener): MessagesSubscription {
        val messageListener = PubSubMessageListener {
            val pubSubMessage = it ?: throw clientError("Got empty pubsub channel message")
            val eventType = messageActionToEventType[pubSubMessage.action]
                ?: throw clientError("Received Unknown message action ${pubSubMessage.action}")

            val data = parsePubSubMessageData(pubSubMessage.data)
            val chatMessage = Message(
                roomId = roomId,
                createdAt = pubSubMessage.createdAt,
                clientId = pubSubMessage.clientId,
                serial = pubSubMessage.serial,
                text = data.text,
                metadata = data.metadata,
                headers = pubSubMessage.extras.asJsonObject().get("headers")?.toMap() ?: mapOf(),
                action = pubSubMessage.action,
                version = pubSubMessage.version,
                timestamp = pubSubMessage.timestamp,
                operation = pubSubMessage.operation,
            )
            listener.onEvent(MessageEvent(type = eventType, message = chatMessage))
        }
        channelSerialMap[messageListener] = deferredChannelSerial
        // (CHA-M4d)
        channel.subscribe(PubSubEventName.CHAT_MESSAGE, messageListener)
        // (CHA-M5) setting subscription point
        if (channel.state == ChannelState.attached) {
            channelSerialMap[messageListener] = CompletableDeferred(requireChannelSerial())
        }

        return DefaultMessagesSubscription(
            chatApi = chatApi,
            roomId = roomId,
            subscription = {
                channelSerialMap.remove(messageListener)
                channel.unsubscribe(PubSubEventName.CHAT_MESSAGE, messageListener)
            },
            fromSerialProvider = {
                channelSerialMap[messageListener]
                    ?: throw clientError("This messages subscription instance was already unsubscribed")
            },
        )
    }

    override suspend fun get(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> =
        chatApi.getMessages(
            roomId,
            QueryOptions(start, end, limit, orderBy),
        )

    override suspend fun send(text: String, metadata: MessageMetadata?, headers: MessageHeaders?): Message =
        chatApi.sendMessage(
            roomId,
            SendMessageParams(text, metadata, headers),
        )

    override suspend fun update(
        message: Message,
        text: String,
        opDescription: String?,
        opMetadata: OperationMetadata?,
        metadata: MessageMetadata?,
        headers: MessageHeaders?,
    ): Message = chatApi.updateMessage(
        message,
        UpdateMessageParams(
            message = SendMessageParams(text, metadata, headers),
            description = opDescription,
            metadata = opMetadata,
        ),
    )

    override suspend fun delete(message: Message, opDescription: String?, opMetadata: OperationMetadata?): Message =
        chatApi.deleteMessage(
            message,
            DeleteMessageParams(
                description = opDescription,
                metadata = opMetadata,
            ),
        )

    private fun requireChannelSerial(): String {
        return channel.properties.channelSerial
            ?: throw clientError("Channel has been attached, but channelSerial is not defined")
    }

    private fun requireAttachSerial(): String {
        return channel.properties.attachSerial
            ?: throw clientError("Channel has been attached, but attachSerial is not defined")
    }

    override fun release() {
        channel.off(channelStateListener)
        channelSerialMap.clear()
        realtimeChannels.release(channel.name)
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
