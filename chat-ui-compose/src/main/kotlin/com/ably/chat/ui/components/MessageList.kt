package com.ably.chat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ably.chat.Message
import com.ably.chat.MessageAction
import com.ably.chat.Room
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.annotations.InternalChatApi
import com.ably.chat.extensions.compose.PagingMessagesState
import com.ably.chat.extensions.compose.collectAsPagingMessagesState
import com.ably.chat.ui.theme.AblyChatTheme
import kotlinx.coroutines.launch

/**
 * A composable that displays a paginated list of messages from a chat room.
 *
 * This component automatically handles:
 * - Subscribing to new messages
 * - Loading message history with pagination
 * - Auto-loading older messages when scrolling to the top
 *
 * The list is displayed in reverse order (newest messages at the bottom).
 *
 * @param room The chat room to display messages from.
 * @param modifier Modifier to be applied to the list.
 * @param scrollThreshold Number of items from the end at which to trigger loading more messages.
 * @param fetchSize Number of messages to load per page.
 * @param showDateSeparators Whether to show date separators between messages from different days.
 * @param dateFormat The format pattern for date separators. Defaults to "MMMM d, yyyy".
 * @param showScrollToBottomButton Whether to show a FAB to scroll to the latest messages.
 * @param scrollToBottomThreshold Number of items from the bottom before showing the scroll button.
 * @param hideDeletedMessages Whether to completely hide deleted messages instead of showing "[Message deleted]".
 * @param messageContent Custom composable for rendering each message. Defaults to [MessageBubble].
 * @param loadingContent Custom composable shown while loading messages.
 * @param emptyContent Custom composable shown when there are no messages.
 * @param errorContent Custom composable shown when an error occurs.
 */
@OptIn(ExperimentalChatApi::class, InternalChatApi::class)
@Composable
public fun MessageList(
    room: Room,
    modifier: Modifier = Modifier,
    scrollThreshold: Int = 10,
    fetchSize: Int = 100,
    showDateSeparators: Boolean = true,
    dateFormat: String = "MMMM d, yyyy",
    showScrollToBottomButton: Boolean = true,
    scrollToBottomThreshold: Int = 3,
    hideDeletedMessages: Boolean = false,
    messageContent: @Composable (Message) -> Unit = { message ->
        MessageBubble(
            message = message,
            currentClientId = room.clientId,
        )
    },
    loadingContent: @Composable () -> Unit = { DefaultLoadingIndicator() },
    emptyContent: @Composable () -> Unit = { DefaultEmptyState() },
    errorContent: @Composable (String) -> Unit = { error -> DefaultErrorState(error) },
) {
    val pagingState = room.collectAsPagingMessagesState(
        scrollThreshold = scrollThreshold,
        fetchSize = fetchSize,
    )

    MessageListContent(
        pagingState = pagingState,
        modifier = modifier,
        showDateSeparators = showDateSeparators,
        dateFormat = dateFormat,
        showScrollToBottomButton = showScrollToBottomButton,
        scrollToBottomThreshold = scrollToBottomThreshold,
        hideDeletedMessages = hideDeletedMessages,
        messageContent = messageContent,
        loadingContent = loadingContent,
        emptyContent = emptyContent,
        errorContent = errorContent,
    )
}

/**
 * A composable that displays a paginated list of messages using a pre-existing [PagingMessagesState].
 *
 * Use this overload when you need to manage the paging state yourself or share it with other components.
 *
 * @param pagingState The paging state containing messages and loading status.
 * @param currentClientId The clientId of the current user, used for message styling.
 * @param modifier Modifier to be applied to the list.
 * @param showDateSeparators Whether to show date separators between messages from different days.
 * @param dateFormat The format pattern for date separators. Defaults to "MMMM d, yyyy".
 * @param showScrollToBottomButton Whether to show a FAB to scroll to the latest messages.
 * @param scrollToBottomThreshold Number of items from the bottom before showing the scroll button.
 * @param hideDeletedMessages Whether to completely hide deleted messages instead of showing "[Message deleted]".
 * @param messageContent Custom composable for rendering each message. Defaults to [MessageBubble].
 * @param loadingContent Custom composable shown while loading messages.
 * @param emptyContent Custom composable shown when there are no messages.
 * @param errorContent Custom composable shown when an error occurs.
 */
