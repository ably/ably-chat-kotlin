package com.ably.chat

import com.ably.chat.json.JsonObject
import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimePresence
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.PresenceMessage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import io.ably.lib.types.ErrorInfo as PubSubErrorInfo

internal suspend fun RealtimeChannel.attachCoroutine() = suspendCancellableCoroutine { continuation ->
    attach(object : CompletionListener {
        override fun onSuccess() {
            continuation.resume(Unit)
        }

        override fun onError(reason: PubSubErrorInfo?) {
            continuation.resumeWithException(ChatException(reason))
        }
    })
}

internal suspend fun RealtimeChannel.detachCoroutine() = suspendCancellableCoroutine { continuation ->
    detach(object : CompletionListener {
        override fun onSuccess() {
            continuation.resume(Unit)
        }

        override fun onError(reason: PubSubErrorInfo?) {
            continuation.resumeWithException(ChatException(reason))
        }
    })
}

internal suspend fun RealtimeChannel.publishCoroutine(message: PubSubMessage) = suspendCancellableCoroutine { continuation ->
    publish(
        message,
        object : CompletionListener {
            override fun onSuccess() {
                continuation.resume(Unit)
            }

            override fun onError(reason: PubSubErrorInfo?) {
                continuation.resumeWithException(ChatException(reason))
            }
        },
    )
}

internal suspend fun RealtimePresence.getCoroutine(
    waitForSync: Boolean = true,
    clientId: String? = null,
    connectionId: String? = null,
): List<PresenceMessage> = withContext(Dispatchers.IO) {
    get(waitForSync = waitForSync, clientId = clientId, connectionId = connectionId)
}

internal suspend fun RealtimePresence.enterClientCoroutine(clientId: String, data: JsonObject?) =
    suspendCancellableCoroutine { continuation ->
        enterClient(
            clientId,
            data?.toGson(),
            object : CompletionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(reason: PubSubErrorInfo?) {
                    continuation.resumeWithException(ChatException(reason))
                }
            },
        )
    }

internal suspend fun RealtimePresence.updateClientCoroutine(clientId: String, data: JsonObject?) =
    suspendCancellableCoroutine { continuation ->
        updateClient(
            clientId,
            data?.toGson(),
            object : CompletionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(reason: PubSubErrorInfo?) {
                    continuation.resumeWithException(ChatException(reason))
                }
            },
        )
    }

internal suspend fun RealtimePresence.leaveClientCoroutine(clientId: String, data: JsonObject?) =
    suspendCancellableCoroutine { continuation ->
        leaveClient(
            clientId,
            data?.toGson(),
            object : CompletionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(reason: PubSubErrorInfo?) {
                    continuation.resumeWithException(ChatException(reason))
                }
            },
        )
    }
