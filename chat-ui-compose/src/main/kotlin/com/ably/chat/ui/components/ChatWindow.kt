package com.ably.chat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ably.chat.Message
import com.ably.chat.Room
import com.ably.chat.annotations.ExperimentalChatApi
import com.ably.chat.annotations.InternalChatApi
import kotlinx.coroutines.launch

/**
 * A unified chat window component that combines all chat UI elements.
 *
 * This composable provides a complete chat experience including:
 * - Message list with pagination
 * - Message input field
 * - Typing indicators
 * - Message reactions
 * - Message editing and deletion
 * - Date separators
 * - Scroll-to-bottom button
 *
 * @param room The chat room to display.
 * @param modifier Modifier to be applied to the chat window.
 * @param showTypingIndicator Whether to show the typing indicator. Defaults to true.
 * @param showDateSeparators Whether to show date separators between messages. Defaults to true.
 * @param showScrollToBottom Whether to show the scroll-to-bottom button. Defaults to true.
 * @param showAvatars Whether to show avatars next to messages from other users. Defaults to true.
 * @param hideDeletedMessages Whether to completely hide deleted messages instead of showing "[Message deleted]". Defaults to false.
 * @param enableReactions Whether to enable message reactions. Defaults to true.
 * @param enableEditing Whether to enable message editing for own messages. Defaults to true.
 * @param enableDeletion Whether to enable message deletion for own messages. Defaults to true.
 * @param headerContent Optional custom header content slot.
 * @param footerContent Optional custom footer content slot.
 * @param onMessageSent Optional callback invoked when a message is sent.
 * @param onMessageEdited Optional callback invoked when a message is edited.
 * @param onMessageDeleted Optional callback invoked when a message is deleted.
 * @param onReactionToggled Optional callback invoked when a reaction is toggled.
 */
@OptIn(ExperimentalChatApi::class, InternalChatApi::class)
@Composable
public fun ChatWindow(
    room: Room,
    modifier: Modifier = Modifier,
    showTypingIndicator: Boolean = true,
    showDateSeparators: Boolean = true,
    showScrollToBottom: Boolean = true,
    showAvatars: Boolean = true,
    hideDeletedMessages: Boolean = false,
    enableReactions: Boolean = true,
    enableEditing: Boolean = true,
    enableDeletion: Boolean = true,
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
    onMessageSent: ((Message) -> Unit)? = null,
    onMessageEdited: ((Message) -> Unit)? = null,
    onMessageDeleted: ((Message) -> Unit)? = null,
    onReactionToggled: ((Message, String) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedMessageForReaction by remember { mutableStateOf<Message?>(null) }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Header slot
        headerContent?.invoke()

        // Message list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            MessageList(
                room = room,
                modifier = Modifier.fillMaxSize(),
                showDateSeparators = showDateSeparators,
                showScrollToBottomButton = showScrollToBottom,
                hideDeletedMessages = hideDeletedMessages,
                messageContent = { message ->
                    MessageBubble(
                        message = message,
                        currentClientId = room.clientId,
                        showAvatar = showAvatars,
                        showReactions = enableReactions,
                        onReactionClick = if (enableReactions) { msg, emoji ->
                            coroutineScope.launch {
                                try {
                                    toggleReaction(room, msg, emoji)
                                    onReactionToggled?.invoke(msg, emoji)
                                } catch (_: Exception) {
                                    // Handle error silently or show toast
                                }
                            }
                        } else {
                            null
                        },
                        onAddReaction = if (enableReactions) { msg ->
                            selectedMessageForReaction = msg
                            showEmojiPicker = true
                        } else {
                            null
                        },
                        onEdit = if (enableEditing) { msg, newText ->
                            coroutineScope.launch {
                                try {
                                    val updated = room.messages.update(
                                        serial = msg.serial,
                                        text = newText,
                                    )
                                    onMessageEdited?.invoke(updated)
                                } catch (_: Exception) {
                                    // Handle error silently or show toast
                                }
                            }
                        } else {
                            null
                        },
                        onDelete = if (enableDeletion) { msg ->
                            coroutineScope.launch {
                                try {
                                    val deleted = room.messages.delete(msg.serial)
                                    onMessageDeleted?.invoke(deleted)
                                } catch (_: Exception) {
                                    // Handle error silently or show toast
                                }
                            }
                        } else {
                            null
                        },
                    )
                },
            )

            // Emoji picker dropdown
            if (showEmojiPicker && selectedMessageForReaction != null) {
                EmojiPickerDropdown(
                    expanded = showEmojiPicker,
                    onDismiss = {
                        showEmojiPicker = false
                        selectedMessageForReaction = null
                    },
                    onEmojiSelected = { emoji ->
                        val message = selectedMessageForReaction
                        if (message != null) {
                            coroutineScope.launch {
                                try {
                                    room.messages.reactions.send(
                                        messageSerial = message.serial,
                                        name = emoji,
                                    )
                                    onReactionToggled?.invoke(message, emoji)
                                } catch (_: Exception) {
                                    // Handle error silently
                                }
                            }
                        }
                        showEmojiPicker = false
                        selectedMessageForReaction = null
                    },
                )
            }
        }

        // Typing indicator
        if (showTypingIndicator) {
            TypingIndicator(
                room = room,
                includeCurrentUser = false,
            )
        }

        // Message input
        MessageInput(
            onSend = { text ->
                coroutineScope.launch {
                    try {
                        val message = room.messages.send(text)
                        onMessageSent?.invoke(message)
                    } catch (_: Exception) {
                        // Handle error silently or show toast
                    }
                }
            },
            onTextChanged = { text ->
                coroutineScope.launch {
                    try {
                        if (text.isNotEmpty()) {
                            room.typing.keystroke()
                        } else {
                            room.typing.stop()
                        }
                    } catch (_: Exception) {
                        // Ignore typing errors
                    }
                }
            },
        )

        // Footer slot
        footerContent?.invoke()
    }
}

/**
 * Toggles a reaction on a message.
 * If the user has already reacted with this emoji, removes it; otherwise adds it.
 */
@OptIn(InternalChatApi::class)
private suspend fun toggleReaction(room: Room, message: Message, emoji: String) {
    val currentClientId = room.clientId
    val reactions = message.reactions

    // Check if user has already reacted with this emoji
    val hasReacted = reactions.unique[emoji]?.clientIds?.contains(currentClientId) == true ||
        reactions.distinct[emoji]?.clientIds?.contains(currentClientId) == true ||
        reactions.multiple[emoji]?.clientIds?.containsKey(currentClientId) == true

    if (hasReacted) {
        room.messages.reactions.delete(
            messageSerial = message.serial,
            name = emoji,
        )
    } else {
        room.messages.reactions.send(
            messageSerial = message.serial,
            name = emoji,
        )
    }
}
