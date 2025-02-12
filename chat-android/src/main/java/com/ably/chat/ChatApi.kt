@file:Suppress("StringLiteralDuplication")

package com.ably.chat

import com.google.gson.JsonElement
import io.ably.lib.types.AblyException
import io.ably.lib.types.AsyncHttpPaginatedResponse
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.MessageAction
import io.ably.lib.types.Param
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val API_PROTOCOL_VERSION = 3
private const val PROTOCOL_VERSION_PARAM_NAME = "v"
private const val RESERVED_ABLY_CHAT_KEY = "ably-chat"
private val apiProtocolParam = Param(PROTOCOL_VERSION_PARAM_NAME, API_PROTOCOL_VERSION.toString())

internal class ChatApi(
    private val realtimeClient: RealtimeClient,
    private val clientId: String,
    private val logger: Logger,
) {

    /**
     * Get messages from the Chat Backend
     *
     * @return paginated result with messages
     */
    suspend fun getMessages(roomId: String, options: QueryOptions, fromSerial: String? = null): PaginatedResult<Message> {
        val baseParams = options.toParams()
        val params = fromSerial?.let { baseParams + Param("fromSerial", it) } ?: baseParams
        return makeAuthorizedPaginatedRequest(
            url = "/chat/v2/rooms/$roomId/messages",
            method = "GET",
            params = params,
        ) {
            val messageJsonObject = it.requireJsonObject()
            val latestAction = messageJsonObject.get("action")?.asString?.let { name -> messageActionNameToAction[name] }
            val operation = messageJsonObject.getAsJsonObject("operation")
            latestAction?.let { action ->
                Message(
                    serial = messageJsonObject.requireString("serial"),
                    clientId = messageJsonObject.requireString("clientId"),
                    roomId = messageJsonObject.requireString("roomId"),
                    text = messageJsonObject.requireString("text"),
                    createdAt = messageJsonObject.requireLong("createdAt"),
                    metadata = messageJsonObject.getAsJsonObject("metadata"),
                    headers = messageJsonObject.get("headers")?.toMap() ?: mapOf(),
                    action = action,
                    version = messageJsonObject.requireString("version"),
                    timestamp = messageJsonObject.requireLong("timestamp"),
                    operation = toMessageOperation(operation),
                )
            }
        }
    }

    private fun validateSendMessageParams(params: SendMessageParams) {
        // (CHA-M3c)
        if (params.metadata?.has(RESERVED_ABLY_CHAT_KEY) == true) {
            throw ablyException("Metadata contains reserved 'ably-chat' key", ErrorCode.InvalidRequestBody)
        }
        // (CHA-M3d)
        if (params.headers?.keys?.any { it.startsWith(RESERVED_ABLY_CHAT_KEY) } == true) {
            throw ablyException("Headers contains reserved key with reserved 'ably-chat' prefix", ErrorCode.InvalidRequestBody)
        }
    }

    /**
     * Send message to the Chat Backend
     *
     * @return sent message instance
     */
    suspend fun sendMessage(roomId: String, params: SendMessageParams): Message {
        validateSendMessageParams(params)
        val body = params.toJsonObject()

        return makeAuthorizedRequest(
            "/chat/v2/rooms/$roomId/messages",
            "POST",
            body,
        )?.let {
            val serial = it.requireString("serial")
            val createdAt = it.requireLong("createdAt")
            // (CHA-M3a)
            Message(
                serial = serial,
                clientId = clientId,
                roomId = roomId,
                text = params.text,
                createdAt = createdAt,
                metadata = params.metadata,
                headers = params.headers ?: mapOf(),
                action = MessageAction.MESSAGE_CREATE,
                version = serial,
                timestamp = createdAt,
                operation = null,
            )
        } ?: throw serverError("Send message endpoint returned empty value")
    }

    suspend fun updateMessage(message: Message, params: UpdateMessageParams): Message {
        validateSendMessageParams(params.message)
        val body = params.toJsonObject()

        return makeAuthorizedRequest(
            "/chat/v2/rooms/${message.roomId}/messages/${message.serial}",
            "PUT",
            body,
        )?.let {
            val version = it.requireString("version")
            val timestamp = it.requireLong("timestamp")

            Message(
                serial = message.serial,
                clientId = clientId,
                roomId = message.roomId,
                text = params.message.text,
                createdAt = message.createdAt,
                metadata = params.message.metadata,
                headers = params.message.headers ?: mapOf(),
                action = MessageAction.MESSAGE_UPDATE,
                version = version,
                timestamp = timestamp,
                operation = toMessageOperation(clientId, params.description, params.metadata),
            )
        } ?: throw serverError("Update message endpoint returned empty value")
    }

    suspend fun deleteMessage(message: Message, params: DeleteMessageParams): Message {
        val body = params.toJsonObject()

        return makeAuthorizedRequest(
            "/chat/v2/rooms/${message.roomId}/messages/${message.serial}/delete",
            "POST",
            body,
        )?.let {
            val version = it.requireString("version")
            val timestamp = it.requireLong("timestamp")

            Message(
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
                operation = toMessageOperation(clientId, params.description, params.metadata),
            )
        } ?: throw serverError("Delete message endpoint returned empty value")
    }

    /**
     * return occupancy for specified room
     */
    suspend fun getOccupancy(roomId: String): OccupancyEvent {
        return this.makeAuthorizedRequest("/chat/v2/rooms/$roomId/occupancy", "GET")?.let {
            OccupancyEvent(
                connections = it.requireInt("connections"),
                presenceMembers = it.requireInt("presenceMembers"),
            )
        } ?: throw serverError("Occupancy endpoint returned empty value")
    }

    private suspend fun makeAuthorizedRequest(
        url: String,
        method: String,
        body: JsonElement? = null,
    ): JsonElement? = suspendCancellableCoroutine { continuation ->
        val requestBody = body.toRequestBody()
        realtimeClient.requestAsync(
            method,
            url,
            arrayOf(apiProtocolParam),
            requestBody,
            arrayOf(),
            object : AsyncHttpPaginatedResponse.Callback {
                override fun onResponse(response: AsyncHttpPaginatedResponse?) {
                    continuation.resume(response?.items()?.firstOrNull())
                }

                override fun onError(reason: ErrorInfo?) {
                    logger.error(
                        "ChatApi.makeAuthorizedRequest(); failed to make request",
                        staticContext = mapOf(
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
        method: String,
        params: List<Param> = listOf(),
        transform: (JsonElement) -> T?,
    ): PaginatedResult<T> = suspendCancellableCoroutine { continuation ->
        realtimeClient.requestAsync(
            method,
            url,
            (params + apiProtocolParam).toTypedArray(),
            null,
            arrayOf(),
            object : AsyncHttpPaginatedResponse.Callback {
                override fun onResponse(response: AsyncHttpPaginatedResponse?) {
                    continuation.resume(response.toPaginatedResult(transform))
                }

                override fun onError(reason: ErrorInfo?) {
                    logger.error(
                        "ChatApi.makeAuthorizedPaginatedRequest(); failed to make request",
                        staticContext = mapOf(
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