@OptIn(ExperimentalChatApi::class)
@Composable
public fun MessageList(
    pagingState: PagingMessagesState,
    currentClientId: String,
    modifier: Modifier = Modifier,
    showDateSeparators: Boolean = true,
    dateFormat: String = "MMMM d, yyyy",
    showScrollToBottomButton: Boolean = true,
    scrollToBottomThreshold: Int = 3,
    hideDeletedMessages: Boolean = false,
    messageContent: @Composable (Message) -> Unit = { message ->
        MessageBubble(
            message = message,
            currentClientId = currentClientId,
        )
    },
    loadingContent: @Composable () -> Unit = { DefaultLoadingIndicator() },
    emptyContent: @Composable () -> Unit = { DefaultEmptyState() },
    errorContent: @Composable (String) -> Unit = { error -> DefaultErrorState(error) },
) {
    MessageListContent(
        pagingState = pagingState,
        modifier = modifier,
        showDateSeparators = showDateSeparators,
        dateFormat = dateFormat,
        showScrollToBottomButton = showScrollToBottomButton,
        scrollToBottomThreshold = scrollToBottomThreshold,
        hideDeletedMessages = hideDeletedMessages,
        messageContent = messageContent,
        loadingContent = loadingContent,
        emptyContent = emptyContent,
        errorContent = errorContent,
    )
}

@OptIn(ExperimentalChatApi::class)
@Composable
private fun MessageListContent(
    pagingState: PagingMessagesState,
    modifier: Modifier,
    showDateSeparators: Boolean,
    dateFormat: String,
    showScrollToBottomButton: Boolean,
    scrollToBottomThreshold: Int,
    hideDeletedMessages: Boolean,
    messageContent: @Composable (Message) -> Unit,
    loadingContent: @Composable () -> Unit,
    emptyContent: @Composable () -> Unit,
    errorContent: @Composable (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var previousMessageCount by remember { mutableIntStateOf(pagingState.loaded.size) }

    // Filter out deleted messages if hideDeletedMessages is enabled
    val messagesToDisplay = remember(pagingState.loaded, hideDeletedMessages) {
        if (hideDeletedMessages) {
            pagingState.loaded.filter { it.action != MessageAction.MessageDelete }
        } else {
            pagingState.loaded
        }
    }

    // Auto-scroll to bottom when new messages arrive and user is at the bottom
    LaunchedEffect(messagesToDisplay.size) {
        val currentCount = messagesToDisplay.size
        val isNewMessage = currentCount > previousMessageCount
        val isAtBottom = pagingState.listState.firstVisibleItemIndex <= 1

        if (isNewMessage && isAtBottom) {
            pagingState.listState.animateScrollToItem(0)
        }
        previousMessageCount = currentCount
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            pagingState.error != null -> {
                errorContent(pagingState.error?.message ?: "An error occurred")
            }
            messagesToDisplay.isEmpty() && pagingState.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    loadingContent()
                }
            }
            messagesToDisplay.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    emptyContent()
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = pagingState.listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = messagesToDisplay,
                            key = { _, message -> message.serial },
                        ) { index, message ->
                            messageContent(message)

                            if (showDateSeparators) {
                                val nextMessage = messagesToDisplay.getOrNull(index + 1)
                                if (shouldShowDateSeparator(message.timestamp, nextMessage?.timestamp)) {
                                    DateSeparator(
                                        timestamp = message.timestamp,
                                        dateFormat = dateFormat,
                                    )
                                }
                            }
                        }

                        if (pagingState.loading && pagingState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    loadingContent()
                                }
                            }
                        }
                    }

                    if (showScrollToBottomButton) {
                        ScrollToBottomButton(
                            listState = pagingState.listState,
                            threshold = scrollToBottomThreshold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            onClick = {
                                coroutineScope.launch {
                                    pagingState.listState.animateScrollToItem(0)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultLoadingIndicator() {
    CircularProgressIndicator()
}

@Composable
private fun DefaultEmptyState() {
    Text(
        text = "No messages yet",
        color = AblyChatTheme.colors.timestamp,
    )
}

@Composable
private fun DefaultErrorState(error: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = error,
            color = AblyChatTheme.colors.timestamp,
        )
    }
}
