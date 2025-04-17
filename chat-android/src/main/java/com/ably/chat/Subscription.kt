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
 */
public fun interface Subscription {

    /**
     * This method should be called when the subscription is no longer needed,
     * it will make sure no further events will be sent to the subscriber and
     * that references to the subscriber are cleaned up.
     */
    public fun unsubscribe()
}

/**
 * Represents a subscription to status change events that can be unsubscribed from. This
// * interface provides a way to clean up and remove subscriptions when they are no longer needed.
 *
 * @interface
 * @example
 * ```kotlin
 * val s = someService.onStatusChange();
 * val s2 = someOtherService.on()
 * // Later when done with the subscription
 * s.off();
 * s2.off();
 * ```
 */
public fun interface StatusSubscription {
    /**
     * Unsubscribes from the status change events. It will ensure that no
     * further status change events will be sent to the subscriber and
     * that references to the subscriber are cleaned up.
     */
    public fun off()
}
