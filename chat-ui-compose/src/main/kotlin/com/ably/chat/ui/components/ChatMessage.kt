package com.ably.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.ably.chat.Message
import com.ably.chat.MessageAction
import com.ably.chat.ui.theme.AblyChatTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
r * A composable that displays a single chat message.
 *
 * The message is styled differently based on whether it was sent by the current user
 * (right-aligned, uses [AblyChatTheme.colors.ownMessageBackground]) or by another user
 * (left-aligned, uses [AblyChatTheme.colors.otherMessageBackground]).
 *
 * @param message The message to display.
 * @param currentClientId The clientId of the current user, used to determine message alignment.
 * @param modifier Modifier to be applied to the message row.
 * @param showClientId Whether to show the sender's clientId/displayName above the message. Defaults to true.
 * @param showAvatar Whether to show the sender's avatar next to the message. Defaults to false.
 * @param reserveAvatarSpace Whether to reserve space for avatar even when not shown. Useful for message
 *   grouping to keep bubbles aligned. Defaults to false.
 * @param showTimestamp Whether to show the message timestamp below the message. Defaults to true.
 * @param timestampFormat The format to use for displaying timestamps. Defaults to "HH:mm".
 * @param showReactions Whether to show message reactions below the message. Defaults to true.
 * @param onLongPress Optional callback invoked when the message is long-pressed.
 * @param onReactionClick Optional callback invoked when a reaction pill is clicked.
 * @param onAddReaction Optional callback invoked when the "React" action is selected.
 * @param onEdit Optional callback invoked when the message is edited. Only for own messages.
 * @param onDelete Optional callback invoked when the message is deleted. Only for own messages.
 * @param actions List of actions to show in the context menu. If null, default actions are used.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun ChatMessage(
    message: Message,
    currentClientId: String,
    modifier: Modifier = Modifier,
    showClientId: Boolean = true,
    showAvatar: Boolean = false,
    reserveAvatarSpace: Boolean = false,
    showTimestamp: Boolean = true,
    timestampFormat: String = "HH:mm",
    showReactions: Boolean = true,
    onLongPress: ((Message) -> Unit)? = null,
    onReactionClick: ((Message, String) -> Unit)? = null,
    onAddReaction: ((Message) -> Unit)? = null,
    onEdit: ((Message, String) -> Unit)? = null,
    onDelete: ((Message) -> Unit)? = null,
    actions: List<ChatMessageAction>? = null,
) {
    val isOwnMessage = message.clientId == currentClientId
    val colors = AblyChatTheme.colors
    val typography = AblyChatTheme.typography
    val isDeleted = message.action == MessageAction.MessageDelete
    val isEdited = message.action == MessageAction.MessageUpdate
    val context = LocalContext.current

    // Check for avatar provider to get display name
    val avatarProvider = LocalAvatarProvider.current
    val resolvedData = avatarProvider?.getAvatarData(message.clientId)
    val displayName = resolvedData?.displayName ?: message.clientId

    var showMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(message.serial) { mutableStateOf(message.text) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // No actions available for deleted messages
    val menuActions = if (isDeleted) {
        emptyList()
    } else {
        actions ?: defaultMessageActions(
            isOwnMessage = isOwnMessage,
            onCopy = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", text)
                clipboard.setPrimaryClip(clip)
            },
            onEdit = if (onEdit != null) {
                { isEditing = true }
            } else {
                null
            },
            onDelete = if (onDelete != null) {
                { showDeleteConfirm = true }
            } else {
                null
            },
            onReact = onAddReaction,
            destructiveColor = colors.dialogDestructive,
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onConfirm = {
                onDelete?.invoke(message)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    // Avatar size (Small = 32.dp) + spacing (8.dp) = 40.dp
    val avatarSpaceWidth = 40.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Show avatar for other users' messages, or reserve space for alignment
        if (!isOwnMessage) {
            if (showAvatar) {
                Avatar(
                    clientId = message.clientId,
                    size = AvatarSize.Small,
                    modifier = Modifier.padding(end = 8.dp, top = if (showClientId) 18.dp else 0.dp),
                )
            } else if (reserveAvatarSpace) {
                // Reserve space for avatar to keep messages aligned in groups
                Spacer(modifier = Modifier.width(avatarSpaceWidth))
            }
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
        ) {
            if (showClientId && !isOwnMessage) {
                Text(
                    text = displayName,
                    color = colors.clientId,
                    fontSize = typography.clientId,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
                )
            }

            Box {
                if (isEditing) {
                    // Edit mode UI
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (isOwnMessage) colors.ownMessageBackground else colors.otherMessageBackground,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                                    bottomEnd = if (isOwnMessage) 4.dp else 16.dp,
                                ),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        BasicTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            textStyle = TextStyle(
                                color = if (isOwnMessage) colors.ownMessageContent else colors.otherMessageContent,
                                fontSize = typography.messageText,
                            ),
                            cursorBrush = SolidColor(if (isOwnMessage) colors.ownMessageContent else colors.otherMessageContent),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextButton(
                                onClick = {
                                    editText = message.text
                                    isEditing = false
                                },
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = if (isOwnMessage) colors.ownMessageContent.copy(alpha = 0.7f) else colors.otherMessageContent.copy(alpha = 0.7f),
                                    fontSize = typography.timestamp,
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = {
                                    if (editText.isNotBlank() && editText != message.text) {
                                        onEdit?.invoke(message, editText)
                                    }
                                    isEditing = false
                                },
                                enabled = editText.isNotBlank(),
                            ) {
                                Text(
                                    text = "Save",
                                    color = if (editText.isNotBlank()) {
                                        if (isOwnMessage) colors.ownMessageContent else colors.otherMessageContent
                                    } else {
                                        if (isOwnMessage) colors.ownMessageContent.copy(alpha = 0.5f) else colors.otherMessageContent.copy(alpha = 0.5f)
                                    },
                                    fontSize = typography.timestamp,
                                )
                            }
                        }
                    }
                } else {
                    // Normal display mode
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isOwnMessage) colors.ownMessageBackground else colors.otherMessageBackground,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                                    bottomEnd = if (isOwnMessage) 4.dp else 16.dp,
                                ),
                            )
                            .combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    if (!isDeleted) {
                                        onLongPress?.invoke(message)
                                        showMenu = true
                                    }
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = if (isDeleted) "[Message deleted]" else message.text,
                                color = if (isOwnMessage) colors.ownMessageContent else colors.otherMessageContent,
                                fontSize = typography.messageText,
                                fontStyle = if (isDeleted) FontStyle.Italic else FontStyle.Normal,
                            )

                            // Show "(edited)" indicator
                            if (isEdited && !isDeleted) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(edited)",
                                    color = if (isOwnMessage) {
                                        colors.ownMessageContent.copy(alpha = 0.7f)
                                    } else {
                                        colors.otherMessageContent.copy(alpha = 0.7f)
                                    },
                                    fontSize = typography.timestamp,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    }

                    MessageActionsMenu(
                        expanded = showMenu,
                        message = message,
                        actions = menuActions,
                        onDismiss = { showMenu = false },
                    )
                }
            }

            // Reactions display
            if (showReactions && !isDeleted && onReactionClick != null) {
                MessageReactions(
                    message = message,
                    currentClientId = currentClientId,
                    onReactionClick = { emoji -> onReactionClick(message, emoji) },
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }

            if (showTimestamp) {
                Text(
                    text = formatTimestamp(message.timestamp, timestampFormat),
                    color = colors.timestamp,
                    fontSize = typography.timestamp,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long, format: String): String {
    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
