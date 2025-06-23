package com.ably.chat

import com.ably.http.HttpMethod
import com.ably.pubsub.RealtimeClient
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ably.lib.types.AblyException
import io.ably.lib.types.AsyncHttpPaginatedResponse
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.MessageAction
import io.ably.lib.types.Param
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val PROTOCOL_VERSION_PARAM_NAME = "v"

private const val PUB_SUB_PROTOCOL_VERSION = 3
private val pubSubProtocolParam = Param(PROTOCOL_VERSION_PARAM_NAME, PUB_SUB_PROTOCOL_VERSION.toString())

private const val CHAT_PROTOCOL_VERSION = 3
private const val CHAT_API_PROTOCOL_VERSION = PROTOCOL_VERSION_PARAM_NAME + CHAT_PROTOCOL_VERSION

internal class ChatApi(
    private val realtimeClient: RealtimeClient,
    private val clientId: String,
    parentLogger: Logger,
) {
    private val logger = parentLogger.withContext(tag = "ChatApi")

    /**
     * Get messages from the Chat Backend
     *
     * @return paginated result with messages
     */
    suspend fun getMessages(roomId: String, options: QueryOptions, fromSerial: String? = null): PaginatedResult<Message> {
        logger.trace(
            "getMessages();",
            context = mapOf("roomId" to roomId, "options" to options.toString(), "fromSerial" to fromSerial.toString()),
        )
        val baseParams = options.toParams()
        val params = fromSerial?.let { baseParams + Param("fromSerial", it) } ?: baseParams
        return makeAuthorizedPaginatedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/$roomId/messages",
            method = HttpMethod.Get,
            params = params,
        ) {
            val messageJsonObject = it.requireJsonObject()
            val latestAction = messageJsonObject.get(MessageProperty.Action)?.asString?.let { name -> messageActionNameToAction[name] }
            val operation = messageJsonObject.getAsJsonObject(MessageProperty.Operation)
            val reactions = messageJsonObject.getAsJsonObject(MessageProperty.Reactions)
            latestAction?.let { action ->
                logger.debug("getMessages();", context = mapOf("roomId" to roomId, "message" to messageJsonObject.toString()))
                DefaultMessage(
                    serial = messageJsonObject.requireString(MessageProperty.Serial),
                    clientId = messageJsonObject.requireString(MessageProperty.ClientId),
                    roomId = messageJsonObject.requireString(MessageProperty.RoomId),
                    text = messageJsonObject.requireString(MessageProperty.Text),
                    createdAt = messageJsonObject.requireLong(MessageProperty.CreatedAt),
                    metadata = messageJsonObject.getAsJsonObject(MessageProperty.Metadata) ?: MessageMetadata(),
                    headers = messageJsonObject.get(MessageProperty.Headers)?.toMap() ?: mapOf(),
                    action = action,
                    version = messageJsonObject.requireString(MessageProperty.Version),
                    timestamp = messageJsonObject.requireLong(MessageProperty.Timestamp),
                    reactions = buildMessageReactions(reactions),
                    operation = buildMessageOperation(operation),
                )
            }
        }
    }

    /**
     * Spec: CHA-M3
     */
    suspend fun sendMessage(roomId: String, params: SendMessageParams): Message {
        logger.trace("sendMessage();", context = mapOf("roomId" to roomId, "params" to params.toString()))
        val body = params.toJsonObject() // CHA-M3b

        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/$roomId/messages",
            HttpMethod.Post,
            body,
        )?.let {
            val serial = it.requireString(MessageProperty.Serial)
            val createdAt = it.requireLong(MessageProperty.CreatedAt)
            logger.debug("sendMessage();", context = mapOf("roomId" to roomId, "response" to it.toString()))
            // CHA-M3a
            DefaultMessage(
                serial = serial,
                clientId = clientId,
                roomId = roomId,
                text = params.text,
                createdAt = createdAt,
                metadata = params.metadata ?: MessageMetadata(),
                headers = params.headers ?: mapOf(),
                action = MessageAction.MESSAGE_CREATE,
                version = serial,
                timestamp = createdAt,
                operation = null,
            )
        } ?: throw serverError("Send message endpoint returned empty value") // CHA-M3e
    }

    /**
     * Spec: CHA-M8
     */
    suspend fun updateMessage(message: Message, params: UpdateMessageParams): Message {
        logger.trace("updateMessage();", context = mapOf("message" to message.toString(), "params" to params.toString()))
        val body = params.toJsonObject()
        // CHA-M8c
        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${message.roomId}/messages/${message.serial}",
            HttpMethod.Put,
            body,
        )?.let {
            val version = it.requireString(MessageProperty.Version)
            val timestamp = it.requireLong(MessageProperty.Timestamp)
            logger.debug("updateMessage();", context = mapOf("messageSerial" to message.serial, "response" to it.toString()))
            // CHA-M8b
            DefaultMessage(
                serial = message.serial,
                clientId = clientId,
                roomId = message.roomId,
                text = params.message.text,
                createdAt = message.createdAt,
                metadata = params.message.metadata ?: MessageMetadata(),
                headers = params.message.headers ?: mapOf(),
                action = MessageAction.MESSAGE_UPDATE,
                version = version,
                timestamp = timestamp,
                operation = buildMessageOperation(clientId, params.description, params.metadata),
            )
        } ?: throw serverError("Update message endpoint returned empty value") // CHA-M8d
    }

    /**
     * Spec: CHA-M9
     */
    suspend fun deleteMessage(message: Message, params: DeleteMessageParams): Message {
        logger.trace("deleteMessage();", context = mapOf("message" to message.toString(), "params" to params.toString()))
        val body = params.toJsonObject()

        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${message.roomId}/messages/${message.serial}/delete",
            HttpMethod.Post,
            body,
        )?.let {
            val version = it.requireString(MessageProperty.Version)
            val timestamp = it.requireLong(MessageProperty.Timestamp)
            logger.debug("deleteMessage();", context = mapOf("messageSerial" to message.serial, "response" to it.toString()))
            // CHA-M9b
            DefaultMessage(
                serial = message.serial,
                clientId = clientId,
                roomId = message.roomId,
                text = message.text,
                createdAt = message.createdAt,
                metadata = message.metadata,
                headers = message.headers,
                action = MessageAction.MESSAGE_DELETE,
                version = version,
                timestamp = timestamp,
                operation = buildMessageOperation(clientId, params.description, params.metadata),
            )
        } ?: throw serverError("Delete message endpoint returned empty value") // CHA-M9c
    }

    /**
     * return occupancy for specified room
     */
    suspend fun getOccupancy(roomId: String): OccupancyEvent {
        logger.trace("getOccupancy();", context = mapOf("roomId" to roomId))
        return this.makeAuthorizedRequest("/chat/$CHAT_API_PROTOCOL_VERSION/rooms/$roomId/occupancy", HttpMethod.Get)?.let {
            logger.debug("getOccupancy();", context = mapOf("roomId" to roomId, "response" to it.toString()))
            DefaultOccupancyEvent(
                connections = it.requireInt("connections"),
                presenceMembers = it.requireInt("presenceMembers"),
            )
        } ?: throw serverError("Occupancy endpoint returned empty value")
    }

    suspend fun sendMessageReaction(roomId: String, messageSerial: String, type: MessageReactionType, name: String, count: Int = 1) {
        this.makeAuthorizedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/$roomId/messages/$messageSerial/reactions",
            method = HttpMethod.Post,
            body = buildMessageReactionsBody(type, name, count),
        )
    }

    suspend fun deleteMessageReaction(roomId: String, messageSerial: String, type: MessageReactionType, name: String? = null) {
        this.makeAuthorizedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/$roomId/messages/$messageSerial/reactions",
            method = HttpMethod.Delete,
            params = buildMessageReactionsApiParams(type, name),
        )
    }

    private fun buildMessageReactionsApiParams(type: MessageReactionType, name: String? = null): List<Param> = buildList {
        add(Param("type", type.type))
        name?.let { add(Param("name", it)) }
    }

    private fun buildMessageReactionsBody(type: MessageReactionType, name: String, count: Int = 1): JsonObject = JsonObject().apply {
        addProperty("type", type.type)
        addProperty("name", name)
        if (type == MessageReactionType.Multiple) {
            addProperty("count", count)
        }
    }

    private suspend fun makeAuthorizedRequest(
        url: String,
        method: HttpMethod,
        body: JsonElement? = null,
        params: List<Param> = listOf(),
    ): JsonElement? = suspendCancellableCoroutine { continuation ->
        val requestBody = body.toRequestBody()
        realtimeClient.requestAsync(
            path = url,
            method = method,
            params = listOf(pubSubProtocolParam) + params,
            body = requestBody,
            headers = listOf(),
            callback = object : AsyncHttpPaginatedResponse.Callback {
                override fun onResponse(response: AsyncHttpPaginatedResponse?) {
                    continuation.resume(response?.items()?.firstOrNull())
                }

                override fun onError(reason: ErrorInfo?) {
                    logger.error(
                        "ChatApi.makeAuthorizedRequest(); failed to make request",
                        context = mapOf(
                            "url" to url,
                            "statusCode" to reason?.statusCode.toString(),
                            "errorCode" to reason?.code.toString(),
                            "errorMessage" to reason?.message.toString(),
                        ),
                    )
                    // (CHA-M3e)
                    continuation.resumeWithException(AblyException.fromErrorInfo(reason))
                }
            },
        )
    }

    private suspend fun <T> makeAuthorizedPaginatedRequest(
        url: String,
        method: HttpMethod,
        params: List<Param> = listOf(),
        transform: (JsonElement) -> T?,
    ): PaginatedResult<T> = suspendCancellableCoroutine { continuation ->
        realtimeClient.requestAsync(
            method = method,
            path = url,
            params = params + pubSubProtocolParam,
            body = null,
            headers = listOf(),
            callback = object : AsyncHttpPaginatedResponse.Callback {
                override fun onResponse(response: AsyncHttpPaginatedResponse?) {
                    continuation.resume(response.toPaginatedResult(transform))
                }

                override fun onError(reason: ErrorInfo?) {
                    logger.error(
                        "ChatApi.makeAuthorizedPaginatedRequest(); failed to make request",
                        context = mapOf(
                            "url" to url,
                            "statusCode" to reason?.statusCode.toString(),
                            "errorCode" to reason?.code.toString(),
                            "errorMessage" to reason?.message.toString(),
                        ),
                    )
                    continuation.resumeWithException(AblyException.fromErrorInfo(reason))
                }
            },
        )
    }
}

private fun QueryOptions.toParams() = buildList {
    start?.let { add(Param("start", it)) }
    end?.let { add(Param("end", it)) }
    add(Param("limit", limit))
    add(
        Param(
            "direction",
            when (orderBy) {
                OrderBy.NewestFirst -> "backwards"
                OrderBy.OldestFirst -> "forwards"
            },
        ),
    )
}
