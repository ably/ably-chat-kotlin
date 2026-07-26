package com.ably.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ably.chat.Message
import com.ably.chat.ui.theme.AblyChatTheme

/**
 * Represents an action that can be performed on a message in the context menu.
 *
 * @property label The display text for the action.
 * @property onClick Callback invoked when the action is selected. Receives the message.
 * @property textColor Optional custom text color for the action. Defaults to theme's menuContent.
 */
public data class ChatMessageAction(
    val label: String,
    val onClick: (Message) -> Unit,
    val textColor: Color? = null,
)

/**
 * A dropdown menu that displays available actions for a message.
 *
 * This component is shown when a user long-presses on a message bubble.
 *
 * @param expanded Whether the menu is currently visible.
 * @param message The message that the actions will be performed on.
 * @param actions The list of actions to display.
 * @param onDismiss Callback invoked when the menu should be dismissed.
 * @param modifier Modifier to be applied to the menu.
 */
@Composable
internal fun MessageActionsMenu(
    expanded: Boolean,
    message: Message,
    actions: List<ChatMessageAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AblyChatTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.background(colors.menuBackground),
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = action.label,
                        color = action.textColor ?: colors.menuContent,
                    )
                },
                onClick = {
                    action.onClick(message)
                    onDismiss()
                },
                colors = MenuDefaults.itemColors(
                    textColor = action.textColor ?: colors.menuContent,
                ),
            )
        }
    }
}

/**
 * Creates the default "Copy" action for message context menus.
 *
 * @param onCopy Callback that receives the message text to copy to clipboard.
 * @return A [ChatMessageAction] for copying message text.
 */
public fun defaultCopyAction(onCopy: (String) -> Unit): ChatMessageAction = ChatMessageAction(
    label = "Copy",
    onClick = { message -> onCopy(message.text) },
)

/**
 * Creates an "Edit" action for message context menus.
 *
 * This action should only be shown for the user's own messages.
 *
 * @param onEdit Callback invoked when the edit action is selected. Receives the message to edit.
 * @return A [ChatMessageAction] for editing a message.
 */
public fun editAction(onEdit: (Message) -> Unit): ChatMessageAction = ChatMessageAction(
    label = "Edit",
    onClick = onEdit,
)

/**
 * Creates a "Delete" action for message context menus.
 *
 * This action should only be shown for the user's own messages.
 * The action is styled with a destructive (red) color.
 *
 * @param onDelete Callback invoked when the delete action is selected. Receives the message to delete.
 * @param destructiveColor The color for the destructive action text.
 * @return A [ChatMessageAction] for deleting a message.
 */
public fun deleteAction(onDelete: (Message) -> Unit, destructiveColor: Color): ChatMessageAction = ChatMessageAction(
    label = "Delete",
    onClick = onDelete,
    textColor = destructiveColor,
)

/**
 * Creates a "React" action for message context menus.
 *
 * @param onReact Callback invoked when the react action is selected. Receives the message to react to.
 * @return A [ChatMessageAction] for adding a reaction to a message.
 */
public fun reactAction(onReact: (Message) -> Unit): ChatMessageAction = ChatMessageAction(
    label = "React",
    onClick = onReact,
)

/**
 * Creates a list of default message actions.
 *
 * @param isOwnMessage Whether the message belongs to the current user.
 * @param onCopy Callback for copying message text.
 * @param onEdit Callback for editing the message (only for own messages).
 * @param onDelete Callback for deleting the message (only for own messages).
 * @param onReact Optional callback for reacting to the message.
 * @param destructiveColor The color for destructive action text.
 * @return List of [ChatMessageAction] appropriate for the message.
 */
public fun defaultMessageActions(
    isOwnMessage: Boolean,
    onCopy: (String) -> Unit,
    onEdit: ((Message) -> Unit)? = null,
    onDelete: ((Message) -> Unit)? = null,
    onReact: ((Message) -> Unit)? = null,
    destructiveColor: Color,
): List<ChatMessageAction> = buildList {
    if (onReact != null) {
        add(reactAction(onReact))
    }
    add(defaultCopyAction(onCopy))
    if (isOwnMessage) {
        if (onEdit != null) {
            add(editAction(onEdit))
        }
        if (onDelete != null) {
            add(deleteAction(onDelete, destructiveColor))
        }
    }
}
