package com.ably.chat

import com.ably.chat.json.JsonObject
import com.ably.chat.json.JsonValue
import com.ably.chat.json.jsonObject
import com.ably.http.HttpMethod
import com.ably.pubsub.RealtimeClient
import io.ably.lib.types.AsyncHttpPaginatedResponse
import io.ably.lib.types.Param
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import io.ably.lib.types.ErrorInfo as PubSubErrorInfo

private const val PROTOCOL_VERSION_PARAM_NAME = "v"

private const val PUB_SUB_PROTOCOL_VERSION = 4
private val pubSubProtocolParam = Param(PROTOCOL_VERSION_PARAM_NAME, PUB_SUB_PROTOCOL_VERSION.toString())

private const val CHAT_PROTOCOL_VERSION = 4
private const val CHAT_API_PROTOCOL_VERSION = PROTOCOL_VERSION_PARAM_NAME + CHAT_PROTOCOL_VERSION

internal class ChatApi(
    private val realtimeClient: RealtimeClient,
    parentLogger: Logger,
) {
    private val logger = parentLogger.withContext(tag = "ChatApi")

    /**
     * Get messages from the Chat Backend
     *
     * @return paginated result with messages
     */
    suspend fun getMessages(roomName: String, options: QueryOptions, fromSerial: String? = null): PaginatedResult<Message> {
        logger.trace(
            "getMessages()",
            context = mapOf("roomName" to roomName, "options" to options, "fromSerial" to fromSerial),
        )
        val baseParams = options.toParams()
        val params = fromSerial?.let { baseParams + Param("fromSerial", it) } ?: baseParams
        return makeAuthorizedPaginatedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages",
            method = HttpMethod.Get,
            params = params,
        ) { tryParseMessageResponse(it) }
    }

    /**
     * Spec: CHA-M3
     */
    suspend fun sendMessage(roomName: String, params: SendMessageParams): Message {
        logger.trace("sendMessage()", context = mapOf("roomName" to roomName, "params" to params))
        val body = params.toJsonObject() // CHA-M3b

        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages",
            HttpMethod.Post,
            body,
        )?.let {
            tryParseMessageResponse(it)
        } ?: throw serverError("unable to send message; server returned empty response") // CHA-M3e
    }

    /**
     * Spec: CHA-M8
     */
    suspend fun updateMessage(roomName: String, serial: String, params: UpdateMessageParams): Message {
        logger.trace("updateMessage()", context = mapOf("message" to serial, "params" to params))
        val body = params.toJsonObject()
        // CHA-M8c
        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages/${encodePath(serial)}",
            HttpMethod.Put,
            body,
        )?.let {
            tryParseMessageResponse(it)
        } ?: throw serverError("unable to update message; server returned empty response") // CHA-M8d
    }

    /**
     * Spec: CHA-M9
     */
    suspend fun deleteMessage(roomName: String, serial: String, params: DeleteMessageParams): Message {
        logger.trace("deleteMessage()", context = mapOf("message" to serial, "params" to params))
        val body = params.toJsonObject()

        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages/${encodePath(serial)}/delete",
            HttpMethod.Post,
            body,
        )?.let {
            tryParseMessageResponse(it)
        } ?: throw serverError("unable to delete message; server returned empty response") // CHA-M9c
    }

    private fun tryParseMessageResponse(json: JsonValue): Message? {
        val messageJsonObject = json.jsonObjectOrNull() ?: return null
        val action = messageJsonObject[MessageProperty.Action]
            ?.stringOrNull()?.let { name -> messageActionNameToAction[name] } ?: MessageAction.MessageCreate
        val version = messageJsonObject[MessageProperty.Version]?.jsonObjectOrNull()
        val reactions = messageJsonObject[MessageProperty.Reactions]?.jsonObjectOrNull()
        val text = messageJsonObject[MessageProperty.Text]?.stringOrNull() ?: ""
        val messageSerial = messageJsonObject[MessageProperty.Serial]?.stringOrNull() ?: return null
        val messageClientId = messageJsonObject[MessageProperty.ClientId]?.stringOrNull() ?: return null
        val messageTimestamp = messageJsonObject[MessageProperty.Timestamp]?.longOrNull() ?: return null

        return DefaultMessage(
            serial = messageSerial,
            clientId = messageClientId,
            text = text,
            timestamp = messageTimestamp,
            metadata = messageJsonObject[MessageProperty.Metadata]?.jsonObjectOrNull() ?: JsonObject(),
            headers = messageJsonObject.get(MessageProperty.Headers)?.toMap() ?: mapOf(),
            action = action,
            reactions = buildMessageReactions(reactions),
            version = DefaultMessageVersion(
                serial = version?.get(MessageVersionProperty.Serial)?.stringOrNull() ?: messageSerial,
                timestamp = version?.get(MessageVersionProperty.Timestamp)?.longOrNull() ?: messageTimestamp,
                clientId = version?.get(MessageVersionProperty.ClientId)?.stringOrNull(),
                description = version?.get(MessageVersionProperty.Description)?.stringOrNull(),
                metadata = version?.get(MessageVersionProperty.Metadata)?.toMap(),
            ),
        )
    }

    /**
     * return occupancy for specified room
     */
    suspend fun getOccupancy(roomName: String): OccupancyData {
        logger.trace("getOccupancy()", context = mapOf("roomName" to roomName))
        return this.makeAuthorizedRequest("/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/occupancy", HttpMethod.Get)?.let {
            logger.debug("getOccupancy()", context = mapOf("roomName" to roomName, "response" to it))
            DefaultOccupancyData(
                connections = it.getOrNull("connections")?.intOrNull() ?: 0,
                presenceMembers = it.getOrNull("presenceMembers")?.intOrNull() ?: 0,
            )
        } ?: throw serverError("unable to get occupancy; server returned empty response")
    }

    suspend fun sendMessageReaction(roomName: String, messageSerial: String, type: MessageReactionType, name: String, count: Int = 1) {
        this.makeAuthorizedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages/${encodePath(messageSerial)}/reactions",
            method = HttpMethod.Post,
            body = buildMessageReactionsBody(type, name, count),
        )
    }

    suspend fun deleteMessageReaction(roomName: String, messageSerial: String, type: MessageReactionType, name: String? = null) {
        this.makeAuthorizedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages/${encodePath(messageSerial)}/reactions",
            method = HttpMethod.Delete,
            params = buildMessageReactionsApiParams(type, name),
        )
    }

    suspend fun getMessage(roomName: String, serial: String): Message {
        return makeAuthorizedRequest(
            "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages/${encodePath(serial)}",
            HttpMethod.Get,
        )?.let {
            tryParseMessageResponse(it)
        } ?: throw serverError("unable to get message; server returned empty response")
    }

    suspend fun getClientReactions(roomName: String, messageSerial: String, clientId: String?): MessageReactionSummary {
        val response = this.makeAuthorizedRequest(
            url = "/chat/$CHAT_API_PROTOCOL_VERSION/rooms/${encodePath(roomName)}/messages/${encodePath(messageSerial)}/client-reactions",
            method = HttpMethod.Get,
            params = if (clientId != null) listOf(Param("forClientId", clientId)) else listOf(),
        )?.jsonObjectOrNull()
        return buildMessageReactions(response)
    }

    private fun buildMessageReactionsApiParams(type: MessageReactionType, name: String? = null): List<Param> = buildList {
        add(Param("type", type.type))
        name?.let { add(Param("name", it)) }
    }

    private fun buildMessageReactionsBody(type: MessageReactionType, name: String, count: Int = 1) = jsonObject {
        put("type", type.type)
        put("name", name)
        if (type == MessageReactionType.Multiple) {
            put("count", count)
        }
    }

    private suspend fun makeAuthorizedRequest(
        url: String,
        method: HttpMethod,
        body: JsonValue? = null,
        params: List<Param> = listOf(),
    ): JsonValue? = suspendCancellableCoroutine { continuation ->
        val requestBody = body.toRequestBody()
        realtimeClient.requestAsync(
            path = url,
            method = method,
            params = listOf(pubSubProtocolParam) + params,
            body = requestBody,
            headers = listOf(),
            callback = object : AsyncHttpPaginatedResponse.Callback {
                override fun onResponse(response: AsyncHttpPaginatedResponse?) {
                    continuation.resume(response?.items()?.firstOrNull()?.tryAsJsonValue())
                }

                override fun onError(reason: PubSubErrorInfo?) {
                    logger.error(
                        "ChatApi.makeAuthorizedRequest(); failed to make request",
                        context = mapOf(
                            "url" to url,
                            "statusCode" to reason?.statusCode,
                            "errorCode" to reason?.code,
                            "errorMessage" to reason?.message,
                        ),
                    )
                    // (CHA-M3e)
                    continuation.resumeWithException(ChatException(reason))
                }
            },
        )
    }

    private suspend fun <T> makeAuthorizedPaginatedRequest(
        url: String,
        method: HttpMethod,
        params: List<Param> = listOf(),
        transform: (JsonValue) -> T?,
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

                override fun onError(reason: PubSubErrorInfo?) {
                    logger.error(
                        "ChatApi.makeAuthorizedPaginatedRequest(); failed to make request",
                        context = mapOf(
                            "url" to url,
                            "statusCode" to reason?.statusCode,
                            "errorCode" to reason?.code,
                            "errorMessage" to reason?.message,
                        ),
                    )
                    continuation.resumeWithException(ChatException(reason))
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

internal fun encodePath(path: String): String = URLEncoder.encode(path, Charsets.UTF_8.name())
    .replace("+", "%20")
