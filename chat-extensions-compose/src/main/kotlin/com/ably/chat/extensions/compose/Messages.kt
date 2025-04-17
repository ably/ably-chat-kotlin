package com.ably.chat.extensions.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ably.chat.Message
import com.ably.chat.MessageEventType
import com.ably.chat.MessagesSubscription
import com.ably.chat.PaginatedResult
import com.ably.chat.Room
import com.ably.chat.RoomStatus.Attached
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.discontinuityAsFlow
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo

/**
 * Fetches the next page of messages when the user scrolls close to the last visible message.
 *
 * @param scrollThreshold The number of items remaining before reaching the last item at which fetching the next page is triggered.
 * @param fetchSize The number of messages to load per page.
 *
 * @return A list of paginated messages.
 */
@ExperimentalChatApi
@Composable
@Suppress("LongMethod")
public fun Room.collectAsPagingMessagesState(scrollThreshold: Int = 10, fetchSize: Int = 100): PagingMessagesState {
    val listState = rememberLazyListState()
    val loaded = remember(this) { mutableStateListOf<Message>() }
    var loading by remember(this) { mutableStateOf(false) }
    var lastReceivedPaginatedResult: PaginatedResult<Message>? by remember(this) { mutableStateOf(null) }
    var subscription: MessagesSubscription? by remember(this) { mutableStateOf(null) }
    val roomStatus = collectAsStatus()
    var error by remember(this) { mutableStateOf<ErrorInfo?>(null) }

    DisposableEffect(this) {
        subscription = messages.subscribe { event ->
            when (event.type) {
                MessageEventType.Created -> loaded.add(0, event.message)

                MessageEventType.Updated -> loaded.replaceFirstWith(event.message) {
                    it.serial == event.message.serial
                }

                MessageEventType.Deleted -> loaded.replaceFirstWith(event.message) {
                    it.serial == event.message.serial
                }
            }
        }

        onDispose {
            subscription?.unsubscribe()
            subscription = null
        }
    }

    LaunchedEffect(this) {
        discontinuityAsFlow().collect {
            loaded.clear()
            lastReceivedPaginatedResult = null
        }
    }

    val shouldRequestMessages by remember(this) {
        derivedStateOf {
            val scrollIsAtTheTop = listState.firstVisibleItemIndex > loaded.size - scrollThreshold
            val hasMoreItems = lastReceivedPaginatedResult?.hasNext() == true
            val shouldGetPreviousMessages = scrollIsAtTheTop && hasMoreItems
            val shouldReInitialize = subscription != null && lastReceivedPaginatedResult == null
            (shouldReInitialize || shouldGetPreviousMessages) && roomStatus == Attached
        }
    }

    LaunchedEffect(this, error, shouldRequestMessages) {
        if (!shouldRequestMessages || error != null) {
            loading = false
            return@LaunchedEffect
        }

        loading = true

        val receivedPaginatedResult = try {
            lastReceivedPaginatedResult?.next()
                ?: subscription!!.getPreviousMessages(limit = fetchSize)
        } catch (exception: AblyException) {
            error = exception.errorInfo
            return@LaunchedEffect
        }
        lastReceivedPaginatedResult = receivedPaginatedResult
        loaded += receivedPaginatedResult.items
        loading = false
    }

    return DefaultPagingMessagesState(
        loaded = loaded,
        listState = listState,
        loading = loading,
        hasMore = lastReceivedPaginatedResult?.hasNext() ?: true,
        error = error,
        refreshLambda = { error = null },
    )
}

public interface PagingMessagesState {
    public val loaded: SnapshotStateList<Message>
    public val listState: LazyListState
    public val loading: Boolean
    public val hasMore: Boolean
    public val error: ErrorInfo?
    public suspend fun refresh()
}

private data class DefaultPagingMessagesState(
    override val loaded: SnapshotStateList<Message>,
    override val listState: LazyListState,
    override val loading: Boolean,
    override val hasMore: Boolean,
    override val error: ErrorInfo?,
    private val refreshLambda: suspend () -> Unit,
) : PagingMessagesState {
    override suspend fun refresh() = refreshLambda()
}

private inline fun <T> MutableList<T>.replaceFirstWith(replacement: T, predicate: (T) -> Boolean) {
    val index = indexOfFirst(predicate)
    if (index != -1) set(index, replacement)
}
