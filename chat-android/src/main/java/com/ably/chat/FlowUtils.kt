package com.ably.chat

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Converts a callback-based API to a Flow with an unlimited buffer to handle backpressure
 */
internal inline fun <T> transformCallbackAsFlow(crossinline subscribe: ((T) -> Unit) -> Subscription): Flow<T> = callbackFlow {
    val subscription = subscribe { trySend(it) }
    awaitClose { subscription.unsubscribe() }
}.buffer(Channel.UNLIMITED)
