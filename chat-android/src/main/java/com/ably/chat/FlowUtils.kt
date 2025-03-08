package com.ably.chat

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Converts a callback-based API to a Flow with an unlimited buffer to handle backpressure
 */
internal inline fun <T> transformCallbackAsFlow(crossinline subscribe: ((T) -> Unit) -> Subscription): Flow<T> = flow {
    val channel = Channel<T>(Channel.UNLIMITED)

    val subscription = subscribe { channel.trySend(it) }

    try {
        for (item in channel) {
            emit(item)
        }
    } finally {
        subscription.unsubscribe()
    }
}
