package com.ably.chat

import java.util.TreeSet
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Kotlin Emitter interface for supplied value
 * Spec: RTE1
 */
internal interface Emitter<V> {
    fun emit(value: V)
    fun on(block: suspend CoroutineScope.(V) -> Unit): Subscription
    fun once(block: suspend CoroutineScope.(V) -> Unit): Subscription
    fun offAll()
}

/**
 * ScopedEmitter is a thread-safe, non-blocking emitter implementation for Kotlin.
 * It ensures that all subscribers receive events asynchronously in the same order under given scope.
 *
 * @param V The type of value to be emitted.
 * @param subscriberScope The CoroutineScope in which the subscribers will run. Defaults to Dispatchers.Default.
 * @param logger An optional logger for logging errors during event processing.
 */
internal class ScopedEmitter<V> (
    private val subscriberScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val logger: Logger? = null,
) : Emitter<V> {

    // Sorted list of unique subscribers based on supplied block
    private val subscribers = TreeSet<AsyncSubscriber<V>>()

    // Emitter scope to make sure all subscribers receive events in same order.
    // Will be automatically garbage collected once all jobs are performed.
    private val sequentialScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    val finishedProcessing: Boolean
        get() = subscribers.all { it.values.isEmpty() && !it.isSubscriberRunning }

    @get:Synchronized
    val subscribersCount: Int
        get() = subscribers.size

    @Synchronized
    override fun emit(value: V) {
        for (subscriber in subscribers.toList()) {
            subscriber.inform(value)
            if (subscriber.once) {
                off(subscriber)
            }
        }
    }

    private fun register(subscriber: AsyncSubscriber<V>): Subscription {
        subscribers.add(subscriber)
        return Subscription {
            off(subscriber)
        }
    }

    @Synchronized
    override fun on(block: suspend CoroutineScope.(V) -> Unit): Subscription {
        val subscriber = AsyncSubscriber(sequentialScope, subscriberScope, block, false, logger)
        return register(subscriber)
    }

    @Synchronized
    override fun once(block: suspend CoroutineScope.(V) -> Unit): Subscription {
        val subscriber = AsyncSubscriber(sequentialScope, subscriberScope, block, true, logger)
        return register(subscriber)
    }

    @Synchronized
    override fun offAll() {
        subscribers.clear()
    }

    @Synchronized
    private fun off(subscriber: AsyncSubscriber<V>) {
        subscribers.remove(subscriber)
    }
}

private class AsyncSubscriber<V>(
    private val emitterSequentialScope: CoroutineScope,
    private val subscriberScope: CoroutineScope,
    private val subscriberBlock: (suspend CoroutineScope.(V) -> Unit),
    val once: Boolean,
    private val logger: Logger? = null,
) : Comparable<V> {
    val values = LinkedBlockingQueue<V>() // Accessed by both Emitter#emit and emitterSequentialScope
    var isSubscriberRunning = false // Only accessed as a part of emitterSequentialScope

    fun inform(value: V) {
        values.add(value)
        emitterSequentialScope.launch {
            if (!isSubscriberRunning) {
                isSubscriberRunning = true
                while (values.isNotEmpty()) {
                    val valueTobeEmitted = values.poll()
                    safelyPublish(valueTobeEmitted as V) // Process sequentially, similar to core ably eventEmitter
                }
                isSubscriberRunning = false
            }
        }
    }

    private suspend fun safelyPublish(value: V) {
        runCatching {
            subscriberScope.launch {
                try {
                    subscriberBlock(value)
                } catch (t: Throwable) {
                    // Catching exception to avoid error propagation to parent
                    logger?.warn("Error processing value $value", t)
                }
            }.join()
        }
    }

    override fun compareTo(other: V): Int {
        // Avoid registering duplicate anonymous subscriber block with same instance id
        // Common scenario when Android activity is refreshed or some app components refresh
        if (other is AsyncSubscriber<*>) {
            return this.subscriberBlock.hashCode().compareTo(other.subscriberBlock.hashCode())
        }
        return this.hashCode().compareTo(other.hashCode())
    }
}
