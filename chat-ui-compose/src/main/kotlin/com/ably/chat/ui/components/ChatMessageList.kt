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
 * @param enableMessageGrouping Whether to group consecutive messages from the same user. When enabled,
 *   avatar and name are only shown for the first message in a group, and timestamp only for the last.
 * @param messageContent Custom composable for rendering each message. Receives the message and grouping info.
 *   Defaults to [ChatMessage] with grouping support when [enableMessageGrouping] is true.
 * @param loadingContent Custom composable shown while loading messages.
 * @param emptyContent Custom composable shown when there are no messages.
 * @param errorContent Custom composable shown when an error occurs.
 */
@OptIn(ExperimentalChatApi::class, InternalChatApi::class)
@Composable
public fun ChatMessageList(
    room: Room,
    modifier: Modifier = Modifier,
    scrollThreshold: Int = 10,
    fetchSize: Int = 100,
    showDateSeparators: Boolean = true,
    dateFormat: String = "MMMM d, yyyy",
    showScrollToBottomButton: Boolean = true,
    scrollToBottomThreshold: Int = 3,
    hideDeletedMessages: Boolean = false,
    enableMessageGrouping: Boolean = false,
    messageContent: @Composable (Message, MessageGroupInfo?) -> Unit = { message, groupInfo ->
        ChatMessage(
            message = message,
            currentClientId = room.clientId,
            showClientId = groupInfo?.isFirstInGroup ?: true,
            showTimestamp = groupInfo?.isLastInGroup ?: true,
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

    ChatMessageListContent(
        pagingState = pagingState,
        modifier = modifier,
        showDateSeparators = showDateSeparators,
        dateFormat = dateFormat,
        showScrollToBottomButton = showScrollToBottomButton,
        scrollToBottomThreshold = scrollToBottomThreshold,
        hideDeletedMessages = hideDeletedMessages,
        enableMessageGrouping = enableMessageGrouping,
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
 * @param enableMessageGrouping Whether to group consecutive messages from the same user.
 * @param messageContent Custom composable for rendering each message with grouping info.
 * @param loadingContent Custom composable shown while loading messages.
 * @param emptyContent Custom composable shown when there are no messages.
 * @param errorContent Custom composable shown when an error occurs.
 */
@OptIn(ExperimentalChatApi::class)
@Composable
public fun ChatMessageList(
    pagingState: PagingMessagesState,
    currentClientId: String,
    modifier: Modifier = Modifier,
    showDateSeparators: Boolean = true,
    dateFormat: String = "MMMM d, yyyy",
    showScrollToBottomButton: Boolean = true,
    scrollToBottomThreshold: Int = 3,
    hideDeletedMessages: Boolean = false,
    enableMessageGrouping: Boolean = false,
    messageContent: @Composable (Message, MessageGroupInfo?) -> Unit = { message, groupInfo ->
        ChatMessage(
            message = message,
            currentClientId = currentClientId,
            showClientId = groupInfo?.isFirstInGroup ?: true,
            showTimestamp = groupInfo?.isLastInGroup ?: true,
        )
    },
    loadingContent: @Composable () -> Unit = { DefaultLoadingIndicator() },
    emptyContent: @Composable () -> Unit = { DefaultEmptyState() },
    errorContent: @Composable (String) -> Unit = { error -> DefaultErrorState(error) },
) {
    ChatMessageListContent(
        pagingState = pagingState,
        modifier = modifier,
        showDateSeparators = showDateSeparators,
        dateFormat = dateFormat,
        showScrollToBottomButton = showScrollToBottomButton,
        scrollToBottomThreshold = scrollToBottomThreshold,
        hideDeletedMessages = hideDeletedMessages,
        enableMessageGrouping = enableMessageGrouping,
        messageContent = messageContent,
        loadingContent = loadingContent,
        emptyContent = emptyContent,
        errorContent = errorContent,
    )
}

@OptIn(ExperimentalChatApi::class)
@Composable
private fun ChatMessageListContent(
    pagingState: PagingMessagesState,
    modifier: Modifier,
    showDateSeparators: Boolean,
    dateFormat: String,
    showScrollToBottomButton: Boolean,
    scrollToBottomThreshold: Int,
    hideDeletedMessages: Boolean,
    enableMessageGrouping: Boolean,
    messageContent: @Composable (Message, MessageGroupInfo?) -> Unit,
    loadingContent: @Composable () -> Unit,
    emptyContent: @Composable () -> Unit,
    errorContent: @Composable (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var previousMessageCount by remember { mutableIntStateOf(pagingState.loaded.size) }
    var newMessageCount by remember { mutableIntStateOf(0) }

    // Filter out deleted messages if hideDeletedMessages is enabled
    val messagesToDisplay = remember(pagingState.loaded, hideDeletedMessages) {
        if (hideDeletedMessages) {
            pagingState.loaded.filter { it.action != MessageAction.MessageDelete }
        } else {
            pagingState.loaded
        }
    }

    // Check if user is scrolled away from bottom
    val isScrolledUp = pagingState.listState.firstVisibleItemIndex > scrollToBottomThreshold

    // Track new messages and auto-scroll behavior
    LaunchedEffect(messagesToDisplay.size) {
        val currentCount = messagesToDisplay.size
        val addedMessages = currentCount - previousMessageCount

        if (addedMessages > 0) {
            val isAtBottom = pagingState.listState.firstVisibleItemIndex <= 1

            if (isAtBottom) {
                // User is at bottom, auto-scroll and reset count
                pagingState.listState.animateScrollToItem(0)
                newMessageCount = 0
            } else {
                // User is scrolled up, increment unread count
                newMessageCount += addedMessages
            }
        }
        previousMessageCount = currentCount
    }

    // Reset new message count when user scrolls to bottom
    LaunchedEffect(isScrolledUp) {
        if (!isScrolledUp) {
            newMessageCount = 0
        }
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
                            // Compute grouping info if enabled
                            // Note: In reversed layout, index 0 is newest, so "previous" is index-1 (newer)
                            // and "next" is index+1 (older)
                            val groupInfo = if (enableMessageGrouping) {
                                val previousMessage = messagesToDisplay.getOrNull(index - 1) // newer message
                                val nextMessage = messagesToDisplay.getOrNull(index + 1) // older message

                                val groupedWithPrevious = shouldGroupMessages(message, previousMessage)
                                val groupedWithNext = shouldGroupMessages(message, nextMessage)

                                MessageGroupInfo(
                                    isFirstInGroup = !groupedWithNext, // Show avatar/name if not grouped with older
                                    isLastInGroup = !groupedWithPrevious, // Show timestamp if not grouped with newer
                                )
                            } else {
                                null
                            }

                            messageContent(message, groupInfo)

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

                    // New messages pill at top
                    NewMessagesPill(
                        visible = newMessageCount > 0 && isScrolledUp,
                        count = newMessageCount,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        onClick = {
                            coroutineScope.launch {
                                pagingState.listState.animateScrollToItem(0)
                                newMessageCount = 0
                            }
                        },
                    )

                    if (showScrollToBottomButton) {
                        ScrollToBottomButton(
                            listState = pagingState.listState,
                            threshold = scrollToBottomThreshold,
                            unreadCount = newMessageCount,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            onClick = {
                                coroutineScope.launch {
                                    pagingState.listState.animateScrollToItem(0)
                                    newMessageCount = 0
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

/**
 * Default time threshold for message grouping (2 minutes).
 * Messages from the same user within this time window are grouped together.
 */
private const val MESSAGE_GROUP_THRESHOLD_MS = 2 * 60 * 1000L

/**
 * Determines if two messages should be grouped together.
 *
 * Messages are grouped when:
 * - They are from the same client
 * - They are within the time threshold
 * - Neither is a deleted message
 *
 * @param current The current message.
 * @param previous The previous message (newer, since list is reversed).
 * @return True if the messages should be grouped.
 */
internal fun shouldGroupMessages(current: Message, previous: Message?): Boolean {
    if (previous == null) return false
    if (current.clientId != previous.clientId) return false
    if (current.action == MessageAction.MessageDelete || previous.action == MessageAction.MessageDelete) return false

    val timeDiff = kotlin.math.abs(current.timestamp - previous.timestamp)
    return timeDiff <= MESSAGE_GROUP_THRESHOLD_MS
}

/**
 * Data class containing grouping information for a message.
 *
 * @property isFirstInGroup Whether this is the first message in a group (should show avatar/name).
 * @property isLastInGroup Whether this is the last message in a group (should show timestamp).
 */
public data class MessageGroupInfo(
    val isFirstInGroup: Boolean,
    val isLastInGroup: Boolean,
)
