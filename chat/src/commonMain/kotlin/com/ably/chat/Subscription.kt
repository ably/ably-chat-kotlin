package com.ably.chat

/**
 * Represents a subscription that can be unsubscribed from.
 * This interface provides a way to clean up and remove subscriptions when they
 * are no longer needed.
 *
 * @interface
 * @example
 * ```kotlin
 * val s = someService.subscribe();
 * // Later when done with the subscription
 * s.unsubscribe();
 * ```
 * ### Not suitable for inheritance
 * This interface is not designed for client implementation or extension. The interface definition may evolve over time
 * with additional properties or methods to support new features, which could break
 * client implementations.
 */
public fun interface Subscription {

    /**
     * This method should be called when the subscription is no longer needed,
     * it will make sure no further events will be sent to the subscriber and
     * that references to the subscriber are cleaned up.
     */
    public fun unsubscribe()

    /**
     * Syntactic sugar to allow using custom name for `unsubscribe` handling
     *
     * @example
     * ```kotlin
     * val (off) = someService.on { println(it) }
     * off()
     * val (unsubscribe) = someService.subscribe { println(it) }
     * unsubscribe()
     * val (dispose) = someService.addListener { println(it) }
     * dispose()
     * ```
     */
    public operator fun component1(): () -> Unit = {
        this.unsubscribe()
    }
}
