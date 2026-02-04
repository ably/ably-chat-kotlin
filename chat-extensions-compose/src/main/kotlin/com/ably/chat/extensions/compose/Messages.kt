package com.ably.chat.extensions.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ably.chat.ChatException
import com.ably.chat.ChatMessageEvent
import com.ably.chat.ChatMessageEventType
import com.ably.chat.ErrorInfo
import com.ably.chat.Message
import com.ably.chat.MessageReactionSummaryEvent
import com.ably.chat.MessagesSubscription
import com.ably.chat.PaginatedResult
import com.ably.chat.Room
import com.ably.chat.RoomStatus.Attached
import com.ably.chat.SummaryClientIdCounts
import com.ably.chat.SummaryClientIdList
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.asFlow
import com.ably.chat.discontinuityAsFlow
import com.ably.chat.mergeWith
import com.ably.chat.with
import kotlinx.coroutines.flow.map

/**
 * Fetches the next page of messages when the user scrolls close to the last visible message.
 *
 * @param scrollThreshold The number of items remaining before reaching the last item at which fetching the next page is triggered.
 * @param fetchSize The number of messages to load per page.
 *
 * @return [PagingMessagesState] containing the current state of the messages loaded in the chat room.
 */
@ExperimentalChatApi
@Composable
@Suppress("LongMethod", "CognitiveComplexMethod")
public fun Room.collectAsPagingMessagesState(scrollThreshold: Int = 10, fetchSize: Int = 100): PagingMessagesState {
    val listState = rememberLazyListState()
    val loaded = remember(this) { mutableStateListOf<Message>() }
    val loadingState = remember(this) { mutableStateOf(false) }
    val lastReceivedPaginatedResultState = remember(this) { mutableStateOf<PaginatedResult<Message>?>(null) }
    var lastReceivedPaginatedResult by lastReceivedPaginatedResultState
    var subscription: MessagesSubscription? by remember(this) { mutableStateOf(null) }
    val roomStatus by collectAsStatus()
    val errorState = remember(this) { mutableStateOf<ErrorInfo?>(null) }

    val pagingMessagesState = remember(this) {
        PagingMessagesState(
            loaded = loaded,
            listState = listState,
            errorState = errorState,
            loadingState = loadingState,
            lastReceivedPaginatedResultState = lastReceivedPaginatedResultState,
        )
    }

    DisposableEffect(this) {
        val effectSubscription = messages.subscribe { event ->
            when (event.type) {
                ChatMessageEventType.Created -> {
                    // Only add if not already in list to prevent duplicate keys
                    if (loaded.none { it.serial == event.message.serial }) {
                        loaded.add(0, event.message)
                    }
                }
                ChatMessageEventType.Updated -> loaded.replaceFirstWith(event)
                ChatMessageEventType.Deleted -> loaded.replaceFirstWith(event)
            }
        }

        subscription = effectSubscription

        onDispose {
            effectSubscription.unsubscribe()
            if (subscription == effectSubscription) {
                subscription = null
            }
        }
    }

    LaunchedEffect(this) {
        @OptIn(InternalChatApi::class)
        messages.reactions.asFlow().map { event ->
            if (event.hasClippedWithoutMyClientId(clientId)) {
                event.mergeWith(messages.reactions.clientReactions(event.messageSerial))
            } else {
                event
            }
        }.collect { loaded.replaceFirstWith(it) }
    }

    LaunchedEffect(this) {
        discontinuityAsFlow().collect {
            loaded.clear()
            lastReceivedPaginatedResult = null
        }
    }

    val scrollIsAtTheTop by remember(this) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0 || visibleItemsInfo.isEmpty()) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                lastVisibleItem.index >= layoutInfo.totalItemsCount - scrollThreshold - 1
            }
        }
    }

    val shouldRequestMessages by remember(this) {
        derivedStateOf {
            val hasMoreItems = lastReceivedPaginatedResult?.hasNext() == true
            val shouldGetPreviousMessages = scrollIsAtTheTop && hasMoreItems
            val shouldReInitialize = subscription != null && lastReceivedPaginatedResult == null
            (shouldReInitialize || shouldGetPreviousMessages) && roomStatus == Attached
        }
    }

    LaunchedEffect(this, error, shouldRequestMessages) {
        if (!shouldRequestMessages || error != null) {
            loadingState.value = false
            return@LaunchedEffect
        }

        loadingState.value = true

        val receivedPaginatedResult = try {
            lastReceivedPaginatedResult?.next()
                ?: subscription!!.historyBeforeSubscribe(limit = fetchSize)
        } catch (exception: ChatException) {
            errorState.value = exception.errorInfo
            return@LaunchedEffect
        }
        lastReceivedPaginatedResult = receivedPaginatedResult
        // Filter out any items that already exist in the list to prevent duplicate keys
        val existingSerials = loaded.map { it.serial }.toSet()
        val newItems = receivedPaginatedResult.items.filter { it.serial !in existingSerials }
        loaded += newItems
        loadingState.value = false
    }

    return pagingMessagesState
}

