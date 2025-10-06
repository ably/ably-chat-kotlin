package com.ably.chat

import com.ably.pubsub.RealtimeClient

/**
 * Resolves the client ID for Chat SDK operations. Most of the Chat operations require a client ID,
 * but client IDs can be not available until the client has been authenticated if we use Token-based Auth.
 * This class encapsulates the logic for retrieving fresh client ID directly from the [RealtimeClient].
 */
internal class ClientIdResolver(private val realtimeClient: RealtimeClient) {
    fun get(): String = realtimeClient.auth.clientId ?: throw chatException(
        errorMessage = "invalid client id",
        errorCode = ErrorCode.InvalidClientId,
        statusCode = HttpStatusCode.BadRequest,
    )
}
