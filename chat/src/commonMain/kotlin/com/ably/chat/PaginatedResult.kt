package com.ably.chat

import com.ably.chat.json.JsonValue
import io.ably.lib.types.AsyncHttpPaginatedResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import io.ably.lib.types.ErrorInfo as PubSubErrorInfo

/**
 * Represents the result of a paginated query.
 *
 * ### Not suitable for inheritance
 * This interface is not designed for implementation or extension outside this SDK.
 * The interface definition may evolve over time with additional properties or methods to support new features,
 * which could break implementations.
 */
public interface PaginatedResult<T> {

    /**
     * The items returned by the query.
     */
    public val items: List<T>

    /**
     * Fetches the next page of items.
     */
    public suspend fun next(): PaginatedResult<T>

    /**
     * Whether there are more items to query.
     *
     * @returns `true` if there are more items to query, `false` otherwise.
     */
    public fun hasNext(): Boolean
}

internal fun <T> AsyncHttpPaginatedResponse?.toPaginatedResult(transform: (JsonValue) -> T?): PaginatedResult<T> =
    this?.let { AsyncPaginatedResultWrapper(it, transform) } ?: EmptyPaginatedResult()

private class EmptyPaginatedResult<T> : PaginatedResult<T> {
    override val items: List<T>
        get() = emptyList()

    override suspend fun next(): PaginatedResult<T> = this

    override fun hasNext(): Boolean = false
}

private class AsyncPaginatedResultWrapper<T>(
    val asyncPaginatedResult: AsyncHttpPaginatedResponse,
    val transform: (JsonValue) -> T?,
) : PaginatedResult<T> {
    override val items: List<T> = asyncPaginatedResult.items()?.mapNotNull {
        it.tryAsJsonValue()?.let(transform)
    } ?: emptyList()

    override suspend fun next(): PaginatedResult<T> = suspendCancellableCoroutine { continuation ->
        asyncPaginatedResult.next(object : AsyncHttpPaginatedResponse.Callback {
            override fun onResponse(response: AsyncHttpPaginatedResponse?) {
                continuation.resume(response.toPaginatedResult(transform))
            }

            override fun onError(reason: PubSubErrorInfo?) {
                continuation.resumeWithException(ChatException(reason))
            }
        })
    }

    override fun hasNext(): Boolean = asyncPaginatedResult.hasNext()
}