internal fun MessageReactionSummaryEvent.hasClippedWithoutMyClientId(clientId: String): Boolean {
    return this.reactions.multiple.hasClippedWithoutMyClientId(clientId) ||
        this.reactions.unique.hasClippedWithoutMyClientId(clientId) ||
        this.reactions.distinct.hasClippedWithoutMyClientId(clientId)
}

private fun <T> Map<String, T>.hasClippedWithoutMyClientId(clientId: String) = any {
    when (val value = it.value) {
        is SummaryClientIdCounts -> value.clipped && !value.clientIds.contains(clientId)
        is SummaryClientIdList -> value.clipped && !value.clientIds.contains(clientId)
        else -> false
    }
}

/**
 * Represents the state for paging through a list of chat messages.
 *
 * This class is designed to manage and track the state of messages loaded
 * in a paginated format within a chat room, along with the associated UI state.
 * It provides information about the loaded messages, the scrolling state,
 * loading progress, availability of additional data, and any errors encountered
 * during the loading process.
 *
 * @property loaded A list of messages that have been loaded so far in reversed order (the most recent message is the first).
 *                  This list is updated as new messages are loaded or removed (e.g., when scrolling up or down).
 *                  It can be used to display the list of messages in a UI component.
 *                  (Note: The list is mutable and can be updated externally to reflect changes in the underlying data)
 * @property listState The scrolling state of the list displaying the messages.
 */
public class PagingMessagesState internal constructor(
    public val loaded: List<Message>,
    public val listState: LazyListState,
    private val errorState: MutableState<ErrorInfo?>,
    private val loadingState: MutableState<Boolean>,
    private val lastReceivedPaginatedResultState: MutableState<PaginatedResult<Message>?>,
) {
    /**
     * Indicates whether a loading operation is currently in progress.
     */
    public val loading: Boolean
        get() = loadingState.value

    /**
     * Indicates whether there are more messages to load in the pagination state.
     */
    public val hasMore: Boolean
        get() = lastReceivedPaginatedResultState.value?.hasNext() == true

    /**
     * An optional error encountered during loading, if any.
     */
    public val error: ErrorInfo?
        get() = errorState.value

    /**
     * Refreshes the list of messages by reloading the most recent page of messages.
     */
    public fun refresh() {
        errorState.value = null
    }
}

private fun MutableList<Message>.replaceFirstWith(event: ChatMessageEvent) {
    val index = indexOfFirst { it.serial == event.message.serial }
    if (index != -1) set(index, get(index).with(event))
}

private fun MutableList<Message>.replaceFirstWith(event: MessageReactionSummaryEvent) {
    val index = indexOfFirst { it.serial == event.messageSerial }
    if (index != -1) set(index, get(index).with(event))
}
