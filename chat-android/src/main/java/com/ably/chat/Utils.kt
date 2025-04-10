package com.ably.chat

import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimePresence
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.PresenceMessage
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal suspend fun RealtimeChannel.attachCoroutine() = suspendCancellableCoroutine { continuation ->
    attach(object : CompletionListener {
        override fun onSuccess() {
            continuation.resume(Unit)
        }

        override fun onError(reason: ErrorInfo?) {
            continuation.resumeWithException(AblyException.fromErrorInfo(reason))
        }
    })
}

internal suspend fun RealtimeChannel.detachCoroutine() = suspendCancellableCoroutine { continuation ->
    detach(object : CompletionListener {
        override fun onSuccess() {
            continuation.resume(Unit)
        }

        override fun onError(reason: ErrorInfo?) {
            continuation.resumeWithException(AblyException.fromErrorInfo(reason))
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

            override fun onError(reason: ErrorInfo?) {
                continuation.resumeWithException(AblyException.fromErrorInfo(reason))
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

internal suspend fun RealtimePresence.enterClientCoroutine(clientId: String, data: JsonElement? = JsonNull.INSTANCE) =
    suspendCancellableCoroutine { continuation ->
        enterClient(
            clientId,
            data,
            object : CompletionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(reason: ErrorInfo?) {
                    continuation.resumeWithException(AblyException.fromErrorInfo(reason))
                }
            },
        )
    }

internal suspend fun RealtimePresence.updateClientCoroutine(clientId: String, data: JsonElement? = JsonNull.INSTANCE) =
    suspendCancellableCoroutine { continuation ->
        updateClient(
            clientId,
            data,
            object : CompletionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(reason: ErrorInfo?) {
                    continuation.resumeWithException(AblyException.fromErrorInfo(reason))
                }
            },
        )
    }

internal suspend fun RealtimePresence.leaveClientCoroutine(clientId: String, data: JsonElement? = JsonNull.INSTANCE) =
    suspendCancellableCoroutine { continuation ->
        leaveClient(
            clientId,
            data,
            object : CompletionListener {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onError(reason: ErrorInfo?) {
                    continuation.resumeWithException(AblyException.fromErrorInfo(reason))
                }
            },
        )
    }

internal val RealtimeChannel.errorMessage: String
    get() = if (reason == null) {
        ""
    } else {
        ", ${reason?.message}"
    }

internal val List<String>.joinWithBrackets: String get() = joinToString(prefix = "[", postfix = "]") { it }

@Suppress("FunctionName")
internal fun ChatChannelOptions(init: (ChannelOptions.() -> Unit)? = null): ChannelOptions {
    return ChannelOptions().apply {
        init?.invoke(this)
        // (CHA-M4a)
        attachOnSubscribe = false
    }
}

internal fun generateUUID() = UUID.randomUUID().toString()

internal fun lifeCycleErrorInfo(
    errorMessage: String,
    errorCode: ErrorCode,
) = createErrorInfo(errorMessage, errorCode, HttpStatusCode.BadRequest)

internal fun lifeCycleException(
    errorMessage: String,
    errorCode: ErrorCode,
    cause: Throwable? = null,
): AblyException = createAblyException(lifeCycleErrorInfo(errorMessage, errorCode), cause)

internal fun lifeCycleException(
    errorInfo: ErrorInfo,
    cause: Throwable? = null,
): AblyException = createAblyException(errorInfo, cause)

internal fun roomInvalidStateException(roomId: String, roomStatus: RoomStatus, statusCode: Int) =
    ablyException(
        "Can't perform operation; the room '$roomId' is in an invalid state: $roomStatus",
        ErrorCode.RoomInInvalidState,
        statusCode,
    )

internal fun ablyException(
    errorMessage: String,
    errorCode: ErrorCode,
    statusCode: Int = HttpStatusCode.BadRequest,
    cause: Throwable? = null,
): AblyException {
    val errorInfo = createErrorInfo(errorMessage, errorCode, statusCode)
    return createAblyException(errorInfo, cause)
}

internal fun ablyException(
    errorInfo: ErrorInfo,
    cause: Throwable? = null,
): AblyException = createAblyException(errorInfo, cause)

private fun createErrorInfo(
    errorMessage: String,
    errorCode: ErrorCode,
    statusCode: Int,
) = ErrorInfo(errorMessage, statusCode, errorCode.code)

private fun createAblyException(
    errorInfo: ErrorInfo,
    cause: Throwable?,
) = cause?.let { AblyException.fromErrorInfo(it, errorInfo) }
    ?: AblyException.fromErrorInfo(errorInfo)

internal fun clientError(errorMessage: String) = ablyException(errorMessage, ErrorCode.BadRequest, HttpStatusCode.BadRequest)

internal fun serverError(errorMessage: String) = ablyException(errorMessage, ErrorCode.InternalError, HttpStatusCode.InternalServerError)

internal fun com.ably.Subscription.asChatSubscription(): Subscription = Subscription {
    this.unsubscribe()
}

/**
 * CHA-TM14 - Processes latest job only
 */
internal class LatestJobExecutor {
    /**
     * Mutex to ensure that only one block is processed at a time.
     */
    private val mtx = Mutex()

    /**
     * A reference to the latest waiter. Used to check if the current job is the latest job.
     */
    private val waiter: AtomicReference<Any?> = AtomicReference(null)

    suspend fun run(block: suspend () -> Unit) {
        val self = Any()
        waiter.set(self)
        mtx.withLock {
            if (waiter.get() == self) block()
        }
    }
}
