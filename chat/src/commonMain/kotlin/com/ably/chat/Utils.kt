package com.ably.chat

import com.ably.chat.json.jsonObject
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.Message
import io.ably.lib.types.MessageExtras
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        extras = extras ?: MessageExtras(jsonObject {}.toGson().asJsonObject)
        extras.asJsonObject().addProperty("ephemeral", true)
    }
}

internal fun generateUUID() = UUID.randomUUID().toString()

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
 * A custom Channel implementation that provides completion tracking for emitted values.
 * It ensures there is only one active collector processing the events at a time.
 *
 * This implementation is useful when you need to:
 * - Track when values are processed by the collector
 * - Ensure single consumer pattern
 * - Handle exceptions gracefully with logging
 */
internal class AwaitableChannel<T>(private val logger: Logger) {
    private val channel = Channel<Pair<T, CompletableDeferred<Unit>>>(Channel.UNLIMITED)
    private val activeCollector = AtomicBoolean(false)

    /**
     * Sends a value to the channel with completion tracking.
     * Returns a [CompletableDeferred] that completes when the value is processed by the collector.
     *
     * @param value The value to send through the channel
     * @return [CompletableDeferred] that completes when the value is processed
     */
    fun sendWithCompletion(value: T): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        channel.trySend(value to deferred)
        return deferred
    }

    /**
     * Collects and processes values from the channel.
     * Only one collector can be active at a time.
     * Automatically completes the deferred for each processed value.
     *
     * @param block The processing function to execute for each value
     * @throws ChatException if multiple collectors attempt to collect simultaneously
     */
    suspend fun collect(block: suspend (T) -> Unit) {
        if (!activeCollector.compareAndSet(false, true)) {
            throw clientError("only one collector is allowed to process events")
        }
        for ((value, deferred) in channel) {
            try {
                block(value)
            } catch (e: Exception) {
                logger.error("Exception caught during collection: ${e.message}", e)
            } finally {
                deferred.complete(Unit)
            }
        }
    }

    /**
     * Closes the channel and releases resources.
     * After disposal, no new values can be sent.
     */
    fun dispose() {
        channel.close()
    }
}
