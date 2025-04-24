package com.ably.chat

import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimePresence
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import io.ably.lib.types.MessageExtras
import io.ably.lib.types.PresenceMessage
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
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

/**
 * Takes an existing Ably message and converts it to an ephemeral message by adding
 * the ephemeral flag in the extras field.
 */
internal fun Message.asEphemeralMessage(): Message {
    return apply {
        extras = extras ?: MessageExtras(JsonObject())
        extras.asJsonObject().addProperty("ephemeral", true)
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

/**
 * A custom implementation of a `MutableSharedFlow` that supports optional awaitable emissions.
 *
 * This class allows emitting values with an optional `CompletableDeferred` to track the completion
 * of processing for each emitted value. It is useful in scenarios where you need to ensure that
 * all consumers have processed the emitted values before proceeding.
 *
 * @param T The type of values emitted by the shared flow.
 * @property logger A logger instance used to log errors during collection.
 */
@Suppress("OutdatedDocumentation")
internal class AwaitableSharedFlow<T>(private val logger: Logger) {
    private val sharedFlow = MutableSharedFlow<Pair<T, CompletableDeferred<Unit>?>>(extraBufferCapacity = Channel.UNLIMITED)
    private val completionDeferredList = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()

    /**
     * Emits a value into the shared flow. If `awaitable` is true, the emission is tracked
     * using a `CompletableDeferred` to allow awaiting its completion.
     *
     * @param value The value to emit.
     * @param awaitable Whether the emission should be tracked for completion.
     */
    fun tryEmit(value: T, awaitable: Boolean = false) {
        if (awaitable) {
            val deferred = CompletableDeferred<Unit>()
            completionDeferredList.add(deferred)
            sharedFlow.tryEmit(value to deferred)
        } else {
            sharedFlow.tryEmit(value to null)
        }
    }

    /**
     * Collects values from the shared flow and processes them using the provided block.
     * Logs any exceptions that occur during processing.
     *
     * @param block A suspendable function to process each emitted value.
     */
    suspend fun collect(block: suspend (T) -> Unit) {
        sharedFlow.collect { (value, deferred) ->
            try {
                block(value)
            } catch (e: Exception) {
                logger.error("Exception caught during collection: ${e.message}", e)
            } finally {
                deferred?.complete(Unit)
            }
        }
    }

    /**
     * Awaits the completion of all tracked emissions. This ensures that all consumers
     * have processed the emitted values before proceeding.
     */
    suspend fun await() {
        val deferredList = completionDeferredList.toSet()
        deferredList.awaitAll()
        completionDeferredList.removeAll(deferredList)
    }
}
